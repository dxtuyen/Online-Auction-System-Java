package com.auction.controller;

import com.auction.model.entity.Auction;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.server.observer.AuctionEventManager;
import com.auction.service.AuctionManager;
import com.auction.service.ImageStorageService;
import com.auction.service.ItemManager;
import com.auction.service.UserManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller cho nhóm action Auction + Item.
 *
 * <p>SAU REFACTOR:
 * <ul>
 *   <li>Singleton stateless — xem {@link UserController}.</li>
 *   <li>Auth check tập trung ở router middleware, không lặp ở từng method.</li>
 *   <li>Số tiền (startingPrice, minimumIncrement) đọc bằng {@code getDataDecimal()}
 *       để giữ precision.</li>
 * </ul>
 *
 * <p>Controller chỉ làm orchestration: đọc raw từ Request, gọi đúng service,
 * map kết quả thành Response. Business rule (quyền sở hữu, bước nhảy giá,
 * anti-sniping, settlement) đã nằm hết ở entity/manager.</p>
 */
public final class AuctionController {

    private AuctionController() {}
    private static final class Holder { static final AuctionController I = new AuctionController(); }
    public static AuctionController getInstance() { return Holder.I; }

    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private final ItemManager itemManager = ItemManager.getInstance();
    private final UserManager userManager = UserManager.getInstance();
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();
    private final ImageStorageService imageStorage = ImageStorageService.getInstance();

    /** PUBLIC — ai cũng xem danh sách phiên đấu giá được. */
    public Response listAuctions(Request req, ClientHandler ctx) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Auction a : auctionManager.findAll()) {
            Item item = itemManager.findById(a.getItemId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("auctionId", a.getId().toString());
            row.put("itemName", item != null ? item.getName() : "N/A");
            row.put("itemCategory", item != null ? item.getCategory().getDisplayName() : "");
            row.put("category", item != null ? item.getCategory().name() : "");
            row.put("imageUrl", primaryImage(item));
            row.put("currentPrice", a.getCurrentPrice());
            row.put("startingPrice", a.getStartingPrice());
            row.put("totalBids", a.getTotalBids());
            row.put("status", a.getStatus().name());
            row.put("displayStatus", a.getStatus().getDisplayName());
            row.put("endTime", a.getEndTime().toString());
            result.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctions", result);
        return Response.success("LIST_AUCTIONS", null, data);
    }

    /** PUBLIC — chi tiết phiên dành cho mọi visitor. */
    public Response getAuction(Request req, ClientHandler ctx) {
        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        Auction a = auctionManager.findById(auctionId).orElse(null);
        if (a == null) return Response.error("GET_AUCTION", "Phiên không tồn tại");

        Item item = itemManager.findById(a.getItemId()).orElse(null);
        User seller = userManager.findById(a.getSellerId()).orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", a.getId().toString());
        data.put("itemName", item != null ? item.getName() : "N/A");
        data.put("itemDescription", item != null ? item.getDescription() : "");
        data.put("itemCategory", item != null ? item.getCategory().getDisplayName() : "");
        data.put("imageUrl", primaryImage(item));
        data.put("sellerName", seller != null ? seller.getUsername() : "N/A");
        data.put("sellerAvatarUrl", seller != null ? seller.getAvatarUrl() : null);
        data.put("startingPrice", a.getStartingPrice());
        data.put("currentPrice", a.getCurrentPrice());
        data.put("minimumIncrement", a.getMinimumIncrement());
        data.put("totalBids", a.getTotalBids());

        UUID highestBidderId = a.getHighestBidderId();
        data.put("highestBidderId", highestBidderId != null ? highestBidderId.toString() : null);
        if (highestBidderId != null) {
            User leader = userManager.findById(highestBidderId).orElse(null);
            data.put("leaderName", leader != null ? leader.getUsername() : "");
        }
        data.put("status", a.getStatus().name());
        data.put("displayStatus", a.getStatus().getDisplayName());
        data.put("startTime", a.getStartTime().toString());
        data.put("endTime", a.getEndTime().toString());
        data.put("remainingSeconds", a.getRemainingSeconds());

        return Response.success("GET_AUCTION", null, data);
    }

    /**
     * USER — tạo item mới. sellerId lấy từ session, không tin client.
     */
    public Response createItem(Request req, ClientHandler ctx) {
        UUID sellerId = ctx.getSession().getCurrentUserId();

        ItemCategory category = ItemCategory.valueOf(req.getDataString("category"));
        String name = req.getDataString("name");
        String description = req.getDataString("description");
        BigDecimal startingPrice = req.getDataDecimal("startingPrice");
        if (startingPrice == null) {
            return Response.error("CREATE_ITEM", "Giá khởi điểm không hợp lệ");
        }
        ItemCondition condition = ItemCondition.valueOf(req.getDataString("condition"));

        // specificAttributes đi qua JSON nên khi vào đây chỉ còn raw Map; controller chỉ
        // chuẩn hóa lại key thành String, value giữ nguyên Object cho factory tự cast.
        Map<String, Object> attrs = new HashMap<>();
        Object rawAttrs = req.getData().get("specificAttributes");
        if (rawAttrs instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                attrs.put(String.valueOf(e.getKey()), e.getValue());
            }
        }

        List<String> images = new ArrayList<>();
        Object rawImages = req.getData().get("images");
        if (rawImages instanceof List<?> list) {
            for (Object url : list) images.add(String.valueOf(url));
        }

        Item item = itemManager.createItem(category, name, description, sellerId,
                startingPrice, images, condition, attrs);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", item.getId().toString());
        data.put("itemName", item.getName());
        return Response.success("CREATE_ITEM", "Đã thêm sản phẩm", data);
    }

    /**
     * USER — mở phiên đấu giá cho item của seller hiện tại.
     *
     * <p>Client chỉ gửi {@code durationMinutes}; server tự derive {@code endTime}.
     * Giá khởi điểm lấy thẳng từ Item để client không thể override.</p>
     */
    public Response createAuction(Request req, ClientHandler ctx) {
        UUID sellerId = ctx.getSession().getCurrentUserId();

        UUID itemId = UUID.fromString(req.getDataString("itemId"));
        int duration = req.getDataInt("durationMinutes");
        BigDecimal increment = req.getDataDecimal("minimumIncrement");

        if (duration <= 0) duration = 30;
        if (increment == null || increment.signum() <= 0) {
            increment = new BigDecimal("100000");
        }

        Item item = itemManager.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item không tồn tại: " + itemId));

        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusMinutes(duration);

        Auction a = auctionManager.createAuction(itemId, sellerId, start, end,
                item.getStartingPrice(), increment);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", a.getId().toString());
        data.put("endTime", a.getEndTime().toString());
        return Response.success("CREATE_AUCTION", "Đã tạo phiên đấu giá", data);
    }

    /** USER — đóng phiên thủ công. AuctionManager kiểm tra actor có phải seller. */
    public Response closeAuction(Request req, ClientHandler ctx) {
        UUID actorUserId = ctx.getSession().getCurrentUserId();
        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        auctionManager.closeAuction(auctionId, actorUserId);
        return Response.success("CLOSE_AUCTION", "Đã đóng phiên", null);
    }

    /**
     * USER — winner xác nhận thanh toán phiên đã FINISHED.
     * AuctionManager kiểm tra actor có phải winner.
     */
    public Response confirmPayment(Request req, ClientHandler ctx) {
        UUID userId = ctx.getSession().getCurrentUserId();
        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        Auction a = auctionManager.confirmPayment(auctionId, userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", a.getId().toString());
        data.put("status", a.getStatus().name());
        data.put("paidAmount", a.getCurrentPrice());
        return Response.success("CONFIRM_PAYMENT", "Thanh toán thành công", data);
    }

    /**
     * USER — winner từ chối thanh toán, chấp nhận mất phí phạt.
     * AuctionManager kiểm tra actor có phải winner.
     */
    public Response forfeitAuction(Request req, ClientHandler ctx) {
        UUID userId = ctx.getSession().getCurrentUserId();
        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        Auction a = auctionManager.forfeitAuction(auctionId, userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", a.getId().toString());
        data.put("status", a.getStatus().name());
        return Response.success("FORFEIT_AUCTION",
                "Đã hủy phiên — bạn mất khoản phí phạt cho người bán", data);
    }

    /** USER — danh sách item của user hiện tại (dashboard Seller). */
    public Response listMyItems(Request req, ClientHandler ctx) {
        UUID sellerId = ctx.getSession().getCurrentUserId();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Item item : itemManager.findBySellerId(sellerId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.getId().toString());
            row.put("name", item.getName());
            row.put("startingPrice", item.getStartingPrice());
            row.put("category", item.getCategory().getDisplayName());
            row.put("condition", item.getCondition().getDisplayCondition());
            row.put("imageUrl", primaryImage(item));
            result.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result);
        return Response.success("LIST_MY_ITEMS", null, data);
    }

    /**
     * PUBLIC — đăng ký client hiện tại vào danh sách observer để nhận push realtime.
     *
     * <p>Bản thân handler chính là observer (xem {@link ClientHandler} implement
     * {@code AuctionObserver}). Không cần đăng nhập để xem realtime — như xem livestream
     * đấu giá; chỉ khi PLACE_BID mới cần đăng nhập.</p>
     */
    public Response watchAuction(Request req, ClientHandler ctx) {
        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        eventManager.subscribe(auctionId, ctx);
        return Response.success("WATCH_AUCTION", "Đang theo dõi phiên " + auctionId, null);
    }

    public Response unwatchAuction(Request req, ClientHandler ctx) {
        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        eventManager.unsubscribe(auctionId, ctx);
        return Response.success("UNWATCH_AUCTION", "Ngừng theo dõi", null);
    }

    /**
     * USER — upload ảnh chính cho item. Seller phải sở hữu item, ảnh sẽ thay thế
     * ảnh ở position=0 (sản phẩm hiện tại chỉ dùng 1 ảnh chính).
     *
     * <p>Payload: {@code itemId, fileName, dataBase64}.</p>
     */
    public Response uploadItemImage(Request req, ClientHandler ctx) {
        UUID userId = ctx.getSession().getCurrentUserId();
        UUID itemId = req.getDataUUID("itemId");
        if (itemId == null) {
            return Response.error("UPLOAD_ITEM_IMAGE", "itemId không hợp lệ");
        }
        Item item = itemManager.findById(itemId).orElse(null);
        if (item == null) {
            return Response.error("UPLOAD_ITEM_IMAGE", "Item không tồn tại");
        }
        if (!item.getSellerId().equals(userId)) {
            return Response.error("UPLOAD_ITEM_IMAGE", "Bạn không phải chủ sản phẩm này");
        }

        String fileName = req.getDataString("fileName");
        String base64 = req.getDataString("dataBase64");
        if (base64 == null || base64.isBlank()) {
            return Response.error("UPLOAD_ITEM_IMAGE", "Thiếu dữ liệu ảnh");
        }
        String url = imageStorage.save(ImageStorageService.ITEM_DIR, base64, fileName);

        // 1 ảnh duy nhất — xóa danh sách cũ rồi add ảnh mới.
        for (String old : new ArrayList<>(item.getImages())) {
            item.removeImage(old);
        }
        item.addImage(url);
        itemManager.save(item);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", item.getId().toString());
        data.put("imageUrl", url);
        return Response.success("UPLOAD_ITEM_IMAGE", "Đã cập nhật ảnh sản phẩm", data);
    }

    /** Ảnh đại diện của item — phần tử đầu tiên trong list, hoặc null nếu chưa có. */
    private static String primaryImage(Item item) {
        if (item == null) return null;
        List<String> imgs = item.getImages();
        return imgs.isEmpty() ? null : imgs.get(0);
    }
}
