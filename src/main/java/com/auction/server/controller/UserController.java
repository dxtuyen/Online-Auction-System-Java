package com.auction.server.controller;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.service.AuctionService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Controller xử lý action liên quan đến User: LOGIN, REGISTER, LOGOUT. */
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
        double extra = req.getDataDouble("extra");

        Role role = Role.valueOf(roleStr);
        User user = service.register(username, password, role, extra);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());

        return Response.success("REGISTER", "Đăng ký thành công", data);
    }

    public Response logout(Request req) {
        handler.setCurrentUserId(null);
        return Response.success("LOGOUT", "Đã đăng xuất", null);
    }
}
