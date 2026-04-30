package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lớp cha cho mọi loại user (Bidder, Seller, Admin).
 *
 * Design patterns áp dụng:
 *  - Template Method: canBid(), canSell(), canManageSystem() → mỗi subclass tự quyết
 *  - Strategy (qua inheritance): mỗi role có "chiến lược quyền" riêng
 *  - Defensive programming: validate ở constructor + setter, fail-fast
 *
 * Tại sao role là final? Vì 1 user không nên đổi role giữa chừng
 * (Bidder không tự nhiên thành Admin được - phải tạo entity mới).
 */
public abstract class User extends Entity {

    private static final long serialVersionUID = 1L;

    // Pattern email cơ bản, đủ dùng cho project. Production có thể dùng Apache Commons Validator
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");

    // Tất cả field private để encapsulation chuẩn - subclass truy cập qua getter
    private String username;
    private String hashedPassword;   // LUÔN lưu hash, không bao giờ plain text
    private String email;
    private String fullName;
    private UserStatus userStatus;
    private final Role role;          // final: role không đổi sau khi tạo

    /** Constructor tạo USER MỚI (đăng ký) - mặc định ACTIVE */
    protected User(String username, String hashedPassword, String email,
                   String fullName, Role role) {
        super();
        this.username       = validateUsername(username);
        this.hashedPassword = validatePassword(hashedPassword);
        this.email          = validateEmail(email);
        this.fullName       = validateFullName(fullName);
        this.role           = Objects.requireNonNull(role, "role must not be null");
        this.userStatus     = UserStatus.ACTIVE;
    }

    /** Constructor RESTORE từ DB - cần đầy đủ id, timestamps, status */
    protected User(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                   String username, String hashedPassword, String email,
                   String fullName, Role role, UserStatus status) {
        super(id, createdAt, updatedAt);
        this.username       = validateUsername(username);
        this.hashedPassword = validatePassword(hashedPassword);
        this.email          = validateEmail(email);
        this.fullName       = validateFullName(fullName);
        this.role           = Objects.requireNonNull(role, "role must not be null");
        this.userStatus     = Objects.requireNonNull(status, "status must not be null");
    }

    // ============== VALIDATION (private static, dễ unit test) ==============

    private static String validateUsername(String username) {
        Objects.requireNonNull(username, "username must not be null");
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 50) {
            throw new IllegalArgumentException("Username phải từ 3-50 ký tự");
        }
        return trimmed;
    }

    private static String validatePassword(String password) {
        Objects.requireNonNull(password, "password must not be null");
        if (password.isEmpty()) {
            throw new IllegalArgumentException("Password (hashed) không được rỗng");
        }
        return password;
    }

    private static String validateEmail(String email) {
        Objects.requireNonNull(email, "email must not be null");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Email không hợp lệ: " + email);
        }
        return email;
    }

    private static String validateFullName(String fullName) {
        Objects.requireNonNull(fullName, "fullName must not be null");
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Full name không được rỗng");
        }
        return trimmed;
    }

    // ============== GETTERS / SETTERS ==============

    public String getUsername() { return username; }
    public void setUsername(String username) {
        this.username = validateUsername(username);
        markUpdated();
    }

    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = validatePassword(hashedPassword);
        markUpdated();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = validateEmail(email);
        markUpdated();
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {
        this.fullName = validateFullName(fullName);
        markUpdated();
    }

    public UserStatus getUserStatus() { return userStatus; }
    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = Objects.requireNonNull(userStatus, "status must not be null");
        markUpdated();
    }

    public Role getRole() { return role; }

    // ============== DOMAIN OPERATIONS ==============

    /** Helper - dùng nội bộ + cho subclass kiểm tra */
    public boolean isActive() {
        return userStatus == UserStatus.ACTIVE;
    }

    /** Kích hoạt user */
    public void activate() {
        setUserStatus(UserStatus.ACTIVE);
    }

    /** Khóa user (vd: vi phạm). Cần thêm UserStatus.BANNED vào enum nếu chưa có */
    public void ban() {
        setUserStatus(UserStatus.BANNED);
    }

    // ============== TEMPLATE METHODS ==============
    // Mỗi subclass BẮT BUỘC trả lời 3 câu hỏi này:

    public abstract boolean canBid();
    public abstract boolean canSell();
    public abstract boolean canManageSystem();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + getId() +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", status=" + userStatus +
                '}';
    }
}