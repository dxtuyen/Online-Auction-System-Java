package com.auction.controller;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.service.UserManager;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;
import com.auction.server.Session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý các action liên quan đến User: LOGIN, REGISTER, LOGOUT, GET_PROFILE.
 *
 * <p>SAU REFACTOR: Controller giờ là <b>singleton stateless</b>. Nó không còn giữ
 * tham chiếu tới {@code ClientHandler} (một state per-connection). Thay vào đó,
 * mọi method nhận thêm {@link ClientHandler} làm tham số để đọc/ghi {@link Session}
 * của connection gọi nó.</p>
 *
 * <p>Lợi ích:
 * <ul>
 *   <li>Đúng nguyên tắc MVC: controller là logic dùng chung, session là state per-request.</li>
 *   <li>Tiết kiệm: 1000 connection chỉ có 1 controller, không phải 1000.</li>
 *   <li>Unit test dễ: chỉ cần new Session() giả thay vì mock cả socket.</li>
 * </ul>
 */
public final class UserController {

    // ============== SINGLETON (Bill Pugh) ==============
    private UserController() {}
    private static final class Holder { static final UserController I = new UserController(); }
    public static UserController getInstance() { return Holder.I; }

    private final UserManager userManager = UserManager.getInstance();

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

        return Response.success("LOGIN", "Đăng nhập thành công", data);
    }

    public Response register(Request req, ClientHandler ctx) {
        String username = req.getDataString("username");
        String password = req.getDataString("password");
        String email = req.getDataString("email");
        String fullName = req.getDataString("fullName");

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

        return Response.success("GET_PROFILE", null, data);
    }
}
