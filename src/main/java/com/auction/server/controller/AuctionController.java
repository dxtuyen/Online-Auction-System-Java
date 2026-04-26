package com.auction.server.controller;

import com.auction.model.entity.Auction;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.observer.AuctionEventManager;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.service.AuctionService;

import java.util.*;

/** Controller xử lý Auction + Item. */
public class AuctionController {

    private final AuctionService service = AuctionService.getInstance();
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();
    private final ClientHandler handler;

    public AuctionController(ClientHandler handler) { this.handler = handler; }

    public Response listAuctions(Request req) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Auction a : service.getAllAuctions()) {
            Item item = service.getItem(a.getItemId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("auctionId", a.getId());
            row.put("itemName", item != null ? item.getName() : "N/A");
            row.put("itemCategory", item != null ? item.getCategory().getDisplayName() : "");
            row.put("currentPrice", a.getCurrentPrice());
            row.put("startingPrice", a.getStartingPrice());
            row.put("totalBids", a.getTotalBids());
            row.put("status", a.getStatus().name());
            row.put("displayStatus", a.getStatus().getDisplayStatus());
            row.put("endTime", a.getEndTime().toString());
            result.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctions", result);
        return Response.success("LIST_AUCTIONS", null, data);
    }

    public Response getAuction(Request req) {
        int auctionId = req.getDataInt("auctionId");
        Auction a = service.getAuction(auctionId);
        if (a == null) return Response.error("GET_AUCTION", "Phiên không tồn tại");

        Item item = service.getItem(a.getItemId());
        User seller = service.getUser(a.getSellerId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", a.getId());
        data.put("itemName", item != null ? item.getName() : "N/A");
        data.put("itemDescription", item != null ? item.getDescription() : "");
        data.put("itemCategory", item != null ? item.getCategory().getDisplayName() : "");
        data.put("sellerName", seller != null ? seller.getUsername() : "N/A");
        data.put("startingPrice", a.getStartingPrice());
        data.put("currentPrice", a.getCurrentPrice());
        data.put("minimumIncrement", a.getMinimumIncrement());
        data.put("totalBids", a.getTotalBids());
        data.put("highestBidderId", a.getHighestBidderID());
        if (a.getHighestBidderID() > 0) {
            User leader = service.getUser(a.getHighestBidderID());
            data.put("leaderName", leader != null ? leader.getUsername() : "");
        }
        data.put("status", a.getStatus().name());
        data.put("displayStatus", a.getStatus().getDisplayStatus());
        data.put("startTime", a.getStartTime().toString());
        data.put("endTime", a.getEndTime().toString());
        data.put("remainedSeconds", a.getRemainedSeconds());

        return Response.success("GET_AUCTION", null, data);
    }

    @SuppressWarnings("unchecked")
    public Response createItem(Request req) {
        if (handler.getCurrentUserId() == null)
            return Response.error("CREATE_ITEM", "Chưa đăng nhập");

        int sellerId = Integer.parseInt(handler.getCurrentUserId());
        ItemCategory category = ItemCategory.valueOf(req.getDataString("category"));
        String name = req.getDataString("name");
        String description = req.getDataString("description");
        double startingPrice = req.getDataDouble("startingPrice");
        ItemCondition condition = ItemCondition.valueOf(req.getDataString("condition"));

        // specificAttributes là Map<String,String> — Gson parse thành Object
        Map<String, String> attrs = new HashMap<>();
        Object raw = req.getData().get("specificAttributes");
        if (raw instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                attrs.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }

        Item item = service.createItem(category, name, description, startingPrice,
                sellerId, condition, attrs);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", item.getId());
        data.put("itemName", item.getName());
        return Response.success("CREATE_ITEM", "Đã thêm sản phẩm", data);
    }

    public Response createAuction(Request req) {
        if (handler.getCurrentUserId() == null)
            return Response.error("CREATE_AUCTION", "Chưa đăng nhập");

        int sellerId = Integer.parseInt(handler.getCurrentUserId());
        int itemId = req.getDataInt("itemId");
        int duration = req.getDataInt("durationMinutes");
        double increment = req.getDataDouble("minimumIncrement");

        if (duration <= 0) duration = 30;
        if (increment <= 0) increment = 100_000;

        Auction a = service.createAuction(itemId, sellerId, duration, increment);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", a.getId());
        data.put("endTime", a.getEndTime().toString());
        return Response.success("CREATE_AUCTION", "Đã tạo phiên đấu giá", data);
    }

    public Response closeAuction(Request req) {
        int auctionId = req.getDataInt("auctionId");
        service.closeAuction(auctionId);
        return Response.success("CLOSE_AUCTION", "Đã đóng phiên", null);
    }

    public Response listMyItems(Request req) {
        if (handler.getCurrentUserId() == null)
            return Response.error("LIST_MY_ITEMS", "Chưa đăng nhập");

        int sellerId = Integer.parseInt(handler.getCurrentUserId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Item item : service.getItemsBySeller(sellerId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.getId());
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

    /** Client muốn nhận push realtime về phiên này. */
    public Response watchAuction(Request req) {
        int auctionId = req.getDataInt("auctionId");
        eventManager.subscribe(auctionId, handler);
        return Response.success("WATCH_AUCTION", "Đang theo dõi phiên " + auctionId, null);
    }

    public Response unwatchAuction(Request req) {
        int auctionId = req.getDataInt("auctionId");
        eventManager.unsubscribe(auctionId, handler);
        return Response.success("UNWATCH_AUCTION", "Ngừng theo dõi", null);
    }
}
