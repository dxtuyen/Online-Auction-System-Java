package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.entity.profile.RoleProfile;
import com.auction.model.entity.profile.AdminProfile;
import com.auction.model.entity.profile.BidderProfile;
import com.auction.model.entity.profile.SellerProfile;
import com.auction.model.enums.UserStatus;
import com.auction.security.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /*
     * DANH SÁCH ROLE ĐÃ ĐƯỢC CẤP CỦA USER
     *
     * Key là Role
     */
    private final EnumMap<Role, RoleProfile> profiles = new EnumMap<>(Role.class);

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
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Email khong hop le");
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

    /**
     * TẠO USER MỚI
     * (Caller phải hash password trước - thường gọi qua UserManager.register)
     */
    public User(String username, String hashedPassword, String passwordSalt,
                String email, String fullName) {
        super();
        this.username = validateUsername(username);
        this.hashedPassword = validateHashedPassword(hashedPassword);
        this.passwordSalt = validateSalt(passwordSalt);
        this.email = validateEmail(email);
        this.fullName = validateFullName(fullName);
        this.userStatus = UserStatus.ACTIVE;
    }

    /**
     * LOAD USER TỪ DATA BASE
     */
    public User(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                String username, String hashedPassword, String passwordSalt,
                String email, String fullName, UserStatus status) {
        super(id, createdAt, updatedAt);
        this.username = validateUsername(username);
        this.hashedPassword = validateHashedPassword(hashedPassword);
        this.passwordSalt = validateSalt(passwordSalt);
        this.email = validateEmail(email);
        this.fullName = validateFullName(fullName);
        this.userStatus = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * KIỂM TRA PASSWORD - dùng cho login.
     * Trả về false (không throw) nếu password sai - để caller quyết định xử lý.
     */
    public boolean checkPassword(String plainPassword) {
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
     * CẤP ROLE CHO USER HIỆN TẠI
     * <p>
     * Quy trình:
     * 1. Kiểm tra profile không null
     * 2. Lấy kết quả từ profile.getRole() từ profile vắn gắn vào biến role
     * 3. Kiểm tra user đã có role này chưa
     * <p>
     * 1. Nếu profile là AdminProfile thì gắn owner (user hiện tại) đọc thêm
     * <p>
     * 1. Lưu profile vào danh sách roles
     * 2. Đánh dấu entity đã được cập nhật
     */
    public synchronized void grantRole(RoleProfile profile) {
        Objects.requireNonNull(profile, "profile khong the la null");
        Role role = Objects.requireNonNull(profile.getRole(), "profile.role khong the la null");
        if (profiles.containsKey(role)) {
            throw new IllegalStateException(
                    "User '" + username + "' đã có role " + role);
        }

        if (profile instanceof AdminProfile admin) {
            admin.attachOwner(this);
        }

        profiles.put(role, profile);
        markUpdated();
    }

    /**
     * THU HỒI ROLE
     */
    public synchronized boolean revokeRole(Role role) {
        Objects.requireNonNull(role, "Role khong duoc null");
        boolean removed = profiles.remove(role) != null;    // Trả về danh sách đã bị xóa role nếu có role cần được thu hồi. Trả về null nếu không có role cần thu hồi
        if (removed) markUpdated();
        return removed;
    }

    /**
     * CHECK CÓ ROLE NÀO KHÔNG
     */
    public boolean hasRole(Role role) {
        return profiles.containsKey(Objects.requireNonNull(role, "Role khong the la null"));
    }

    /**
     * LẤY DANH SÁCH ROLE
     */
    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(profiles.keySet());
    }

    /**
     * KIỂM TRA XEM CÓ ROLEPFROFILE NÀO KHÔNG
     * <p>
     * Nếu User có role BIDDER → trả về BidderProfile tương ứng.
     * Nếu không có role BIDDER → trả về Optional rỗng.
     */
    public Optional<BidderProfile> asBidder() {
        return getProfileAs(Role.BIDDER, BidderProfile.class);
    }

    public Optional<SellerProfile> asSeller() {
        return getProfileAs(Role.SELLER, SellerProfile.class);
    }

    public Optional<AdminProfile> asAdmin() {
        return getProfileAs(Role.ADMIN, AdminProfile.class);
    }

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public <P extends RoleProfile> Optional<P> getProfileAs(Role role, Class<P> type) {
        Objects.requireNonNull(role);
        Objects.requireNonNull(type);
        RoleProfile p = profiles.get(role);
        if (p == null) return Optional.empty();
        if (!type.isInstance(p)) {
            throw new ClassCastException(
                    "Profile của role " + role + " thực ra là " + p.getClass().getSimpleName() +
                            ", không phải " + type.getSimpleName());
        }
        return Optional.of((P) p);
    }

    /**
     * Lấy BidderProfile bắt buộc của User.
     * <p>
     * Nếu User có role BIDDER → trả về BidderProfile.
     * Nếu không có role BIDDER → ném lỗi (không cho tiếp tục).
     */
    public BidderProfile requireBidder() {
        return asBidder().orElseThrow(() ->
                new NoSuchElementException("User '" + username + "' không phải Bidder"));
    }

    public SellerProfile requireSeller() {
        return asSeller().orElseThrow(() ->
                new NoSuchElementException("User '" + username + "' không phải Seller"));
    }

    public AdminProfile requireAdmin() {
        return asAdmin().orElseThrow(() ->
                new NoSuchElementException("User '" + username + "' không phải Admin"));
    }

    /**
     * Check quyền
     */
    public boolean canBid() {
        return isActive() && hasRole(Role.BIDDER);
    }

    public boolean canSell() {
        return isActive() && hasRole(Role.SELLER);
    }

    public boolean canManageSystem() {
        return isActive() && hasRole(Role.ADMIN);
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
     * Trả hash đã store - KHÔNG bao giờ trả plain password vì không lưu
     */
    public String getHashedPassword() {
        return hashedPassword;
    }

    public String getPasswordSalt() {
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

    @Override
    public String toString() {
        // KHÔNG bao giờ in hashedPassword/salt - tránh leak qua log
        return "User{" +
                "id=" + getId() +
                ", username='" + username + '\'' +
                ", roles=" + profiles.keySet() +
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
        private final Map<Role, RoleProfile> stagedProfiles = new EnumMap<>(Role.class);

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

        public Builder asBidder() {
            return withProfile(new BidderProfile());
        }

        public Builder asBidder(java.math.BigDecimal initialBalance) {
            return withProfile(new BidderProfile(initialBalance));
        }

        public Builder asSeller() {
            return withProfile(new SellerProfile());
        }

        public Builder asSeller(java.math.BigDecimal initialRevenue) {
            return withProfile(new SellerProfile(initialRevenue));
        }

        public Builder asAdmin() {
            return withProfile(new AdminProfile());
        }

        public Builder withProfile(RoleProfile profile) {
            Objects.requireNonNull(profile);
            stagedProfiles.put(profile.getRole(), profile);
            return this;
        }

        public User build() {
            Objects.requireNonNull(username, "username required");
            Objects.requireNonNull(plainPassword, "password required");
            Objects.requireNonNull(email, "email required");
            Objects.requireNonNull(fullName, "fullName required");
            if (plainPassword.length() < 6) {
                throw new IllegalArgumentException("Password phải >= 6 ký tự");
            }

            String salt = PasswordEncoder.generateSalt();
            String hash = PasswordEncoder.hash(plainPassword, salt);

            User u = new User(username, hash, salt, email, fullName);
            stagedProfiles.values().forEach(u::grantRole);
            return u;
        }
    }
}
