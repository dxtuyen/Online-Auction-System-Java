package com.auction.server.controller;

import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.User;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.service.AuctionService;

import java.util.*;

/** Controller xử lý Bid + Auto-bid + Bid history. */
public class BidController {

    private final AuctionService service = AuctionService.getInstance();
    private final ClientHandler handler;

    public BidController(ClientHandler handler) { this.handler = handler; }

    public Response placeBid(Request req) {
        if (handler.getCurrentUserId() == null)
            return Response.error("PLACE_BID", "Chưa đăng nhập");

        int bidderId = Integer.parseInt(handler.getCurrentUserId());
        int auctionId = req.getDataInt("auctionId");
        double amount = req.getDataDouble("amount");

        BidTransaction bid = service.placeBid(auctionId, bidderId, amount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bidId", bid.getId());
        data.put("amount", bid.getBidAmount());
        return Response.success("PLACE_BID", "Đặt giá thành công", data);
    }

    public Response setAutoBid(Request req) {
        if (handler.getCurrentUserId() == null)
            return Response.error("SET_AUTO_BID", "Chưa đăng nhập");

        int bidderId = Integer.parseInt(handler.getCurrentUserId());
        int auctionId = req.getDataInt("auctionId");
        double maxBid = req.getDataDouble("maxBid");
        double increment = req.getDataDouble("increment");

        service.registerAutoBid(auctionId, bidderId, maxBid, increment);
        return Response.success("SET_AUTO_BID",
                String.format("Auto-bid đã đăng ký: max %,.0f, bước %,.0f", maxBid, increment),
                null);
    }

    public Response bidHistory(Request req) {
        int auctionId = req.getDataInt("auctionId");
        List<Map<String, Object>> result = new ArrayList<>();
        for (BidTransaction b : service.getBidHistory(auctionId)) {
            User bidder = service.getUser(b.getBidderId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bidId", b.getId());
            row.put("bidderId", b.getBidderId());
            row.put("bidderName", bidder != null ? bidder.getUsername() : "?");
            row.put("amount", b.getBidAmount());
            row.put("timestamp", b.getTimestamp().toString());
            result.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auctionId);
        data.put("bids", result);
        return Response.success("BID_HISTORY", null, data);
    }
}
