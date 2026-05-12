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

public final class UserController {

    private UserController() {}
    private static final class Holder { static final UserController I = new UserController(); }
    public static UserController getInstance() { return Holder.I; }

    private final UserManager userManager = UserManager.getInstance();

    public Response login(Request req, ClientHandler ctx) {
        String username = req.requireString("username");
        String password = req.requireString("password");

        User user = userManager.login(username, password);
        ctx.getSession().setCurrentUserId(user.getId());

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

    public Response register(Request req, ClientHandler ctx) {
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

    public Response logout(Request req, ClientHandler ctx) {
        ctx.getSession().clear();
        return Response.success("LOGOUT", "Đã đăng xuất", null);
    }

    public Response getProfile(Request req, ClientHandler ctx) {
        UUID userId = ctx.getSession().getCurrentUserId();
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
