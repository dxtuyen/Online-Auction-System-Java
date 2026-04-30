package com.auction.server;

import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.UserController;

/**
 * Router phân phối request tới controller tương ứng dựa trên action name.
 *
 * <p>Mỗi {@link ClientHandler} có một router riêng vì mỗi connection mang theo session
 * đăng nhập khác nhau. Controller bên dưới đều dùng chung service singleton,
 * nhưng ngữ cảnh "user hiện tại là ai" lại nằm ở handler của connection đó.</p>
 *
 * <p>Router này cố ý mỏng: nó không chứa business rule, chỉ làm 2 việc:
 * chọn đúng controller method theo {@code action},
 * và chuẩn hóa exception runtime thành {@link Response#error(String, String)}
 * để protocol trả về cho client luôn thống nhất.</p>
 */
public class RequestRouter {

    private final UserController userCtrl;
    private final AuctionController auctionCtrl;
    private final BidController bidCtrl;

    /**
     * Khởi tạo bộ controller gắn với đúng handler/session hiện tại.
     */
    public RequestRouter(ClientHandler handler) {
        this.userCtrl = new UserController(handler);
        this.auctionCtrl = new AuctionController(handler);
        this.bidCtrl = new BidController(handler);
    }

    /**
     * Điều phối request dựa trên tên action trong payload JSON.
     *
     * <p>Nếu một controller/service ném {@link RuntimeException}, router sẽ bắt lại
     * và đổi thành {@code Response.error(...)}. Nhờ vậy các tầng dưới chỉ cần ném lỗi
     * nghiệp vụ tự nhiên, còn tầng protocol vẫn trả về cùng một shape response cho client.</p>
     */
    public Response route(Request req) {
        String action = req.getAction();

        try {
            return switch (action) {
                // User
                case "LOGIN"    -> userCtrl.login(req);
                case "REGISTER" -> userCtrl.register(req);
                case "LOGOUT"   -> userCtrl.logout(req);

                // Auction
                case "LIST_AUCTIONS"  -> auctionCtrl.listAuctions(req);
                case "GET_AUCTION"    -> auctionCtrl.getAuction(req);
                case "CREATE_AUCTION" -> auctionCtrl.createAuction(req);
                case "CLOSE_AUCTION"  -> auctionCtrl.closeAuction(req);
                case "CREATE_ITEM"    -> auctionCtrl.createItem(req);
                case "LIST_MY_ITEMS"  -> auctionCtrl.listMyItems(req);
                case "WATCH_AUCTION"  -> auctionCtrl.watchAuction(req);
                case "UNWATCH_AUCTION" -> auctionCtrl.unwatchAuction(req);

                // Bid
                case "PLACE_BID"    -> bidCtrl.placeBid(req);
                case "SET_AUTO_BID" -> bidCtrl.setAutoBid(req);
                case "BID_HISTORY"  -> bidCtrl.bidHistory(req);

                // Action lạ không làm rớt connection; server chỉ trả lỗi protocol cho client.
                default -> Response.error(action, "Action không hỗ trợ: " + action);
            };
        } catch (RuntimeException e) {
            // Toàn bộ lỗi nghiệp vụ được normalize về ERROR response ở đây.
            return Response.error(action, e.getMessage());
        }
    }
}
