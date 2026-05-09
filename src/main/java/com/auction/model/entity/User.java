package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;
import com.auction.security.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class User extends Entity {

    private static final long serialVersionUID = 1L;

    /**
     * QUY TẮC ĐẶT EMAIL
     * Cho phép local part: chữ, số, dấu chấm, gạch dưới, gạch ngang, plus, %.
     * Domain phải có ít nhất 1 dấu chấm và TLD ≥ 2 chữ.
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");


    /**
     * THUỘC TÍNH
     */
    private String username;
    private String hashedPassword;
    private String passwordSalt;        // unique per-user, sinh ra lúc đặt password
    private String email;
    private String fullName;
    private UserStatus userStatus;
    private Role role;
    private BigDecimal balance;     // số dư khả dụng để đặt cọc/thanh toán
    private BigDecimal revenue;     // tổng doanh thu nhận được từ bán đấu giá

    /**
     * VALIDATE
     */

    private static String validateUsername(String username) {
        Objects.requireNonNull(username, "username khong the la null");
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 50) {
            throw new IllegalArgumentException("Username phai tu 3-50 ky tu");
        }
        // Chỉ cho phép chữ, số, gạch dưới, chấm - tránh ký tự lạ làm SQL/log injection
        if (!trimmed.matches("[A-Za-z0-9._]+")) {
            throw new IllegalArgumentException("Username chỉ được chứa chữ, số, dấu . và _");
        }
        return trimmed;
    }

    /**
     * validate hash đã có (load từ DB hoặc đã hash sẵn) - không phải plain password
     */
    private static String validateHashedPassword(String hashed) {
        Objects.requireNonNull(hashed, "hashedPassword must not be null");
        if (hashed.isBlank()) {
            throw new IllegalArgumentException("hashedPassword không được rỗng");
        }
        return hashed;
    }

    private static String validateSalt(String salt) {
        Objects.requireNonNull(salt, "passwordSalt must not be null");
        if (salt.isBlank()) {
            throw new IllegalArgumentException("passwordSalt không được rỗng");
        }
        return salt;
    }

    private static String validateEmail(String email) {
        Objects.requireNonNull(email, "Email khong duoc null");
        String trimmed = email.trim();
        // RFC 5321: tổng độ dài địa chỉ email tối đa 254 ký tự
        if (trimmed.length() > 254) {
            throw new IllegalArgumentException("Email vuot qua 254 ky tu");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Email khong hop le");
        }
        return trimmed;
    }

    private static String validateFullName(String fullName) {
        Objects.requireNonNull(fullName, "fullName must not be null");
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Full name không được rỗng");
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Full name khong duoc qua 100 ky tu");
        }
        return trimmed;
    }

    /**
     * Validate số tiền: không null, không âm (cho phép = 0).
     */
    private static BigDecimal validateNonNegativeAmount(BigDecimal amount, String fieldName) {
        Objects.requireNonNull(amount, fieldName + " must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " khong duoc am");
        }
        return amount;
    }

    /**
     * TẠO USER MỚI
     * (Caller phải hash password trước - thường gọi qua UserManager.register)
     */
    public User(String username, String hashedPassword, String passwordSalt,
                String email, String fullName, Role role) {
        super();
        this.username = validateUsername(username);
        this.hashedPassword = validateHashedPassword(hashedPassword);
        this.passwordSalt = validateSalt(passwordSalt);
        this.email = validateEmail(email);
        this.fullName = validateFullName(fullName);
        this.userStatus = UserStatus.ACTIVE;
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.balance = BigDecimal.ZERO;
        this.revenue = BigDecimal.ZERO;
    }

    /**
     * LOAD USER TỪ DATA BASE
     */
    public User(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                String username, String hashedPassword, String passwordSalt,
                String email, String fullName, UserStatus status, Role role,
                BigDecimal balance, BigDecimal revenue) {
        super(id, createdAt, updatedAt);
        this.username = validateUsername(username);
        this.hashedPassword = validateHashedPassword(hashedPassword);
        this.passwordSalt = validateSalt(passwordSalt);
        this.email = validateEmail(email);
        this.fullName = validateFullName(fullName);
        this.userStatus = Objects.requireNonNull(status, "status must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.balance = validateNonNegativeAmount(balance, "balance");
        this.revenue = validateNonNegativeAmount(revenue, "revenue");
    }

    /**
     * KIỂM TRA PASSWORD - dùng cho login.
     * Trả về false (không throw) nếu password null/sai - để caller quyết định xử lý.
     * synchronized để đọc salt+hash atomic với changePassword (tránh đọc nửa-cập-nhật).
     */
    public synchronized boolean checkPassword(String plainPassword) {
        if (plainPassword == null) {
            return false;
        }
        return PasswordEncoder.matches(plainPassword, passwordSalt, hashedPassword);
    }

    /**
     * ĐỔI PASSWORD - tự sinh salt mới, hash lại.
     * Dùng khi user thay password.
     */
    public synchronized void changePassword(String newPlainPassword) {
        Objects.requireNonNull(newPlainPassword, "password must not be null");
        if (newPlainPassword.length() < 6) {
            throw new IllegalArgumentException("Password phải >= 6 ký tự");
        }
        this.passwordSalt = PasswordEncoder.generateSalt();
        this.hashedPassword = PasswordEncoder.hash(newPlainPassword, passwordSalt);
        markUpdated();
    }

    /**
     * Check quyền
     */

    public boolean canSell() {
        return isActive() && role != Role.ADMIN;
    }

    public boolean canBid() {
        return isActive() && role != Role.ADMIN;
    }

    public boolean canManageSystem() {
        return isActive() && role == Role.ADMIN;
    }

    /**
     * Check hoạt động, chỉnh trạng thái User
     */
    public boolean isActive() {
        return userStatus == UserStatus.ACTIVE;
    }

    /**
     * Bật lại user bị ban
     */
    public void activate() {
        setUserStatus(UserStatus.ACTIVE);
    }

    /**
     * Khóa user
     */
    public void ban() {
        setUserStatus(UserStatus.BANNED);
    }

    /**
     * Kiểm tra xem có đủ số dư để thực hiện bid.
     *
     * <p>Đây chỉ là eligibility check không đảm bảo atomicity với mutate sau đó —
     * code đặt bid phải dùng {@link #tryReserve(BigDecimal)} để check-and-deduct atomic.</p>
     */
    public synchronized boolean hasEnoughBalance(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return balance.compareTo(amount) >= 0;
    }

    /**
     * RESERVE - Atomic check-and-deduct: nếu đủ tiền → trừ ngay khỏi {@code balance} và trả {@code true}.
     *
     * <p>Mục đích: khi user đặt bid và trở thành highest bidder, số tiền đó được "giữ"
     * khỏi balance để chống user spend cùng một số tiền cho nhiều phiên cùng lúc
     * (race khi 2 thread placeBid song song).</p>
     *
     * <p>Cặp với {@link #release(BigDecimal)} (hoàn lại khi bị outbid hoặc phiên CANCELED)
     * và {@link #addRevenue(BigDecimal)} (chuyển cho seller khi phiên FINISHED→PAID).</p>
     */
    public synchronized boolean tryReserve(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount phải > 0");
        }
        if (balance.compareTo(amount) < 0) return false;
        balance = balance.subtract(amount);
        markUpdated();
        return true;
    }

    /**
     * RELEASE - Hoàn lại số tiền đã reserve. Dùng khi:
     * <ul>
     *   <li>User bị outbid → trả lại reservation cũ.</li>
     *   <li>Phiên CANCELED khi user đang là highest bidder.</li>
     * </ul>
     */
    public synchronized void release(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount phải > 0");
        }
        balance = balance.add(amount);
        markUpdated();
    }

    /**
     * Tăng doanh thu cho seller khi phiên FINISHED→PAID. Tiền winner đã reserve trước đó
     * (đã trừ khỏi balance) được chuyển hết sang revenue của seller.
     */
    public synchronized void addRevenue(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount phải > 0");
        }
        revenue = revenue.add(amount);
        markUpdated();
    }

    /**
     * GETTER SETTER
     */
    public String getUsername() {
        return username;
    }

    /** Username là invariant - KHÔNG cho đổi sau khi tạo (login bị vỡ, audit log lệch).
     *  Nếu bắt buộc phải đổi thì gỡ comment dưới và document rõ. */
    // public void setUsername(String username) {
    //     this.username = validateUsername(username);
    //     markUpdated();
    // }

    /**
     * Trả hash đã store - KHÔNG bao giờ trả plain password vì không lưu.
     * synchronized để đồng bộ với changePassword (xem checkPassword).
     */
    public synchronized String getHashedPassword() {
        return hashedPassword;
    }

    public synchronized String getPasswordSalt() {
        return passwordSalt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = validateEmail(email);
        markUpdated();
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = validateFullName(fullName);
        markUpdated();
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    /**
     * package-private hoặc protected sẽ tốt hơn nhưng AdminProfile khác package nên đành public
     */
    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = Objects.requireNonNull(userStatus, "status must not be null");
        markUpdated();
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
        markUpdated();
    }

    public synchronized BigDecimal getBalance() {
        return balance;
    }

    /**
     * Đặt balance trực tiếp - chỉ dùng cho admin / data seeding.
     * Mutate balance thông thường (đặt bid) PHẢI đi qua {@link #tryReserve}/{@link #release}
     * để giữ atomic check-and-modify.
     */
    public synchronized void setBalance(BigDecimal balance) {
        this.balance = validateNonNegativeAmount(balance, "balance");
        markUpdated();
    }

    public synchronized BigDecimal getRevenue() {
        return revenue;
    }

    public synchronized void setRevenue(BigDecimal revenue) {
        this.revenue = validateNonNegativeAmount(revenue, "revenue");
        markUpdated();
    }

    @Override
    public String toString() {
        // KHÔNG bao giờ in hashedPassword/salt - tránh leak qua log
        return "User{" +
                "id=" + getId() +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", status=" + userStatus +
                '}';
    }

    /**
     * BUILDER PATTERN
     * <p>
     * Builder nhận PLAIN password - tự hash bằng PasswordEncoder.
     * Khác với constructor (constructor đòi hash sẵn để load từ DB).
     */
    public static final class Builder {
        private String username, plainPassword, email, fullName;
        private Role role;

        public Builder username(String v) {
            this.username = v;
            return this;
        }

        /**
         * Truyền password DẠNG PLAIN - builder sẽ hash khi build()
         */
        public Builder password(String v) {
            this.plainPassword = v;
            return this;
        }

        public Builder email(String v) {
            this.email = v;
            return this;
        }

        public Builder fullName(String v) {
            this.fullName = v;
            return this;
        }

        public Builder role(Role v) {
            this.role = v;
            return this;
        }

        public User build() {
            Objects.requireNonNull(username, "username required");
            Objects.requireNonNull(plainPassword, "password required");
            Objects.requireNonNull(email, "email required");
            Objects.requireNonNull(fullName, "fullName required");
            Objects.requireNonNull(role, "role required");
            if (plainPassword.length() < 6) {
                throw new IllegalArgumentException("Password phải >= 6 ký tự");
            }

            String salt = PasswordEncoder.generateSalt();
            String hash = PasswordEncoder.hash(plainPassword, salt);

            return new User(username, hash, salt, email, fullName, role);
        }
    }
}
