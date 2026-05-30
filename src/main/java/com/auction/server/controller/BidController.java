package com.auction.server.controller;

import com.auction.model.entity.AutoBid;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.User;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.service.BidManager;
import com.auction.service.UserManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BidController {

    private BidController() {}
    private static final class Holder { static final BidController I = new BidController(); }
    public static BidController getInstance() { return Holder.I; }

    private final BidManager bidManager = BidManager.getInstance();
    private final UserManager userManager = UserManager.getInstance();

    public Response placeBid(Request req, ClientHandler ctx) {
        UUID bidderId = ctx.getSession().getCurrentUserId();

        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        BigDecimal amount = req.getDataDecimal("amount");
        if (amount == null) {
            return Response.error("PLACE_BID", "Số tiền không hợp lệ");
        }

        BidTransaction bid = bidManager.placeBid(auctionId, bidderId, amount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bidId", bid.getId().toString());
        data.put("amount", bid.getBidAmount());
        return Response.success("PLACE_BID", "Đặt giá thành công", data);
    }

    public Response setAutoBid(Request req, ClientHandler ctx) {
        UUID bidderId = ctx.getSession().getCurrentUserId();

        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        BigDecimal maxBid = req.getDataDecimal("maxBid");
        BigDecimal increment = req.getDataDecimal("increment");
        if (maxBid == null || increment == null) {
            return Response.error("SET_AUTO_BID", "maxBid và increment phải là số hợp lệ");
        }

        AutoBid ab = bidManager.registerAutoBid(auctionId, bidderId, maxBid, increment);
        return Response.success("SET_AUTO_BID",
                String.format("Auto-bid đã đăng ký: max %s, bước %s",
                        ab.getMaxBid(), ab.getIncrement()),
                null);
    }

    public Response bidHistory(Request req, ClientHandler ctx) {
        UUID auctionId = UUID.fromString(req.getDataString("auctionId"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BidTransaction b : bidManager.getBidHistory(auctionId)) {
            User bidder = userManager.findById(b.getBidderId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bidId", b.getId().toString());
            row.put("bidderId", b.getBidderId().toString());
            row.put("bidderName", bidder != null ? bidder.getUsername() : "?");
            row.put("amount", b.getBidAmount());
            row.put("timestamp", b.getTimestamp().toString());
            result.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auctionId.toString());
        data.put("bids", result);
        return Response.success("BID_HISTORY", null, data);
    }
}
