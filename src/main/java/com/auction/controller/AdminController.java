package com.auction.controller;

import com.auction.model.entity.Auction;
import com.auction.model.entity.User;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.service.AuctionManager;
import com.auction.service.UserManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller cho các action chỉ dành cho ADMIN: liệt kê user, ban/unban,
 * đóng cưỡng bức phiên đấu giá.
 *
 * <p>Router đã enforce {@code AuthLevel.ADMIN} trước khi forward request vào đây
 * nên các method bên dưới KHÔNG cần check role lại — chỉ tập trung orchestration.</p>
 */
public final class AdminController {

    private AdminController() {}
    private static final class Holder { static final AdminController I = new AdminController(); }
    public static AdminController getInstance() { return Holder.I; }

    private final UserManager userManager = UserManager.getInstance();
    private final AuctionManager auctionManager = AuctionManager.getInstance();

    /** Danh sách toàn bộ user (không trả hashed password / salt). */
    public Response listUsers(Request req, ClientHandler ctx) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : userManager.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId().toString());
            row.put("username", u.getUsername());
            row.put("email", u.getEmail());
            row.put("fullName", u.getFullName());
            row.put("role", u.getRole().name());
            row.put("displayRole", u.getRole().getDisplayRole());
            row.put("status", u.getUserStatus().name());
            row.put("displayStatus", u.getUserStatus().getDisplayName());
            row.put("balance", u.getBalance());
            row.put("revenue", u.getRevenue());
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", rows);
        return Response.success("LIST_USERS", null, data);
    }

    public Response banUser(Request req, ClientHandler ctx) {
        UUID targetId = req.getDataUUID("userId");
        if (targetId == null) {
            return Response.error("BAN_USER", "userId không hợp lệ");
        }
        if (targetId.equals(ctx.getSession().getCurrentUserId())) {
            return Response.error("BAN_USER", "Không thể khóa chính mình");
        }
        User u = userManager.banUser(targetId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", u.getId().toString());
        data.put("status", u.getUserStatus().name());
        data.put("displayStatus", u.getUserStatus().getDisplayName());
        return Response.success("BAN_USER", "Đã khóa tài khoản " + u.getUsername(), data);
    }

    public Response unbanUser(Request req, ClientHandler ctx) {
        UUID targetId = req.getDataUUID("userId");
        if (targetId == null) {
            return Response.error("UNBAN_USER", "userId không hợp lệ");
        }
        User u = userManager.unbanUser(targetId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", u.getId().toString());
        data.put("status", u.getUserStatus().name());
        data.put("displayStatus", u.getUserStatus().getDisplayName());
        return Response.success("UNBAN_USER", "Đã mở lại tài khoản " + u.getUsername(), data);
    }

    public Response closeAuction(Request req, ClientHandler ctx) {
        UUID auctionId = req.getDataUUID("auctionId");
        if (auctionId == null) {
            return Response.error("ADMIN_CLOSE_AUCTION", "auctionId không hợp lệ");
        }
        Auction a = auctionManager.adminCloseAuction(auctionId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", a.getId().toString());
        data.put("status", a.getStatus().name());
        return Response.success("ADMIN_CLOSE_AUCTION",
                "Đã đóng phiên đấu giá", data);
    }
}
