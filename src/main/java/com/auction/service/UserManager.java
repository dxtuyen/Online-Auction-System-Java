package com.auction.service;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;
import com.auction.model.exception.AuthException;
import com.auction.persistence.dao.MysqlUserDao;
import com.auction.persistence.dao.UserDao;
import com.auction.security.PasswordEncoder;
import com.auction.util.AppLogger;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Quản lý user - registry trung tâm + authentication.
 * <p>
 * ============== KIẾN TRÚC ==============
 * <p>
 * Write-through cache pattern:
 * - In-memory map vẫn giữ làm cache cho find() nhanh + để code cũ không vỡ.
 * - Mọi mutation (register, save) ghi xuống DB qua {@link UserDao}, rồi mới update cache.
 * - Lúc startup, {@link #loadAllFromDb()} fill cache từ DB.
 * <p>
 * Nếu DB write fail → cache không bị thay đổi, throw exception lên caller.
 * <p>
 * ============== DESIGN PATTERNS ==============
 * 1. SINGLETON (Bill Pugh idiom)
 * 2. REPOSITORY-LIKE (findById, findByUsername)
 * 3. FACADE (che giấu hash password, validate uniqueness, DAO)
 * <p>
 * ============== BẢO MẬT ==============
 * - Password hash + salt qua PasswordEncoder TRƯỚC khi lưu
 * - Login không phân biệt "user không tồn tại" vs "sai password" → chống username enumeration
 * - Banned user không login được
 */
public final class UserManager {

    private static final Logger log = AppLogger.get(UserManager.class);

    // ============== SINGLETON ==============

    private static final class Holder {
        private static final UserManager INSTANCE = new UserManager();
    }

    public static UserManager getInstance() {
        return Holder.INSTANCE;
    }

    // ============== FIELDS ==============

    private final UserDao dao;

    /** Cache chính - key: userId */
    private final ConcurrentHashMap<UUID, User> users = new ConcurrentHashMap<>();

    /** Index theo username để check trùng O(1) và login nhanh */
    private final ConcurrentHashMap<String, UUID> usernameIndex = new ConcurrentHashMap<>();

    /** Index theo email để check trùng O(1) — email được DB UNIQUE constrain. */
    private final ConcurrentHashMap<String, UUID> emailIndex = new ConcurrentHashMap<>();

    private UserManager() {
        this.dao = new MysqlUserDao();
    }

    // ============== BOOTSTRAP ==============

    /**
     * Load toàn bộ user từ DB vào cache. Gọi 1 lần lúc server khởi động.
     * Idempotent: clear cache trước khi load → có thể gọi lại để refresh.
     */
    public void loadAllFromDb() {
        users.clear();
        usernameIndex.clear();
        emailIndex.clear();
        for (User u : dao.findAll()) {
            users.put(u.getId(), u);
            usernameIndex.put(u.getUsername(), u.getId());
            emailIndex.put(u.getEmail().toLowerCase(), u.getId());
        }
        log.info(() -> "Đã load " + users.size() + " user từ DB");
    }

    /** Số user trong DB - dùng cho DataSeeder check xem đã seed chưa. */
    public long countInDb() {
        return dao.count();
    }

    // ============== REGISTRATION ==============

    /**
     * Đăng ký user mới. Password được hash + salt trước khi lưu xuống DB.
     *
     * @throws IllegalArgumentException nếu input không hợp lệ
     * @throws IllegalStateException    nếu username đã tồn tại
     */
    public User register(String username, String plainPassword, String email,
                         String fullName, Role role) {
        return register(username, plainPassword, email, fullName, role, null);
    }

    /**
     * Đăng ký user mới + nạp số dư ban đầu.
     *
     * @param initialBalance nếu non-null và > 0 sẽ set vào balance trước khi persist.
     *                       null hoặc 0 → balance mặc định = 0.
     */
    public User register(String username, String plainPassword, String email,
                         String fullName, Role role, BigDecimal initialBalance) {
        Objects.requireNonNull(plainPassword, "password must not be null");
        if (plainPassword.length() < 6) {
            throw new IllegalArgumentException("Password phải >= 6 ký tự");
        }
        if (initialBalance != null && initialBalance.signum() < 0) {
            throw new IllegalArgumentException("Số dư ban đầu không được âm");
        }

        String trimmedUsername = username == null ? null : username.trim();
        if (trimmedUsername != null && usernameIndex.containsKey(trimmedUsername)) {
            throw new IllegalStateException(
                    "Username '" + trimmedUsername + "' đã được sử dụng");
        }

        String emailKey = email == null ? null : email.trim().toLowerCase();
        if (emailKey != null && emailIndex.containsKey(emailKey)) {
            throw new IllegalStateException(
                    "Email '" + email.trim() + "' đã được đăng ký");
        }

        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash(plainPassword, salt);
        User user = new User(trimmedUsername, hash, salt, email, fullName, role);
        if (initialBalance != null && initialBalance.signum() > 0) {
            user.setBalance(initialBalance);
        }

        // Reserve username + email atomic — chống race giữa 2 register cùng lúc.
        UUID prev = usernameIndex.putIfAbsent(user.getUsername(), user.getId());
        if (prev != null) {
            throw new IllegalStateException(
                    "Username '" + user.getUsername() + "' vừa được dùng bởi user khác");
        }
        String finalEmailKey = user.getEmail().toLowerCase();
        UUID prevEmail = emailIndex.putIfAbsent(finalEmailKey, user.getId());
        if (prevEmail != null) {
            usernameIndex.remove(user.getUsername(), user.getId());
            throw new IllegalStateException(
                    "Email '" + user.getEmail() + "' vừa được dùng bởi user khác");
        }

        // Persist DB TRƯỚC khi commit cache. Nếu DB fail → rollback index.
        try {
            dao.insert(user);
        } catch (RuntimeException e) {
            usernameIndex.remove(user.getUsername(), user.getId());
            emailIndex.remove(finalEmailKey, user.getId());
            throw e;
        }
        users.put(user.getId(), user);
        return user;
    }

    /**
     * Đổi email cho user — đồng bộ {@link #emailIndex} với User entity.
     *
     * <p>Bắt buộc đi qua đây thay vì gọi trực tiếp {@code user.setEmail()} để giữ index
     * trùng với state thật + check trùng email với user khác.</p>
     *
     * @throws IllegalStateException nếu email mới đã có user khác dùng
     */
    public void changeEmail(User user, String newEmail) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(newEmail, "newEmail must not be null");
        String newKey = newEmail.trim().toLowerCase();
        String oldKey = user.getEmail().toLowerCase();
        if (newKey.equals(oldKey)) {
            user.setEmail(newEmail);  // validate format dù key không đổi
            dao.update(user);
            return;
        }
        UUID owner = emailIndex.putIfAbsent(newKey, user.getId());
        if (owner != null && !owner.equals(user.getId())) {
            throw new IllegalStateException("Email '" + newEmail.trim() + "' đã được sử dụng");
        }
        try {
            user.setEmail(newEmail);
            dao.update(user);
        } catch (RuntimeException e) {
            emailIndex.remove(newKey, user.getId());
            throw e;
        }
        emailIndex.remove(oldKey, user.getId());
    }

    /**
     * Sync entity state xuống DB sau khi caller mutate User (setBalance, ban, activate...).
     * <p>
     * VÍ DỤ DÙNG:
     * <pre>{@code
     *   user.setBalance(newBalance);
     *   userManager.save(user);   // flush xuống DB
     * }</pre>
     * <p>
     * Throws {@link com.auction.persistence.dao.PersistenceException} nếu DB không có user này.
     */
    public void save(User user) {
        Objects.requireNonNull(user, "user must not be null");
        if (!users.containsKey(user.getId())) {
            throw new IllegalArgumentException(
                    "User chưa được register: " + user.getId());
        }
        dao.update(user);
        // Cache đã reference cùng object → không cần put lại
    }

    // ============== AUTHENTICATION ==============

    /**
     * Đăng nhập bằng username + plain password.
     * <p>
     * BẢO MẬT:
     * - Mọi lỗi đều generic message → chống username enumeration
     * - Luôn chạy hash cả khi không tìm thấy user → timing đều, chống timing attack
     */
    public User login(String username, String plainPassword) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(plainPassword, "password");

        UUID userId = usernameIndex.get(username.trim());
        User user = userId == null ? null : users.get(userId);

        boolean passwordOk;
        if (user == null) {
            PasswordEncoder.hash(plainPassword, "00000000000000000000000000000000");
            passwordOk = false;
        } else {
            passwordOk = user.checkPassword(plainPassword);
        }

        if (user == null || !passwordOk) {
            throw new AuthException("Tên đăng nhập hoặc mật khẩu không đúng");
        }
        if (!user.isActive()) {
            throw new AuthException("Tài khoản đã bị khóa");
        }
        return user;
    }

    // ============== ADMIN OPERATIONS ==============

    /**
     * Ban (khóa) một user. Admin gọi qua AdminController; manager chỉ enforce
     * invariant chứ không tự verify role (router middleware đã check ADMIN).
     *
     * @throws IllegalArgumentException nếu targetId không tồn tại
     * @throws IllegalStateException nếu cố ban admin khác (admin không ban admin)
     */
    public User banUser(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        User target = users.get(targetId);
        if (target == null) {
            throw new IllegalArgumentException("Không tìm thấy user: " + targetId);
        }
        if (target.getRole() == Role.ADMIN) {
            throw new IllegalStateException("Không thể khóa tài khoản quản trị viên khác");
        }
        if (target.getUserStatus() == UserStatus.BANNED) {
            return target; // idempotent
        }
        target.ban();
        dao.update(target);
        return target;
    }

    /**
     * Bỏ ban — đưa user về ACTIVE. Idempotent: nếu user đang ACTIVE thì không-op.
     */
    public User unbanUser(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        User target = users.get(targetId);
        if (target == null) {
            throw new IllegalArgumentException("Không tìm thấy user: " + targetId);
        }
        if (target.isActive()) {
            return target; // idempotent
        }
        target.activate();
        dao.update(target);
        return target;
    }

    // ============== QUERIES ==============

    public Optional<User> findById(UUID userId) {
        return Optional.ofNullable(users.get(Objects.requireNonNull(userId)));
    }

    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        UUID id = usernameIndex.get(username.trim());
        return id == null ? Optional.empty() : findById(id);
    }

    public Collection<User> findAll() {
        return Collections.unmodifiableCollection(users.values());
    }

    public int count() {
        return users.size();
    }

    // ============== TEST ONLY ==============

    /** Reset cache - chỉ dùng trong unit test. KHÔNG xóa DB. */
    void clearForTesting() {
        users.clear();
        usernameIndex.clear();
    }
}
