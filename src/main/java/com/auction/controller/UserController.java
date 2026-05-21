package com.auction.controller;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.service.ImageStorageService;
import com.auction.service.UserManager;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.server.Session;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý các action liên quan đến User: LOGIN, REGISTER, LOGOUT, GET_PROFILE.
 *
 * <p>Singleton stateless: 1 instance dùng chung cho mọi connection. State per-connection
 * (userId đang đăng nhập) nằm ở {@link Session} truyền qua {@link ClientHandler}.</p>
 */
public final class UserController {

    // ============== SINGLETON (Bill Pugh) ==============
    private UserController() {}
    private static final class Holder { static final UserController I = new UserController(); }
    public static UserController getInstance() { return Holder.I; }

    private final UserManager userManager = UserManager.getInstance();
    private final ImageStorageService imageStorage = ImageStorageService.getInstance();

    /**
     * Đăng nhập. Lưu userId vào Session để các request sau biết "tôi là ai".
     */
    public Response login(Request req, ClientHandler ctx) {
        String username = req.getDataString("username");
        String password = req.getDataString("password");

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
        data.put("avatarUrl", user.getAvatarUrl());

        return Response.success("LOGIN", "Đăng nhập thành công", data);
    }

    public Response register(Request req, ClientHandler ctx) {
        String username = req.getDataString("username");
        String password = req.getDataString("password");
        String email = req.getDataString("email");
        String fullName = req.getDataString("fullName");
        // Optional — client có thể bỏ trống, mặc định 0.
        BigDecimal initialBalance = req.getDataDecimal("initialBalance");

        User user = userManager.register(username, password, email, fullName,
                Role.NORMAL, initialBalance);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId().toString());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("fullName", user.getFullName());
        data.put("role", user.getRole().name());
        data.put("displayRole", user.getRole().getDisplayRole());
        data.put("balance", user.getBalance());

        return Response.success("REGISTER", "Đăng ký thành công", data);
    }

    public Response logout(Request req, ClientHandler ctx) {
        ctx.getSession().clear();
        return Response.success("LOGOUT", "Đã đăng xuất", null);
    }

    /**
     * Trả thông tin tài khoản của user đang đăng nhập.
     *
     * <p>Auth đã được middleware ở router check trước → ở đây userId chắc chắn != null.</p>
     */
    public Response getProfile(Request req, ClientHandler ctx) {
        UUID userId = ctx.getSession().getCurrentUserId();

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
        data.put("avatarUrl", user.getAvatarUrl());

        return Response.success("GET_PROFILE", null, data);
    }

    /**
     * Cập nhật fullName và/hoặc email cho user đang đăng nhập.
     *
     * <p>Trường nào client không gửi (null/blank) thì giữ nguyên — cho phép sửa
     * từng field riêng. Email đi qua {@link UserManager#changeEmail} để index
     * không lệch state.</p>
     */
    public Response updateProfile(Request req, ClientHandler ctx) {
        UUID userId = ctx.getSession().getCurrentUserId();
        User user = userManager.findById(userId).orElse(null);
        if (user == null) {
            return Response.error("UPDATE_PROFILE", "Không tìm thấy người dùng hiện tại");
        }

        String fullName = req.getDataString("fullName");
        String email = req.getDataString("email");

        boolean changed = false;
        if (fullName != null && !fullName.isBlank()
                && !fullName.trim().equals(user.getFullName())) {
            user.setFullName(fullName);
            userManager.save(user);
            changed = true;
        }
        if (email != null && !email.isBlank()
                && !email.trim().equalsIgnoreCase(user.getEmail())) {
            userManager.changeEmail(user, email);
            changed = true;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullName", user.getFullName());
        data.put("email", user.getEmail());
        String msg = changed ? "Cập nhật thông tin thành công" : "Không có thay đổi";
        return Response.success("UPDATE_PROFILE", msg, data);
    }

    /**
     * Đổi mật khẩu — bắt buộc khớp mật khẩu cũ trước khi đặt mới.
     */
    public Response changePassword(Request req, ClientHandler ctx) {
        UUID userId = ctx.getSession().getCurrentUserId();
        User user = userManager.findById(userId).orElse(null);
        if (user == null) {
            return Response.error("CHANGE_PASSWORD", "Không tìm thấy người dùng hiện tại");
        }

        String oldPassword = req.getDataString("oldPassword");
        String newPassword = req.getDataString("newPassword");
        if (oldPassword == null || newPassword == null) {
            return Response.error("CHANGE_PASSWORD", "Vui lòng nhập đầy đủ mật khẩu cũ và mới");
        }
        if (!user.checkPassword(oldPassword)) {
            return Response.error("CHANGE_PASSWORD", "Mật khẩu cũ không đúng");
        }
        if (newPassword.length() < 6) {
            return Response.error("CHANGE_PASSWORD", "Mật khẩu mới phải >= 6 ký tự");
        }
        user.changePassword(newPassword);
        userManager.save(user);
        return Response.success("CHANGE_PASSWORD", "Đổi mật khẩu thành công", null);
    }

    /**
     * Nhận base64 bytes từ client, lưu file ở uploads/avatars/, cập nhật DB.
     * Payload: {@code fileName, dataBase64}.
     */
    public Response uploadAvatar(Request req, ClientHandler ctx) {
        UUID userId = ctx.getSession().getCurrentUserId();
        User user = userManager.findById(userId).orElse(null);
        if (user == null) {
            return Response.error("UPLOAD_AVATAR", "Không tìm thấy người dùng hiện tại");
        }

        String fileName = req.getDataString("fileName");
        String base64 = req.getDataString("dataBase64");
        if (base64 == null || base64.isBlank()) {
            return Response.error("UPLOAD_AVATAR", "Thiếu dữ liệu ảnh");
        }

        String url = imageStorage.save(ImageStorageService.AVATAR_DIR, base64, fileName);
        user.setAvatarUrl(url);
        userManager.save(user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("avatarUrl", url);
        return Response.success("UPLOAD_AVATAR", "Đã cập nhật ảnh đại diện", data);
    }

    /**
     * Trả bytes ảnh dạng base64 theo URL tương đối. PUBLIC để mọi user load
     * avatar/thumbnail mà không cần check ownership.
     */
    public Response getImage(Request req, ClientHandler ctx) {
        String url = req.getDataString("url");
        if (url == null || url.isBlank()) {
            return Response.error("GET_IMAGE", "Thiếu url");
        }
        String base64 = imageStorage.loadAsBase64(url);
        if (base64 == null) {
            return Response.error("GET_IMAGE", "Không tìm thấy ảnh");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("dataBase64", base64);
        return Response.success("GET_IMAGE", null, data);
    }
}
