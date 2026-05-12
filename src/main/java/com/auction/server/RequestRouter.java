package com.auction.server;

import com.auction.controller.AuctionController;
import com.auction.controller.BidController;
import com.auction.controller.UserController;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.command.AuthLevel;
import com.auction.server.command.CommandHandler;

import java.util.EnumMap;
import java.util.Map;

/**
 * Router phân phối request tới command handler dựa trên {@link ActionType}.
 *
 * <p>SAU REFACTOR áp dụng <b>Command pattern + map dispatch</b>:
 * <ul>
 *   <li>Mỗi action được đăng ký kèm {@link AuthLevel} và một {@link CommandHandler}
 *       (method reference của controller).</li>
 *   <li>Thay vì switch-case String dài, router dùng {@link EnumMap} tra cứu O(1).</li>
 *   <li>Thêm action mới = thêm 1 dòng {@code register(...)} — không cần sửa logic dispatch.
 *       Đây chính là <i>Open/Closed Principle</i> trong SOLID.</li>
 * </ul>
 *
 * <p><b>Auth middleware:</b> tất cả check "Chưa đăng nhập" đều tập trung tại đây
 * (xem {@link #dispatch(Request, ClientHandler)}). Controller giờ không phải lặp lại
 * {@code if (userId == null) return error("Chưa đăng nhập")} ở mỗi method.</p>
 *
 * <p>Router là <b>singleton stateless</b>: 1 instance dùng chung cho toàn bộ
 * connection trong server (vì map handler là read-only sau khi khởi tạo).</p>
 */
public final class RequestRouter {

    // ============== SINGLETON (Bill Pugh) ==============
    private static final class Holder {
        private static final RequestRouter INSTANCE = new RequestRouter();
    }
    public static RequestRouter getInstance() { return Holder.INSTANCE; }

    /**
     * Một "command spec" — gắn action với yêu cầu xác thực và handler thực thi.
     * Bản chất là record bất biến, dùng làm value trong map dispatch.
     */
    private record Spec(AuthLevel auth, CommandHandler handler) {}

    /**
     * EnumMap nhanh hơn HashMap khi key là enum. Khởi tạo 1 lần trong constructor
     * và không bao giờ mutate sau đó → an toàn cho mọi thread đọc.
     */
    private final Map<ActionType, Spec> table = new EnumMap<>(ActionType.class);

    private RequestRouter() {
        // Đăng ký tất cả action ở đây — đây là điểm DUY NHẤT cần sửa khi thêm/bớt action.
        UserController user = UserController.getInstance();
        AuctionController auction = AuctionController.getInstance();
        BidController bid = BidController.getInstance();

        // ===== USER =====
        register(ActionType.LOGIN,        AuthLevel.PUBLIC, user::login);
        register(ActionType.REGISTER,     AuthLevel.PUBLIC, user::register);
        register(ActionType.LOGOUT,       AuthLevel.USER,   user::logout);
        register(ActionType.GET_PROFILE,  AuthLevel.USER,   user::getProfile);

        // ===== AUCTION & ITEM =====
        register(ActionType.LIST_AUCTIONS,  AuthLevel.PUBLIC, auction::listAuctions);
        register(ActionType.GET_AUCTION,    AuthLevel.PUBLIC, auction::getAuction);
        register(ActionType.CREATE_ITEM,    AuthLevel.USER,   auction::createItem);
        register(ActionType.LIST_MY_ITEMS,  AuthLevel.USER,   auction::listMyItems);
        register(ActionType.CREATE_AUCTION, AuthLevel.USER,   auction::createAuction);
        register(ActionType.CLOSE_AUCTION,  AuthLevel.USER,   auction::closeAuction);
        register(ActionType.WATCH_AUCTION,  AuthLevel.PUBLIC, auction::watchAuction);
        register(ActionType.UNWATCH_AUCTION, AuthLevel.PUBLIC, auction::unwatchAuction);

        // ===== BID =====
        register(ActionType.PLACE_BID,    AuthLevel.USER,   bid::placeBid);
        register(ActionType.SET_AUTO_BID, AuthLevel.USER,   bid::setAutoBid);
        register(ActionType.BID_HISTORY,  AuthLevel.PUBLIC, bid::bidHistory);
    }

    private void register(ActionType action, AuthLevel auth, CommandHandler handler) {
        table.put(action, new Spec(auth, handler));
    }

    /**
     * Điều phối request.
     *
     * <p>Trình tự:
     * <ol>
     *   <li>Parse action name → enum. Sai enum → trả lỗi.</li>
     *   <li>Tra map → tìm spec. Không có spec → action chưa hỗ trợ.</li>
     *   <li><b>Auth middleware:</b> spec yêu cầu USER mà session chưa login → từ chối.</li>
     *   <li>Gọi handler. RuntimeException của business layer được normalize thành
     *       {@code Response.error} để protocol đồng nhất.</li>
     * </ol>
     */
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

        // ===== Auth middleware =====
        if (spec.auth() == AuthLevel.USER && !ctx.getSession().isAuthenticated()) {
            return Response.error(rawAction, "Chưa đăng nhập");
        }

        try {
            return spec.handler().handle(req, ctx);
        } catch (RuntimeException e) {
            // Toàn bộ lỗi nghiệp vụ (InvalidBidException, AuctionClosedException,
            // SecurityException...) được gom về 1 dạng response để client xử lý đồng nhất.
            return Response.error(rawAction, e.getMessage());
        }
    }
}
