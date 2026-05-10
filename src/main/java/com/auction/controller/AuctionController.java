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
 * Controller phía server cho nhóm use case Auction + Item.
 *
 * <p>Vai trò: đứng giữa socket request và service layer — đọc dữ liệu thô từ {@link Request},
 * kiểm tra session tối thiểu, gọi đúng nghiệp vụ ở các *Manager (singleton), rồi map kết quả
 * về {@link Response} để client JavaFX hoặc client console dùng được ngay.</p>
 *
 * <p>Controller không chứa business rule nặng. Quyền sở hữu, bước nhảy giá, reserve balance,
 * settlement... đã được chốt ở entity/manager.</p>
 */
public class AuctionController {

    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private final ItemManager itemManager = ItemManager.getInstance();
    private final UserManager userManager = UserManager.getInstance();
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();
    private final ClientHandler handler;

    public AuctionController(ClientHandler handler) { this.handler = handler; }

    /**
     * Trả danh sách auction tóm tắt để client render màn hình list.
     */
    public Response listAuctions(Request req) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Auction a : auctionManager.findAll()) {
            Item item = itemManager.findById(a.getItemId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("auctionId", a.getId().toString());
            row.put("itemName", item != null ? item.getName() : "N/A");
            row.put("itemCategory", item != null ? item.getCategory().getDisplayName() : "");
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

    /**
     * Lấy đầy đủ thông tin auction để client mở màn hình chi tiết/bidding.
     */
    public Response getAuction(Request req) {
        UUID auctionId = req.requireUUID("auctionId");
        Auction a = auctionManager.findById(auctionId).orElse(null);
        if (a == null) return Response.error("GET_AUCTION", "Phiên không tồn tại");

        Item item = itemManager.findById(a.getItemId()).orElse(null);
        User seller = userManager.findById(a.getSellerId()).orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", a.getId().toString());
        data.put("itemName", item != null ? item.getName() : "N/A");
        data.put("itemDescription", item != null ? item.getDescription() : "");
        data.put("itemCategory", item != null ? item.getCategory().getDisplayName() : "");
        data.put("sellerName", seller != null ? seller.getUsername() : "N/A");
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
     * Tạo item mới cho seller đang đăng nhập.
     *
     * <p>Quyền sell và validate category-specific attribute do {@link ItemManager}/factory đảm nhiệm.
     * Controller chỉ xác thực session và normalize dữ liệu raw từ JSON.</p>
     */
    public Response createItem(Request req) {
        UUID sellerId = handler.getCurrentUserId();
        if (sellerId == null) return Response.error("CREATE_ITEM", "Chưa đăng nhập");

        ItemCategory category = req.requireEnum(ItemCategory.class, "category");
        String name = req.requireString("name");
        // description có thể trống (item không bắt buộc mô tả) → giữ getDataString
        String description = req.getDataString("description");
        BigDecimal startingPrice = req.requireBigDecimal("startingPrice");
        ItemCondition condition = req.requireEnum(ItemCondition.class, "condition");

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
     * Mở phiên đấu giá mới cho item của seller hiện tại.
     *
     * <p>Client chỉ gửi {@code durationMinutes} cho gọn; controller tự derive
     * {@code startTime = now} và {@code endTime = now + duration}. Giá khởi điểm
     * lấy thẳng từ Item để client không thể override.</p>
     */
    public Response createAuction(Request req) {
        UUID sellerId = handler.getCurrentUserId();
        if (sellerId == null) return Response.error("CREATE_AUCTION", "Chưa đăng nhập");

        UUID itemId = req.requireUUID("itemId");
        // duration có default; minimumIncrement parse bằng BigDecimal để không đi qua double.
        int duration = req.getDataInt("durationMinutes");
        BigDecimal increment = req.getDataBigDecimal("minimumIncrement");

        if (duration <= 0) duration = 30;
        if (increment.signum() <= 0) increment = new BigDecimal("100000");

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

    /**
     * Đóng phiên thủ công theo yêu cầu seller (PENDING→CANCELED hoặc RUNNING→FINISHED).
     *
     * <p>Actor id luôn lấy từ session; AuctionManager kiểm tra actor có phải seller của phiên hay không.</p>
     */
    public Response closeAuction(Request req) {
        UUID actorUserId = handler.getCurrentUserId();
        if (actorUserId == null) return Response.error("CLOSE_AUCTION", "Chưa đăng nhập");

        UUID auctionId = req.requireUUID("auctionId");
        auctionManager.closeAuction(auctionId, actorUserId);
        return Response.success("CLOSE_AUCTION", "Đã đóng phiên", null);
    }

    /**
     * Trả danh sách item của user hiện tại để seller chọn item nào sẽ mở auction.
     */
    public Response listMyItems(Request req) {
        UUID sellerId = handler.getCurrentUserId();
        if (sellerId == null) return Response.error("LIST_MY_ITEMS", "Chưa đăng nhập");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Item item : itemManager.findBySellerId(sellerId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.getId().toString());
            row.put("name", item.getName());
            row.put("startingPrice", item.getStartingPrice());
            row.put("category", item.getCategory().getDisplayName());
            row.put("condition", item.getCondition().getDisplayCondition());
            result.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result);
        return Response.success("LIST_MY_ITEMS", null, data);
    }

    /**
     * Đăng ký client hiện tại vào danh sách observer của auction để nhận push realtime.
     */
    public Response watchAuction(Request req) {
        UUID auctionId = req.requireUUID("auctionId");
        eventManager.subscribe(auctionId, handler);
        return Response.success("WATCH_AUCTION", "Đang theo dõi phiên " + auctionId, null);
    }

    /**
     * Hủy theo dõi realtime cho client hiện tại.
     */
    public Response unwatchAuction(Request req) {
        UUID auctionId = req.requireUUID("auctionId");
        eventManager.unsubscribe(auctionId, handler);
        return Response.success("UNWATCH_AUCTION", "Ngừng theo dõi", null);
    }
}
