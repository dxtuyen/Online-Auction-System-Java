package com.auction.server.controller;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.server.service.AuctionService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller xử lý action liên quan đến User: LOGIN, REGISTER, LOGOUT, GET_PROFILE.
 *
 * <p>Ngoài xác thực, controller này còn chịu trách nhiệm trả về thông tin tài khoản
 * của session hiện tại để client có thể hiển thị username, role, số dư khả dụng
 * hoặc doanh thu mà không phải tự truy cập thẳng vào domain model.</p>
 */
public class UserController {

    private final AuctionService service = AuctionService.getInstance();
    private final ClientHandler handler;

    public UserController(ClientHandler handler) { this.handler = handler; }

    public Response login(Request req) {
        String username = req.getDataString("username");
        String password = req.getDataString("password");

        User user = service.login(username, password);
        // Lưu userId vào handler để các request sau biết mình là ai
        handler.setCurrentUserId(String.valueOf(user.getId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole().name());
        data.put("displayRole", user.getRole().getDisplayRole());

        return Response.success("LOGIN", "Đăng nhập thành công", data);
    }

    public Response register(Request req) {
        String username = req.getDataString("username");
        String password = req.getDataString("password");
        String roleStr = req.getDataString("role");
        double balance = req.getDataDouble("balance");
        double revenue = req.getDataDouble("revenue");

        Role role = Role.valueOf(roleStr);
        User user = service.register(username, password, role, balance, revenue);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());

        return Response.success("REGISTER", "Đăng ký thành công", data);
    }

    public Response logout(Request req) {
        handler.setCurrentUserId(null);
        return Response.success("LOGOUT", "Đã đăng xuất", null);
    }

    /**
     * Trả thông tin tài khoản của user đang đăng nhập.
     *
     * <p>Bidder sẽ nhận thêm 3 số quan trọng:
     * số dư ví, số tiền đang giữ chỗ ở các auction đang dẫn đầu, và số dư khả dụng.
     * Seller sẽ nhận doanh thu hiện tại. Admin hiện chỉ trả thông tin cơ bản.</p>
     */
    public Response getProfile(Request req) {
        if (handler.getCurrentUserId() == null)
            return Response.error("GET_PROFILE", "Chưa đăng nhập");

        int userId = Integer.parseInt(handler.getCurrentUserId());
        User user = service.getUser(userId);
        if (user == null) {
            return Response.error("GET_PROFILE", "Không tìm thấy người dùng hiện tại");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole().name());
        data.put("displayRole", user.getRole().getDisplayRole());
        data.put("status", user.getUserStatus().name());
        data.put("displayStatus", user.getUserStatus().getDisplayStatus());

        data.put("balance", user.getBalance());
        data.put("reservedBalance", service.getReservedBalance(userId));
        data.put("availableBalance", service.getAvailableBalance(userId));
        data.put("totalRevenue", user.getRevenue());


        return Response.success("GET_PROFILE", null, data);
    }
}
