package com.auction.server;

import java.util.UUID;

/**
 * Session - giữ trạng thái đăng nhập của MỘT connection.
 *
 * <p>Vì sao tách thành class riêng (thay vì để field {@code currentUserId} trong
 * {@link ClientHandler} như trước)?
 * <ul>
 *   <li><b>Stateless controller:</b> controller giờ là singleton dùng chung, không
 *       giữ user state. State chuyển hết vào Session, pass qua method.</li>
 *   <li><b>Dễ mở rộng:</b> sau này thêm token hết hạn, role cache, locale...
 *       chỉ thêm field ở đây, không phải đụng ClientHandler.</li>
 *   <li><b>Dễ test:</b> unit test controller chỉ cần new Session(), không phải mock
 *       cả socket + handler.</li>
 * </ul>
 *
 * <p>Mỗi {@link ClientHandler} sở hữu đúng 1 Session — life-cycle bằng nhau với connection.</p>
 */
public final class Session {

    // currentUserId = null nghĩa là chưa đăng nhập.
    // volatile vì có thể bị đọc từ thread observer push trong khi thread request đang set.
    private volatile UUID currentUserId;

    public UUID getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(UUID userId) {
        this.currentUserId = userId;
    }

    /** Tiện dụng cho middleware auth — kiểm tra đăng nhập trong 1 dòng. */
    public boolean isAuthenticated() {
        return currentUserId != null;
    }

    public void clear() {
        this.currentUserId = null;
    }
}
