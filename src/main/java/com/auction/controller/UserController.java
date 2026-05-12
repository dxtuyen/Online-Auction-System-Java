package com.auction.controller;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.service.UserManager;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý action liên quan đến User: LOGIN, REGISTER, LOGOUT, GET_PROFILE.
 *
 * <p>Ngoài xác thực, controller này còn chịu trách nhiệm trả về thông tin tài khoản
 * của session hiện tại để client có thể hiển thị username, role, số dư khả dụng
 * hoặc doanh thu mà không phải tự truy cập thẳng vào domain model.</p>
 */
public class UserController {

    private final UserManager userManager = UserManager.getInstance();
    private final ClientHandler handler;

    public UserController(ClientHandler handler) { this.handler = handler; }

    public Response login(Request req) {
        String username = req.requireString("username");
        String password = req.requireString("password");

        User user = userManager.login(username, password);
        // Lưu userId vào handler để các request sau biết mình là ai
        handler.setCurrentUserId(user.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId().toString());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("fullName", user.getFullName());
        data.put("status", user.getUserStatus().name());
        data.put("displayStatus", user.getUserStatus().getDisplayName());
        data.put("role", user.getRole().name());
        data.put("displayRole", user.getRole().getDisplayRole());

        return Response.success("LOGIN", "Đăng nhập thành công", data);
    }

    public Response register(Request req) {
        String username = req.requireString("username");
        String password = req.requireString("password");
        String email = req.requireString("email");
        String fullName = req.requireString("fullName");

        User user = userManager.register(username, password, email, fullName, Role.NORMAL);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId().toString());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("fullName", user.getFullName());
        data.put("role", user.getRole().name());
        data.put("displayRole", user.getRole().getDisplayRole());

        return Response.success("REGISTER", "Đăng ký thành công", data);
    }

    public Response logout(Request req) {
        handler.setCurrentUserId(null);
        return Response.success("LOGOUT", "Đã đăng xuất", null);
    }

    /**
     * Trả thông tin tài khoản của user đang đăng nhập.
     *
     * <p>Mỗi user có balance (số dư khả dụng để đặt cọc/thanh toán) và revenue
     * (tổng doanh thu nhận được từ bán đấu giá) đính kèm trong response.</p>
     */
    public Response getProfile(Request req) {
        UUID userId = handler.getCurrentUserId();
        if (userId == null) {
            return Response.error("GET_PROFILE", "Chưa đăng nhập");
        }

        User user = userManager.findById(userId).orElse(null);
        if (user == null) {
            return Response.error("GET_PROFILE", "Không tìm thấy người dùng hiện tại");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId().toString());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("fullName", user.getFullName());
        data.put("role", user.getRole().name());
        data.put("displayRole", user.getRole().getDisplayRole());
        data.put("status", user.getUserStatus().name());
        data.put("displayStatus", user.getUserStatus().getDisplayName());
        data.put("balance", user.getBalance());
        data.put("revenue", user.getRevenue());

        return Response.success("GET_PROFILE", null, data);
    }
}
