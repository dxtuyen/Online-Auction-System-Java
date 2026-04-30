package com.auction.server.controller;

import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.User;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.service.AuctionService;

import java.util.*;

/**
 * Controller phía server cho các thao tác bid.
 *
 * <p>Khác với {@link com.auction.server.controller.AuctionController} là thiên về đọc/list dữ liệu,
 * controller này đi vào các action có thay đổi state: đặt giá tay, đăng ký auto-bid
 * và đọc lịch sử bid. Phần nghiệp vụ phức tạp như bước nhảy tối thiểu, reserve balance,
 * auto-bid chain hay settlement vẫn nằm ở {@link AuctionService}.</p>
 */
public class BidController {

    private final AuctionService service = AuctionService.getInstance();
    private final ClientHandler handler;

    public BidController(ClientHandler handler) { this.handler = handler; }

    /**
     * Nhận yêu cầu đặt giá từ client đã đăng nhập.
     *
     * <p>Controller chỉ rút bidderId từ session hiện tại thay vì tin bidderId do client gửi lên.
     * Cách này tránh việc client giả mạo user khác khi gọi PLACE_BID.</p>
     */
    public Response placeBid(Request req) {
        if (handler.getCurrentUserId() == null)
            return Response.error("PLACE_BID", "Chưa đăng nhập");

        int bidderId = Integer.parseInt(handler.getCurrentUserId());
        int auctionId = req.getDataInt("auctionId");
        double amount = req.getDataDouble("amount");

        BidTransaction bid = service.placeBid(auctionId, bidderId, amount);

        // Response chỉ trả các field vừa đủ để client xác nhận bid nào đã được ghi nhận.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bidId", bid.getId());
        data.put("amount", bid.getBidAmount());
        return Response.success("PLACE_BID", "Đặt giá thành công", data);
    }

    /**
     * Đăng ký auto-bid cho bidder hiện tại.
     *
     * <p>maxBid và increment được parse ở controller, nhưng toàn bộ validation nghiệp vụ
     * như số dư khả dụng hay trạng thái auction sẽ được service chặn.</p>
     */
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

    /**
     * Trả lịch sử bid của một auction theo dạng đã enrich tên bidder.
     *
     * <p>Service chỉ giữ transaction thô. Controller map thêm username để client
     * có thể render lịch sử ngay mà không phải gọi thêm request tra user.</p>
     */
    public Response bidHistory(Request req) {
        int auctionId = req.getDataInt("auctionId");
        List<Map<String, Object>> result = new ArrayList<>();
        for (BidTransaction b : service.getBidHistory(auctionId)) {
            User bidder = service.getUser(b.getBidderId());
            // Mỗi row là một snapshot đơn giản, phù hợp cho table/list và debug JSON.
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
