package com.auction.server.controller;

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

public final class AuctionController {

    private AuctionController() {}
    private static final class Holder { static final AuctionController I = new AuctionController(); }
    public static AuctionController getInstance() { return Holder.I; }

    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private final ItemManager itemManager = ItemManager.getInstance();
    private final UserManager userManager = UserManager.getInstance();
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();

    public Response listAuctions(Request req, ClientHandler ctx) {
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

    public Response closeAuction(Request req, ClientHandler ctx) {
        UUID actorUserId = ctx.getSession().getCurrentUserId();
        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        auctionManager.closeAuction(auctionId, actorUserId);
        return Response.success("CLOSE_AUCTION", "Đã đóng phiên", null);
    }

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
            result.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result);
        return Response.success("LIST_MY_ITEMS", null, data);
    }

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
}
