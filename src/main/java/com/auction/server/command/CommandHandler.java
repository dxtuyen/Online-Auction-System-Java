package com.auction.server.command;

import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;

/**
 * Một command xử lý cho một action.
 *
 * <p>Đây là phần "Command pattern" của tầng network: thay vì router viết
 * 1 chuỗi switch-case dài, mỗi action trở thành 1 lambda/method reference
 * khớp với interface này và được đăng ký vào {@code RequestRouter}.</p>
 *
 * <p>Lý do dùng functional interface:
 * <ul>
 *   <li>Đăng ký bằng method reference cho gọn:
 *       {@code register(LOGIN, USER, userCtrl::login)}.</li>
 *   <li>Không cần tạo class con cho mỗi action — tránh phình code.</li>
 *   <li>Thêm action mới chỉ cần 1 dòng register, không sửa router.</li>
 * </ul>
 *
 * @see com.auction.server.RequestRouter
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * @param req request đã parse từ JSON
     * @param ctx connection-scope context — chứa {@link com.auction.server.Session}
     *            và bản thân handler (cần khi action subscribe observer realtime)
     */
    Response handle(Request req, ClientHandler ctx);
}
