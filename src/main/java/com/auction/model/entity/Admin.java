package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin - quản trị hệ thống.
 *
 * Khác Bidder/Seller: KHÔNG có balance, KHÔNG có revenue.
 * Quyền chính: quản lý user khác (ban, activate).
 *
 * Note: bản code cũ của bạn bị broken nhiều chỗ:
 *  - super() không tồn tại trong User
 *  - dùng int id thay UUID
 *  - implement interface canManage không định nghĩa
 *  - không override các abstract method
 * Mình đã fix toàn bộ.
 */
public class Admin extends User {

    private static final long serialVersionUID = 1L;

    // ============== CONSTRUCTORS ==============

    /** Tạo admin mới */
    public Admin(String username, String hashedPassword, String email, String fullName) {
        super(username, hashedPassword, email, fullName, Role.ADMIN);
    }

    /** Restore từ DB */
    public Admin(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                 String username, String hashedPassword, String email,
                 String fullName, UserStatus status) {
        super(id, createdAt, updatedAt, username, hashedPassword, email,
                fullName, Role.ADMIN, status);
    }

    // ============== ADMIN OPERATIONS ==============

    /**
     * Khóa user khác.
     * Đưa method này vào Admin (chứ không để service gọi user.ban()) thể hiện rõ:
     * "chỉ Admin mới được làm hành động này" - đó là DDD: hành vi đi kèm role.
     */
    public void banUser(User target) {
        if (!canManageSystem()) {
            throw new IllegalStateException("Admin hiện không active, không thể thao tác");
        }
        if (target == this) {
            throw new IllegalArgumentException("Admin không thể tự khóa mình");
        }
        target.ban();
    }

    /** Kích hoạt lại user đã bị khóa */
    public void activateUser(User target) {
        if (!canManageSystem()) {
            throw new IllegalStateException("Admin hiện không active, không thể thao tác");
        }
        target.activate();
    }

    // ============== PERMISSIONS ==============

    @Override public boolean canBid()           { return false; }
    @Override public boolean canSell()          { return false; }
    @Override public boolean canManageSystem()  { return isActive(); }

    @Override
    public String toString() {
        return "Admin{" +
                "id=" + getId() +
                ", username='" + getUsername() + '\'' +
                ", status=" + getUserStatus() +
                '}';
    }
}
