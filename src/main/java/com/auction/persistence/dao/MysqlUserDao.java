package com.auction.persistence.dao;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;
import com.auction.persistence.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MysqlUserDao implements UserDao {

    private static final String COLS =
            "id, created_at, updated_at, username, hashed_password, password_salt, " +
            "email, full_name, user_status, role, balance, revenue";

    private static final String SELECT_ALL =
            "SELECT " + COLS + " FROM users";

    private static final String INSERT_SQL =
            "INSERT INTO users (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE_SQL =
            "UPDATE users SET updated_at=?, hashed_password=?, password_salt=?, " +
            "email=?, full_name=?, user_status=?, role=?, balance=?, revenue=? " +
            "WHERE id=?";

    private final Database db;

    public MysqlUserDao() {
        this.db = Database.getInstance();
    }

    @Override
    public void insert(User user) {
        Objects.requireNonNull(user);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            ps.setString(1, user.getId().toString());
            ps.setTimestamp(2, Timestamp.valueOf(user.getCreatedAt()));
            ps.setTimestamp(3, Timestamp.valueOf(user.getUpdatedAt()));
            ps.setString(4, user.getUsername());
            ps.setString(5, user.getHashedPassword());
            ps.setString(6, user.getPasswordSalt());
            ps.setString(7, user.getEmail());
            ps.setString(8, user.getFullName());
            ps.setString(9, user.getUserStatus().name());
            ps.setString(10, user.getRole().name());
            ps.setBigDecimal(11, user.getBalance());
            ps.setBigDecimal(12, user.getRevenue());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Insert user thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(User user) {
        Objects.requireNonNull(user);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(UPDATE_SQL)) {
            ps.setTimestamp(1, Timestamp.valueOf(user.getUpdatedAt()));
            ps.setString(2, user.getHashedPassword());
            ps.setString(3, user.getPasswordSalt());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getUserStatus().name());
            ps.setString(7, user.getRole().name());
            ps.setBigDecimal(8, user.getBalance());
            ps.setBigDecimal(9, user.getRevenue());
            ps.setString(10, user.getId().toString());
            int n = ps.executeUpdate();
            if (n == 0) {
                throw new PersistenceException("User không tồn tại trong DB: " + user.getId());
            }
        } catch (SQLException e) {
            throw new PersistenceException("Update user thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        Objects.requireNonNull(id);
        return querySingle(SELECT_ALL + " WHERE id=?", ps -> ps.setString(1, id.toString()));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return querySingle(SELECT_ALL + " WHERE username=?",
                ps -> ps.setString(1, username.trim()));
    }

    @Override
    public List<User> findAll() {
        List<User> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new PersistenceException("findAll user thất bại: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public long count() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new PersistenceException("count user thất bại: " + e.getMessage(), e);
        }
    }

    private Optional<User> querySingle(String sql, ParamSetter setter) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Query user thất bại: " + e.getMessage(), e);
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        BigDecimal balance = rs.getBigDecimal("balance");
        BigDecimal revenue = rs.getBigDecimal("revenue");

        return new User(
                id,
                createdTs.toLocalDateTime(),
                updatedTs.toLocalDateTime(),
                rs.getString("username"),
                rs.getString("hashed_password"),
                rs.getString("password_salt"),
                rs.getString("email"),
                rs.getString("full_name"),
                UserStatus.valueOf(rs.getString("user_status")),
                Role.valueOf(rs.getString("role")),
                balance == null ? BigDecimal.ZERO : balance,
                revenue == null ? BigDecimal.ZERO : revenue
        );
    }

    @FunctionalInterface
    private interface ParamSetter {
        void set(PreparedStatement ps) throws SQLException;
    }
}
