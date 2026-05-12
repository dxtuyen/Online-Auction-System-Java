package com.auction.persistence.dao;

import com.auction.model.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract cho User. Tách interface để service không phụ thuộc
 * cụ thể vào MySQL — sau này có thể swap sang implementation khác (Postgres,
 * H2 cho test...) mà không đụng UserManager.
 */
public interface UserDao {

    /** Insert user mới. Throws nếu username/email trùng (unique constraint). */
    void insert(User user);

    /** Update toàn bộ field mutable (status, balance, revenue, hash, email, fullName, role). */
    void update(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    /** Load tất cả user - dùng lúc bootstrap để fill cache. */
    List<User> findAll();

    /** Đếm số user trong DB - dùng để kiểm tra DB có rỗng không (cho seeder). */
    long count();
}
