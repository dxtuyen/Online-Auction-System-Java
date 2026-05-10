package com.auction.service;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.model.exception.AuthException;
import com.auction.security.PasswordEncoder;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý user - registry trung tâm + xử lý authentication.
 * <p>
 * ============== DESIGN PATTERNS ==============
 * <p>
 * 1. SINGLETON (Bill Pugh idiom)
 * - Lazy init, thread-safe, KHÔNG cần synchronized hay volatile
 * - JVM đảm bảo Holder chỉ load 1 lần khi getInstance() lần đầu
 * <p>
 * 2. REPOSITORY-LIKE
 * - findById, findByUsername giống pattern Repository
 * <p>
 * 3. FACADE
 * - Che giấu logic hash password, validate uniqueness...
 * <p>
 * ============== BẢO MẬT ==============
 * <p>
 * - Password được hash + salt qua PasswordEncoder TRƯỚC khi lưu
 * - Login KHÔNG phân biệt "user không tồn tại" vs "sai password" → tránh
 * username enumeration
 * - Banned user không login được
 * - Duy trì index username → check trùng O(1) thay vì duyệt O(n)
 */
public final class UserManager {

    // ============== SINGLETON (Bill Pugh idiom) ==============

    private static final class Holder {
        private static final UserManager INSTANCE = new UserManager();
    }

    public static UserManager getInstance() {
        return Holder.INSTANCE;
    }

    // ============== FIELDS ==============

    /**
     * Storage chính - key: userId
     */
    private final ConcurrentHashMap<UUID, User> users = new ConcurrentHashMap<>();

    /**
     * Index theo username để check trùng O(1) và login nhanh
     */
    private final ConcurrentHashMap<String, UUID> usernameIndex = new ConcurrentHashMap<>();

    private UserManager() { /* singleton */ }

    // ============== REGISTRATION ==============

    /**
     * Đăng ký user mới với 1 hoặc nhiều role profile.
     * <p>
     * Pasword được hash + salt rồi mới lưu. Plain password không bao giờ tồn
     * tại trong memory lâu hơn cần thiết.
     *
     * @param plainPassword password do user nhập (plain text - sẽ hash bên trong)
     * @return User vừa tạo
     * @throws IllegalArgumentException nếu input không hợp lệ
     * @throws IllegalStateException    nếu username đã tồn tại
     */
    public User register(String username, String plainPassword, String email,
                         String fullName, Role role) {
        Objects.requireNonNull(plainPassword, "password must not be null");
        if (plainPassword.length() < 6) {
            throw new IllegalArgumentException("Password phải >= 6 ký tự");
        }

        // Check trùng username TRƯỚC khi tạo User để khỏi phí công hash
        // putIfAbsent atomic - 2 thread cùng register trùng username thì chỉ 1 thắng
        String trimmedUsername = username == null ? null : username.trim();
        if (trimmedUsername != null && usernameIndex.containsKey(trimmedUsername)) {
            throw new IllegalStateException(
                    "Username '" + trimmedUsername + "' đã được sử dụng");
        }

        // Hash password
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash(plainPassword, salt);

        // Tạo user (constructor đã validate username/email/fullName)
        User user = new User(trimmedUsername, hash, salt, email, fullName, role);

        // Reserve username atomic - nếu thua race, hủy
        UUID prev = usernameIndex.putIfAbsent(user.getUsername(), user.getId());
        if (prev != null) {
            throw new IllegalStateException(
                    "Username '" + user.getUsername() + "' vừa được dùng bởi user khác");
        }
        users.put(user.getId(), user);
        return user;
    }

    // ============== AUTHENTICATION ==============

    /**
     * Đăng nhập bằng username + plain password.
     * <p>
     * BẢO MẬT:
     * - Mọi lỗi đều trả về cùng message generic → attacker không biết username
     * nào tồn tại ("user not found" và "wrong password" chỉ chung 1 message).
     * - Vẫn check password ngay cả khi username không tồn tại để timing đồng đều
     * (chống timing attack — attacker không thể đoán username dựa vào response time).
     */
    public User login(String username, String plainPassword) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(plainPassword, "password");

        UUID userId = usernameIndex.get(username.trim());
        User user = userId == null ? null : users.get(userId);

        // Luôn chạy hash để timing đều - không expose việc user có tồn tại hay không
        boolean passwordOk;
        if (user == null) {
            // Hash với salt giả để tốn time tương đương trường hợp có user
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

    /**
     * Reset state - chỉ dùng trong unit test
     */
    void clearForTesting() {
        users.clear();
        usernameIndex.clear();
    }
}
