package com.auction.server;

import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.UserController;

/**
 * Router phân phối request tới controller tương ứng dựa trên action name.
 *
 * <p>Mỗi ClientHandler có 1 RequestRouter riêng (vì ngữ cảnh đăng nhập khác nhau).</p>
 */
public class RequestRouter {

    private final UserController userCtrl;
    private final AuctionController auctionCtrl;
    private final BidController bidCtrl;

    public RequestRouter(ClientHandler handler) {
        this.userCtrl = new UserController(handler);
        this.auctionCtrl = new AuctionController(handler);
        this.bidCtrl = new BidController(handler);
    }

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

                default -> Response.error(action, "Action không hỗ trợ: " + action);
            };
        } catch (RuntimeException e) {
            return Response.error(action, e.getMessage());
        }
    }
}
