package com.auction.server;

import com.auction.model.entity.Auction;
import com.auction.model.enums.AuctionStatus;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.service.AuctionManager;
import com.auction.testsupport.Reset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private Request req(String action, Map<String, Object> data) {
        Request r = new Request(action, data, null);
        r.setRequestId(UUID.randomUUID().toString());
        return r;
    }

    private ClientHandler registerAndLogin(String username, String balance) {
        ClientHandler ctx = ctx();
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
        ClientHandler seller = registerAndLogin("seller", "1000000");
        String itemId = createItem(seller, "iPhone");
        String auctionId = createAuction(seller, itemId);

        // Một client watcher (seller) subscribe để nhận push
        assertTrue(router.dispatch(
                req("WATCH_AUCTION", Map.of("auctionId", auctionId)), seller).isSuccess());

        ClientHandler bidder = registerAndLogin("bidder", "1000000");
        Response bid = router.dispatch(
                req("PLACE_BID", Map.of("auctionId", auctionId, "amount", "1000")), bidder);
        assertTrue(bid.isSuccess());

        // BID_HISTORY public
        Response history = router.dispatch(
                req("BID_HISTORY", Map.of("auctionId", auctionId)), ctx());
        assertTrue(history.isSuccess());

        assertTrue(router.dispatch(
                req("UNWATCH_AUCTION", Map.of("auctionId", auctionId)), seller).isSuccess());
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
}
