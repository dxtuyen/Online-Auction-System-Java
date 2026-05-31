package com.auction.server;

import com.auction.config.AppConfig;
import com.auction.model.entity.Auction;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.enums.Role;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.service.AuctionManager;
import com.auction.service.ImageStorageService;
import com.auction.service.UserManager;
import com.auction.testsupport.Reset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test xuyên suốt: RequestRouter → Controller → Manager → DAO (H2).
 * Bao phủ UserController, AuctionController, BidController, RequestRouter,
 * ClientHandler (session + observer callbacks), AuctionEventManager.
 */
@DisplayName("RequestRouter end-to-end (H2)")
class RouterIntegrationTest {

    private final RequestRouter router = RequestRouter.getInstance();

    @BeforeEach
    void setUp() {
        Reset.all();
    }

    private ClientHandler ctx() {
        try {
            return new ClientHandler(new Socket());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CapturingClientHandler capturingCtx() {
        try {
            return new CapturingClientHandler();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class CapturingClientHandler extends ClientHandler {
        private final List<Response> sent = new CopyOnWriteArrayList<>();

        private CapturingClientHandler() throws Exception {
            super(new Socket());
        }

        @Override
        public synchronized void send(Response response) {
            sent.add(response);
        }

        private List<Response> sent() {
            return sent;
        }
    }

    private Request req(String action, Map<String, Object> data) {
        Request r = new Request(action, data, null);
        r.setRequestId(UUID.randomUUID().toString());
        return r;
    }

    private ClientHandler registerAndLogin(String username, String balance) {
        return registerAndLogin(ctx(), username, balance);
    }

    private <T extends ClientHandler> T registerAndLogin(T ctx, String username, String balance) {
        Map<String, Object> reg = new HashMap<>();
        reg.put("username", username);
        reg.put("password", "password123");
        reg.put("email", username + "@example.com");
        reg.put("fullName", username);
        if (balance != null) reg.put("initialBalance", balance);
        assertTrue(router.dispatch(req("REGISTER", reg), ctx).isSuccess());

        Map<String, Object> login = new HashMap<>();
        login.put("username", username);
        login.put("password", "password123");
        assertTrue(router.dispatch(req("LOGIN", login), ctx).isSuccess());
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private String createItem(ClientHandler seller, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("category", "ELECTRONICS");
        data.put("name", name);
        data.put("description", "desc");
        data.put("startingPrice", "1000");
        data.put("condition", "NEW");
        data.put("specificAttributes", Map.of("brand", "Apple", "model", "15"));
        data.put("images", List.of("a.jpg"));
        Response r = router.dispatch(req("CREATE_ITEM", data), seller);
        assertTrue(r.isSuccess());
        return (String) ((Map<String, Object>) r.getData()).get("itemId");
    }

    @SuppressWarnings("unchecked")
    private String createAuction(ClientHandler seller, String itemId) {
        Map<String, Object> data = new HashMap<>();
        data.put("itemId", itemId);
        data.put("durationMinutes", 120);
        data.put("minimumIncrement", "100");
        Response r = router.dispatch(req("CREATE_AUCTION", data), seller);
        assertTrue(r.isSuccess());
        String auctionId = (String) ((Map<String, Object>) r.getData()).get("auctionId");
        forceRunning(auctionId);
        return auctionId;
    }

    private void forceRunning(String auctionId) {
        Auction a = AuctionManager.getInstance().findById(UUID.fromString(auctionId)).orElseThrow();
        if (a.getStatus() == AuctionStatus.PENDING) {
            try { a.transitionTo(AuctionStatus.RUNNING); } catch (RuntimeException ignored) { }
        }
    }

    // ============== auth & routing ==============

    @Test
    @DisplayName("Action không hỗ trợ - error")
    void unknownAction() {
        Response r = router.dispatch(req("NOPE", Map.of()), ctx());
        assertTrue(r.isError());
    }

    @Test
    @DisplayName("USER action khi chưa đăng nhập - 'Chưa đăng nhập'")
    void unauthenticated() {
        Response r = router.dispatch(req("CREATE_ITEM", new HashMap<>()), ctx());
        assertTrue(r.isError());
        assertEquals("Chưa đăng nhập", r.getMessage());
    }

    @Test
    @DisplayName("login sai mật khẩu - business exception normalize thành error")
    void loginWrong() {
        registerAndLogin("alice", "1000000");
        Map<String, Object> login = new HashMap<>();
        login.put("username", "alice");
        login.put("password", "WRONG");
        Response r = router.dispatch(req("LOGIN", login), ctx());
        assertTrue(r.isError());
    }

    @Test
    @DisplayName("REGISTER + LOGIN + GET_PROFILE + LOGOUT")
    void userLifecycle() {
        ClientHandler ctx = registerAndLogin("alice", "1000000");

        Response profile = router.dispatch(req("GET_PROFILE", new HashMap<>()), ctx);
        assertTrue(profile.isSuccess());

        Response logout = router.dispatch(req("LOGOUT", new HashMap<>()), ctx);
        assertTrue(logout.isSuccess());

        // Sau logout, USER action bị từ chối
        Response after = router.dispatch(req("GET_PROFILE", new HashMap<>()), ctx);
        assertTrue(after.isError());
    }

    // ============== item & auction ==============

    @Test
    @DisplayName("CREATE_ITEM + LIST_MY_ITEMS")
    void itemFlow() {
        ClientHandler seller = registerAndLogin("seller", "1000000");
        createItem(seller, "iPhone");

        Response list = router.dispatch(req("LIST_MY_ITEMS", new HashMap<>()), seller);
        assertTrue(list.isSuccess());
    }

    @Test
    @DisplayName("CREATE_AUCTION + LIST_AUCTIONS + GET_AUCTION")
    void auctionFlow() {
        ClientHandler seller = registerAndLogin("seller", "1000000");
        String itemId = createItem(seller, "iPhone");
        String auctionId = createAuction(seller, itemId);

        assertTrue(router.dispatch(req("LIST_AUCTIONS", new HashMap<>()), ctx()).isSuccess());

        Response get = router.dispatch(
                req("GET_AUCTION", Map.of("auctionId", auctionId)), ctx());
        assertTrue(get.isSuccess());
    }

    @Test
    @DisplayName("GET_AUCTION không tồn tại - error")
    void getAuction_missing() {
        Response r = router.dispatch(
                req("GET_AUCTION", Map.of("auctionId", UUID.randomUUID().toString())), ctx());
        assertTrue(r.isError());
    }

    // ============== bidding + watch (push qua ClientHandler) ==============

    @Test
    @DisplayName("WATCH + PLACE_BID + BID_HISTORY (kích hoạt push observer)")
    void biddingFlow() {
        CapturingClientHandler seller = registerAndLogin(capturingCtx(), "seller", "1000000");
        String itemId = createItem(seller, "iPhone");
        String auctionId = createAuction(seller, itemId);

        // Một client watcher (seller) subscribe để nhận push
        assertTrue(router.dispatch(
                req("WATCH_AUCTION", Map.of("auctionId", auctionId)), seller).isSuccess());

        ClientHandler bidder = registerAndLogin("bidder", "1000000");
        Response bid = router.dispatch(
                req("PLACE_BID", Map.of("auctionId", auctionId, "amount", "1000")), bidder);
        assertTrue(bid.isSuccess());

        Response push = seller.sent().stream()
                .filter(Response::isPush)
                .filter(r -> "BID_UPDATE".equals(r.getAction()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> pushData = (Map<String, Object>) push.getData();
        assertEquals(auctionId, pushData.get("auctionId"));
        assertEquals("bidder", pushData.get("bidderName"));

        // BID_HISTORY public
        Response history = router.dispatch(
                req("BID_HISTORY", Map.of("auctionId", auctionId)), ctx());
        assertTrue(history.isSuccess());

        assertTrue(router.dispatch(
                req("UNWATCH_AUCTION", Map.of("auctionId", auctionId)), seller).isSuccess());
    }

    @Test
    @DisplayName("UNWATCH_AUCTION xong thì watcher không nhận BID_UPDATE mới")
    void unwatchStopsBidPush() {
        CapturingClientHandler watcher = registerAndLogin(capturingCtx(), "seller", "1000000");
        String itemId = createItem(watcher, "iPhone");
        String auctionId = createAuction(watcher, itemId);
        assertTrue(router.dispatch(
                req("WATCH_AUCTION", Map.of("auctionId", auctionId)), watcher).isSuccess());

        ClientHandler bidder1 = registerAndLogin("bidder1", "1000000");
        assertTrue(router.dispatch(
                req("PLACE_BID", Map.of("auctionId", auctionId, "amount", "1000")), bidder1).isSuccess());
        long bidPushesBeforeUnwatch = watcher.sent().stream()
                .filter(r -> "BID_UPDATE".equals(r.getAction()))
                .count();
        assertEquals(1, bidPushesBeforeUnwatch);

        assertTrue(router.dispatch(
                req("UNWATCH_AUCTION", Map.of("auctionId", auctionId)), watcher).isSuccess());

        ClientHandler bidder2 = registerAndLogin("bidder2", "1000000");
        assertTrue(router.dispatch(
                req("PLACE_BID", Map.of("auctionId", auctionId, "amount", "1100")), bidder2).isSuccess());
        long bidPushesAfterUnwatch = watcher.sent().stream()
                .filter(r -> "BID_UPDATE".equals(r.getAction()))
                .count();
        assertEquals(bidPushesBeforeUnwatch, bidPushesAfterUnwatch);
    }

    @Test
    @DisplayName("WATCH_AUCTION nhận AUCTION_STATUS khi seller đóng phiên")
    void watchReceivesStatusPush() {
        CapturingClientHandler seller = registerAndLogin(capturingCtx(), "seller", "1000000");
        String itemId = createItem(seller, "iPhone");
        String auctionId = createAuction(seller, itemId);
        assertTrue(router.dispatch(
                req("WATCH_AUCTION", Map.of("auctionId", auctionId)), seller).isSuccess());

        Response close = router.dispatch(
                req("CLOSE_AUCTION", Map.of("auctionId", auctionId)), seller);

        assertTrue(close.isSuccess());
        Response push = seller.sent().stream()
                .filter(r -> "AUCTION_STATUS".equals(r.getAction()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) push.getData();
        assertEquals(auctionId, data.get("auctionId"));
        assertEquals("CANCELED", data.get("status"));
        assertNull(data.get("highestBidderId"));
    }

    @Test
    @DisplayName("WATCH_AUCTION nhận AUCTION_EXTENDED khi anti-sniping extend")
    void watchReceivesExtendedPush() {
        CapturingClientHandler watcher = registerAndLogin(capturingCtx(), "seller", "1000000");
        String itemId = createItem(watcher, "iPhone");
        String auctionId = createAuction(watcher, itemId);
        assertTrue(router.dispatch(
                req("WATCH_AUCTION", Map.of("auctionId", auctionId)), watcher).isSuccess());

        Auction auction = AuctionManager.getInstance()
                .findById(UUID.fromString(auctionId))
                .orElseThrow();
        auction.extend(30);

        Response push = watcher.sent().stream()
                .filter(r -> "AUCTION_EXTENDED".equals(r.getAction()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) push.getData();
        assertEquals(auctionId, data.get("auctionId"));
        assertEquals(auction.getEndTime().toString(), data.get("newEndTime"));
        assertEquals(30, data.get("extendedSeconds"));
    }

    @Test
    @DisplayName("PLACE_BID số tiền thiếu - error")
    void placeBid_missingAmount() {
        ClientHandler seller = registerAndLogin("seller", "1000000");
        String itemId = createItem(seller, "iPhone");
        String auctionId = createAuction(seller, itemId);

        ClientHandler bidder = registerAndLogin("bidder", "1000000");
        Map<String, Object> data = new HashMap<>();
        data.put("auctionId", auctionId);
        Response r = router.dispatch(req("PLACE_BID", data), bidder);
        assertTrue(r.isError());
    }

    @Test
    @DisplayName("SET_AUTO_BID")
    void setAutoBid() {
        ClientHandler seller = registerAndLogin("seller", "1000000");
        String itemId = createItem(seller, "iPhone");
        String auctionId = createAuction(seller, itemId);

        ClientHandler bidder = registerAndLogin("bidder", "1000000");
        Response r = router.dispatch(
                req("SET_AUTO_BID", Map.of("auctionId", auctionId,
                        "maxBid", "5000", "increment", "100")), bidder);
        assertTrue(r.isSuccess());
    }

    @Test
    @DisplayName("CLOSE_AUCTION bởi seller - chuyển trạng thái")
    void closeAuction() {
        ClientHandler seller = registerAndLogin("seller", "1000000");
        String itemId = createItem(seller, "iPhone");
        String auctionId = createAuction(seller, itemId);

        Response r = router.dispatch(
                req("CLOSE_AUCTION", Map.of("auctionId", auctionId)), seller);
        assertTrue(r.isSuccess());
    }

    @Test
    @DisplayName("ADMIN actions: bảo vệ quyền, ban/unban user")
    void adminActions() {
        User admin = UserManager.getInstance().register(
                "admin", "password123", "admin@example.com", "Admin", Role.ADMIN);
        User target = UserManager.getInstance().register(
                "target", "password123", "target@example.com", "Target", Role.NORMAL);
        User normal = UserManager.getInstance().register(
                "normal", "password123", "normal@example.com", "Normal", Role.NORMAL);

        ClientHandler normalCtx = ctx();
        normalCtx.getSession().setCurrentUserId(normal.getId());
        Response denied = router.dispatch(req("LIST_USERS", Map.of()), normalCtx);
        assertTrue(denied.isError());
        assertEquals("Yêu cầu quyền quản trị viên", denied.getMessage());

        ClientHandler adminCtx = ctx();
        adminCtx.getSession().setCurrentUserId(admin.getId());
        Response list = router.dispatch(req("LIST_USERS", Map.of()), adminCtx);
        assertTrue(list.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>)
                ((Map<String, Object>) list.getData()).get("users");
        assertTrue(rows.stream().noneMatch(row -> row.containsKey("hashedPassword")));

        Response selfBan = router.dispatch(
                req("BAN_USER", Map.of("userId", admin.getId().toString())), adminCtx);
        assertTrue(selfBan.isError());

        Response ban = router.dispatch(
                req("BAN_USER", Map.of("userId", target.getId().toString())), adminCtx);
        assertTrue(ban.isSuccess());
        assertFalse(UserManager.getInstance().findById(target.getId()).orElseThrow().isActive());

        Response unban = router.dispatch(
                req("UNBAN_USER", Map.of("userId", target.getId().toString())), adminCtx);
        assertTrue(unban.isSuccess());
        assertTrue(UserManager.getInstance().findById(target.getId()).orElseThrow().isActive());
    }

    @Test
    @DisplayName("ADMIN_CLOSE_AUCTION hủy phiên đang chạy và hoàn tiền leader")
    void adminCloseAuction_refundsLeader() {
        User admin = UserManager.getInstance().register(
                "admin", "password123", "admin@example.com", "Admin", Role.ADMIN);
        ClientHandler seller = registerAndLogin("seller", "1000000");
        String itemId = createItem(seller, "iPhone");
        String auctionId = createAuction(seller, itemId);

        ClientHandler bidder = registerAndLogin("bidder", "1000000");
        assertTrue(router.dispatch(
                req("PLACE_BID", Map.of("auctionId", auctionId, "amount", "1000")), bidder).isSuccess());

        ClientHandler adminCtx = ctx();
        adminCtx.getSession().setCurrentUserId(admin.getId());
        Response closed = router.dispatch(
                req("ADMIN_CLOSE_AUCTION", Map.of("auctionId", auctionId)), adminCtx);

        assertTrue(closed.isSuccess());
        Auction auction = AuctionManager.getInstance().findById(UUID.fromString(auctionId)).orElseThrow();
        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
        User bidderUser = UserManager.getInstance().findByUsername("bidder").orElseThrow();
        assertEquals(0, bidderUser.getBalance().compareTo(new java.math.BigDecimal("1000000")));
    }

    @Test
    @DisplayName("UPDATE_PROFILE + CHANGE_PASSWORD + UPLOAD_AVATAR + GET_IMAGE")
    void profileAndImageFlow() throws Exception {
        ClientHandler user = registerAndLogin("alice", "1000000");

        Response update = router.dispatch(req("UPDATE_PROFILE",
                Map.of("fullName", "Alice Updated", "email", "alice2@example.com")), user);
        assertTrue(update.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> updateData = (Map<String, Object>) update.getData();
        assertEquals("Alice Updated", updateData.get("fullName"));
        assertEquals("alice2@example.com", updateData.get("email"));

        Response wrongPassword = router.dispatch(req("CHANGE_PASSWORD",
                Map.of("oldPassword", "bad", "newPassword", "newpass123")), user);
        assertTrue(wrongPassword.isError());

        Response shortPassword = router.dispatch(req("CHANGE_PASSWORD",
                Map.of("oldPassword", "password123", "newPassword", "123")), user);
        assertTrue(shortPassword.isError());

        Response changedPassword = router.dispatch(req("CHANGE_PASSWORD",
                Map.of("oldPassword", "password123", "newPassword", "newpass123")), user);
        assertTrue(changedPassword.isSuccess());

        ImageStorageService.getInstance().init();
        String base64 = Base64.getEncoder().encodeToString("avatar-bytes".getBytes(StandardCharsets.UTF_8));
        String uploadedUrl = null;
        try {
            Response missingAvatar = router.dispatch(req("UPLOAD_AVATAR",
                    Map.of("fileName", "avatar.png")), user);
            assertTrue(missingAvatar.isError());

            Response upload = router.dispatch(req("UPLOAD_AVATAR",
                    Map.of("fileName", "avatar.png", "dataBase64", base64)), user);
            assertTrue(upload.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadData = (Map<String, Object>) upload.getData();
            uploadedUrl = (String) uploadData.get("avatarUrl");
            assertNotNull(uploadedUrl);

            Response image = router.dispatch(req("GET_IMAGE", Map.of("url", uploadedUrl)), ctx());
            assertTrue(image.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> imageData = (Map<String, Object>) image.getData();
            assertEquals(base64, imageData.get("dataBase64"));
        } finally {
            deleteUploaded(uploadedUrl);
        }
    }

    private void deleteUploaded(String url) throws Exception {
        if (url == null || url.isBlank()) return;
        Path root = Path.of(AppConfig.get("UPLOAD_DIR", "uploads")).toAbsolutePath();
        Path target = root.resolve(url).normalize();
        if (target.startsWith(root)) {
            Files.deleteIfExists(target);
        }
    }
}
