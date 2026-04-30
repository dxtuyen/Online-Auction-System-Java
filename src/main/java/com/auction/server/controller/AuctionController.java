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

/**
 * Controller phía server cho nhóm use case Auction + Item.
 *
 * <p>Vai trò của controller này là đứng giữa socket request và service layer:
 * đọc dữ liệu thô từ {@link Request}, kiểm tra session tối thiểu,
 * gọi đúng nghiệp vụ trong {@link AuctionService}, rồi map kết quả về {@link Response}
 * để client JavaFX hoặc client console dùng được ngay.</p>
 *
 * <p>Controller không nên chứa business rule nặng. Các rule như quyền sở hữu item,
 * bước nhảy giá, reserve balance, settlement... đều phải được chốt ở service.
 * Ở đây chủ yếu là orchestration và serialization dữ liệu trả về.</p>
 */
public class AuctionController {

    private final AuctionService service = AuctionService.getInstance();
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();
    private final ClientHandler handler;

    public AuctionController(ClientHandler handler) { this.handler = handler; }

    /**
     * Trả về danh sách auction để client render màn hình list.
     *
     * <p>Response ở đây là bản tóm tắt: tên item, giá hiện tại, trạng thái, thời điểm kết thúc...
     * Các field này đủ để hiển thị lưới danh sách mà chưa cần tải toàn bộ chi tiết item.</p>
     */
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

    /**
     * Lấy đầy đủ thông tin của một auction để client mở màn hình chi tiết/bidding.
     *
     * <p>Ngoài dữ liệu auction cơ bản, method này còn map thêm sellerName, leaderName,
     * remainedSeconds và phần mô tả item để client không phải gọi nhiều request nhỏ.</p>
     */
    public Response getAuction(Request req) {
        int auctionId = req.getDataInt("auctionId");
        Auction a = service.getAuction(auctionId);
        if (a == null) return Response.error("GET_AUCTION", "Phiên không tồn tại");

        Item item = service.getItem(a.getItemId());
        User seller = service.getUser(a.getSellerId());

        // LinkedHashMap giữ thứ tự field ổn định, thuận tiện khi debug response JSON.
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
        Integer highestBidderId = a.getHighestBidderIdOrNull();
        data.put("highestBidderId", highestBidderId);
        // Auction mới tạo có thể chưa có leader; dùng getter nullable để tránh NPE ở giai đoạn này.
        if (highestBidderId != null) {
            User leader = service.getUser(highestBidderId);
            data.put("leaderName", leader != null ? leader.getUsername() : "");
        }
        data.put("status", a.getStatus().name());
        data.put("displayStatus", a.getStatus().getDisplayStatus());
        data.put("startTime", a.getStartTime().toString());
        data.put("endTime", a.getEndTime().toString());
        data.put("remainedSeconds", a.getRemainedSeconds());

        return Response.success("GET_AUCTION", null, data);
    }

    /**
     * Tạo item mới cho seller đang đăng nhập.
     *
     * <p>Controller chỉ xác thực việc client đã có session. Tính hợp lệ về vai trò seller,
     * category-specific attributes hay quyền tạo item vẫn do service/factory chịu trách nhiệm.</p>
     */
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

        // specificAttributes đi qua JSON nên khi vào đây chỉ còn Object/Map raw;
        // controller chuẩn hóa lại về Map<String, String> trước khi giao cho service.
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

    /**
     * Mở một auction mới cho item thuộc seller hiện tại.
     *
     * <p>Controller áp vài default nhẹ cho input thiếu hoặc không hợp lệ
     * (duration/increment <= 0) để request từ client đỡ bị fail vì giá trị trống,
     * còn validation sở hữu item vẫn nằm ở service.</p>
     */
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

    /**
     * Đóng auction theo request từ client.
     *
     * <p>Từ controller nhìn vào đây chỉ là một lệnh "close", nhưng ở service nó sẽ chạy
     * toàn bộ finalize flow: kiểm tra quyền actor hiện tại, đổi trạng thái,
     * settlement nếu có winner, và phát event realtime.</p>
     */
    public Response closeAuction(Request req) {
        if (handler.getCurrentUserId() == null)
            return Response.error("CLOSE_AUCTION", "Chưa đăng nhập");

        int actorUserId = Integer.parseInt(handler.getCurrentUserId());
        int auctionId = req.getDataInt("auctionId");
        // Actor id luôn lấy từ session hiện tại, không tin dữ liệu userId do client tự gửi lên.
        service.closeAuction(auctionId, actorUserId);
        return Response.success("CLOSE_AUCTION", "Đã đóng phiên", null);
    }

    /**
     * Trả danh sách item của user hiện tại để seller chọn item nào sẽ mở auction.
     */
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

    /**
     * Đăng ký client hiện tại vào danh sách observer của auction.
     *
     * <p>Sau call này, client sẽ nhận được các push như BID_UPDATE, AUCTION_STATUS,
     * AUCTION_EXTENDED mà không cần polling liên tục.</p>
     */
    public Response watchAuction(Request req) {
        int auctionId = req.getDataInt("auctionId");
        eventManager.subscribe(auctionId, handler);
        return Response.success("WATCH_AUCTION", "Đang theo dõi phiên " + auctionId, null);
    }

    /**
     * Hủy theo dõi realtime cho client hiện tại.
     */
    public Response unwatchAuction(Request req) {
        int auctionId = req.getDataInt("auctionId");
        eventManager.unsubscribe(auctionId, handler);
        return Response.success("UNWATCH_AUCTION", "Ngừng theo dõi", null);
    }
}
