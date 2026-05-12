package com.auction.controller;

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

/**
 * Controller phía server cho các thao tác bid.
 *
 * <p>Khác với {@link AuctionController} thiên về đọc/list dữ liệu,
 * controller này đi vào các action có thay đổi state: đặt giá tay, đăng ký auto-bid
 * và đọc lịch sử bid.</p>
 *
 * <p>Phần nghiệp vụ phức tạp như bước nhảy tối thiểu, role/balance check, anti-sniping,
 * settlement... đã nằm ở {@link BidManager}/Auction entity.
 * Controller chỉ làm orchestration: rút bidderId từ session, parse input, gọi manager,
 * và serialize kết quả.</p>
 */
public class BidController {

    private final BidManager bidManager = BidManager.getInstance();
    private final UserManager userManager = UserManager.getInstance();
    private final ClientHandler handler;

    public BidController(ClientHandler handler) { this.handler = handler; }

    /**
     * Nhận yêu cầu đặt giá từ client đã đăng nhập.
     *
     * <p>bidderId được lấy từ session, KHÔNG tin trường userId do client tự gửi —
     * tránh việc client giả mạo người khác khi gọi PLACE_BID.</p>
     */
    public Response placeBid(Request req) {
        UUID bidderId = handler.getCurrentUserId();
        if (bidderId == null) return Response.error("PLACE_BID", "Chưa đăng nhập");

        UUID auctionId = req.requireUUID("auctionId");
        BigDecimal amount = req.requireBigDecimal("amount");

        BidTransaction bid = bidManager.placeBid(auctionId, bidderId, amount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bidId", bid.getId().toString());
        data.put("amount", bid.getBidAmount());
        return Response.success("PLACE_BID", "Đặt giá thành công", data);
    }

    /**
     * Đăng ký auto-bid cho bidder hiện tại.
     *
     * <p>maxBid/increment được parse ở controller, validation nghiệp vụ
     * (auction còn mở, bidder không phải seller, maxBid > 0) đã được {@link BidManager} chặn.</p>
     */
    public Response setAutoBid(Request req) {
        UUID bidderId = handler.getCurrentUserId();
        if (bidderId == null) return Response.error("SET_AUTO_BID", "Chưa đăng nhập");

        UUID auctionId = req.requireUUID("auctionId");
        BigDecimal maxBid = req.requireBigDecimal("maxBid");
        BigDecimal increment = req.requireBigDecimal("increment");

        AutoBid ab = bidManager.registerAutoBid(auctionId, bidderId, maxBid, increment);
        return Response.success("SET_AUTO_BID",
                String.format("Auto-bid đã đăng ký: max %s, bước %s",
                        ab.getMaxBid(), ab.getIncrement()),
                null);
    }

    /**
     * Trả lịch sử bid của một auction theo dạng đã enrich tên bidder.
     *
     * <p>{@link BidManager} chỉ giữ transaction thô. Controller map thêm username để client
     * có thể render lịch sử ngay mà không phải gọi thêm request tra user.</p>
     */
    public Response bidHistory(Request req) {
        UUID auctionId = req.requireUUID("auctionId");
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
