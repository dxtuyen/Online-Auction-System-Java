package com.auction.server;

import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.controller.AuctionController;
import com.auction.controller.BidController;
import com.auction.controller.UserController;

public final class RequestRouter {

    private RequestRouter() {}
    private static final class Holder { static final RequestRouter I = new RequestRouter(); }
    public static RequestRouter getInstance() { return Holder.I; }

    private final UserController userCtrl = UserController.getInstance();
    private final AuctionController auctionCtrl = AuctionController.getInstance();
    private final BidController bidCtrl = BidController.getInstance();

    public Response dispatch(Request req, ClientHandler ctx) {
        String action = req.getAction();
        String requestId = req.getRequestId();

        if (action == null) {
            return Response.error("UNKNOWN", "Thiếu action").withRequestId(requestId);
        }

        try {
            Response response = switch (action) {
                case "LOGIN"       -> userCtrl.login(req, ctx);
                case "REGISTER"    -> userCtrl.register(req, ctx);
                case "LOGOUT"      -> userCtrl.logout(req, ctx);
                case "GET_PROFILE" -> userCtrl.getProfile(req, ctx);

                case "LIST_AUCTIONS"   -> auctionCtrl.listAuctions(req, ctx);
                case "GET_AUCTION"     -> auctionCtrl.getAuction(req, ctx);
                case "CREATE_AUCTION"  -> auctionCtrl.createAuction(req, ctx);
                case "CLOSE_AUCTION"   -> auctionCtrl.closeAuction(req, ctx);
                case "CREATE_ITEM"     -> auctionCtrl.createItem(req, ctx);
                case "LIST_MY_ITEMS"   -> auctionCtrl.listMyItems(req, ctx);
                case "WATCH_AUCTION"   -> auctionCtrl.watchAuction(req, ctx);
                case "UNWATCH_AUCTION" -> auctionCtrl.unwatchAuction(req, ctx);

                case "PLACE_BID"    -> bidCtrl.placeBid(req, ctx);
                case "SET_AUTO_BID" -> bidCtrl.setAutoBid(req, ctx);
                case "BID_HISTORY"  -> bidCtrl.bidHistory(req, ctx);

                default -> Response.error(action, "Action không hỗ trợ: " + action);
            };
            return response.withRequestId(requestId);
        } catch (RuntimeException e) {
            return Response.error(action, e.getMessage()).withRequestId(requestId);
        }
    }
}
