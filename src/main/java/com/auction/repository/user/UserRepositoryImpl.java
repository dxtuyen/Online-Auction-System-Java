package com.auction.repository.user;

import com.auction.model.entity.User;
import com.auction.util.DBConnection;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

/*

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public class PBKDF2Hashing {
    public static String hashPassword(String password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        byte[] hash = factory.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(hash);
    }
}
*/

public class UserRepositoryImpl implements UserRepository {

    @Override
    public User create(String username, String password) {
        String sql = "INSERT INTO users (id, username, password) VALUES (?, ?, ?)";
        UUID id = UUID.randomUUID();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, id.toString());
            pstmt.setString(2, username);
            pstmt.setString(3, password);

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                return new User(id, username, password);
            } else {
                throw new RuntimeException("Loi insert");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Loi sql: " + e.getMessage());
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new User(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("username"),
                            rs.getString("password")));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Loi sql: " + e.getMessage());
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new User(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("username"),
                            rs.getString("password")));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Loi sql: " + e.getMessage());
        }
    }

}
