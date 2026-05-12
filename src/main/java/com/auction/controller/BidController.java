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
 * Controller cho các action bid: PLACE_BID, SET_AUTO_BID, BID_HISTORY.
 *
 * <p>SAU REFACTOR:
 * <ul>
 *   <li><b>Singleton stateless</b> (xem {@link UserController} để giải thích).</li>
 *   <li><b>Auth check chuyển sang router middleware</b> — không còn lặp
 *       {@code if (userId == null) return error("Chưa đăng nhập")} ở mỗi method.</li>
 *   <li><b>Đọc số tiền bằng {@code getDataDecimal()}</b> thay vì
 *       {@code BigDecimal.valueOf(getDataDouble(...))}. Cách cũ ép qua {@code double}
 *       → mất precision (ví dụ 0.1 + 0.2 = 0.30000000000000004). Với tiền điều này
 *       là BUG nghiêm trọng — và đề BTL chấm cả "tránh lost update / rollback".</li>
 * </ul>
 */
public final class BidController {

    private BidController() {}
    private static final class Holder { static final BidController I = new BidController(); }
    public static BidController getInstance() { return Holder.I; }

    private final BidManager bidManager = BidManager.getInstance();
    private final UserManager userManager = UserManager.getInstance();

    /**
     * Nhận yêu cầu đặt giá từ client đã đăng nhập.
     *
     * <p>bidderId LẤY TỪ SESSION, không tin trường userId do client tự gửi —
     * chống giả mạo người khác. Số tiền đọc thẳng dưới dạng BigDecimal để giữ precision.</p>
     */
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

    /**
     * Đăng ký auto-bid. Validation chi tiết (auction còn mở, bidder ≠ seller,
     * maxBid > 0) đã có ở {@link BidManager}; controller chỉ parse + forward.
     */
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

    /**
     * Lịch sử bid của 1 auction, đã enrich tên bidder.
     *
     * <p>PUBLIC: không yêu cầu đăng nhập — ai cũng xem được lịch sử như sàn đấu giá thật.</p>
     */
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
