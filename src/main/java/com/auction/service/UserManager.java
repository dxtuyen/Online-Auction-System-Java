package com.auction.service;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
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

public final class UserManager {

    private static final Logger log = AppLogger.get(UserManager.class);

    private static final class Holder {
        private static final UserManager INSTANCE = new UserManager();
    }

    public static UserManager getInstance() {
        return Holder.INSTANCE;
    }

    private final UserDao dao;

    private final ConcurrentHashMap<UUID, User> users = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, UUID> usernameIndex = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, UUID> emailIndex = new ConcurrentHashMap<>();

    private UserManager() {
        this.dao = new MysqlUserDao();
    }

    UserManager(UserDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao must not be null");
    }

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

    public long countInDb() {
        return dao.count();
    }

    public User register(String username, String plainPassword, String email,
                         String fullName, Role role) {
        return register(username, plainPassword, email, fullName, role, null);
    }

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

    public void save(User user) {
        Objects.requireNonNull(user, "user must not be null");
        if (!users.containsKey(user.getId())) {
            throw new IllegalArgumentException(
                    "User chưa được register: " + user.getId());
        }
        dao.update(user);

    }

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

    void clearForTesting() {
        users.clear();
        usernameIndex.clear();
    }
}
