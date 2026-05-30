package com.auction.server;

import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.UserController;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.command.AuthLevel;
import com.auction.server.command.CommandHandler;

import java.util.EnumMap;
import java.util.Map;

public final class RequestRouter {

    private static final class Holder {
        private static final RequestRouter INSTANCE = new RequestRouter();
    }
    public static RequestRouter getInstance() { return Holder.INSTANCE; }

    private record Spec(AuthLevel auth, CommandHandler handler) {}

    private final Map<ActionType, Spec> table = new EnumMap<>(ActionType.class);

    private RequestRouter() {

        UserController user = UserController.getInstance();
        AuctionController auction = AuctionController.getInstance();
        BidController bid = BidController.getInstance();

        register(ActionType.LOGIN,        AuthLevel.PUBLIC, user::login);
        register(ActionType.REGISTER,     AuthLevel.PUBLIC, user::register);
        register(ActionType.LOGOUT,       AuthLevel.USER,   user::logout);
        register(ActionType.GET_PROFILE,  AuthLevel.USER,   user::getProfile);

        register(ActionType.LIST_AUCTIONS,  AuthLevel.PUBLIC, auction::listAuctions);
        register(ActionType.GET_AUCTION,    AuthLevel.PUBLIC, auction::getAuction);
        register(ActionType.CREATE_ITEM,    AuthLevel.USER,   auction::createItem);
        register(ActionType.LIST_MY_ITEMS,  AuthLevel.USER,   auction::listMyItems);
        register(ActionType.CREATE_AUCTION, AuthLevel.USER,   auction::createAuction);
        register(ActionType.CLOSE_AUCTION,  AuthLevel.USER,   auction::closeAuction);
        register(ActionType.WATCH_AUCTION,  AuthLevel.PUBLIC, auction::watchAuction);
        register(ActionType.UNWATCH_AUCTION, AuthLevel.PUBLIC, auction::unwatchAuction);
        register(ActionType.CONFIRM_PAYMENT, AuthLevel.USER,  auction::confirmPayment);
        register(ActionType.FORFEIT_AUCTION, AuthLevel.USER,  auction::forfeitAuction);

        register(ActionType.PLACE_BID,    AuthLevel.USER,   bid::placeBid);
        register(ActionType.SET_AUTO_BID, AuthLevel.USER,   bid::setAutoBid);
        register(ActionType.BID_HISTORY,  AuthLevel.PUBLIC, bid::bidHistory);
    }

    private void register(ActionType action, AuthLevel auth, CommandHandler handler) {
        table.put(action, new Spec(auth, handler));
    }

    public Response dispatch(Request req, ClientHandler ctx) {
        String rawAction = req.getAction();
        ActionType action = ActionType.from(rawAction);
        if (action == null) {
            return Response.error(rawAction, "Action không hỗ trợ: " + rawAction);
        }

        Spec spec = table.get(action);
        if (spec == null) {
            return Response.error(rawAction, "Action chưa được đăng ký: " + action);
        }

        if (spec.auth() == AuthLevel.USER && !ctx.getSession().isAuthenticated()) {
            return Response.error(rawAction, "Chưa đăng nhập");
        }

        try {
            return spec.handler().handle(req, ctx);
        } catch (RuntimeException e) {

            return Response.error(rawAction, e.getMessage());
        }
    }
}
