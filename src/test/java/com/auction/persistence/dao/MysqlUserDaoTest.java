package com.auction.persistence.dao;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;
import com.auction.security.PasswordEncoder;
import com.auction.testsupport.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MysqlUserDao (H2)")
class MysqlUserDaoTest {

    private final UserDao dao = new MysqlUserDao();

    @BeforeEach
    void setUp() {
        TestDb.clean();
    }

    private static User newUser(String username, String email) {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        return new User(username, hash, salt, email, "Full Name", Role.NORMAL);
    }

    @Test
    @DisplayName("insert + findById round-trip")
    void insertAndFindById() {
        User u = newUser("alice", "alice@example.com");
        dao.insert(u);

        Optional<User> found = dao.findById(u.getId());

        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
        assertEquals("alice@example.com", found.get().getEmail());
    }

    @Test
    @DisplayName("findById không tồn tại - empty")
    void findById_missing_empty() {
        assertTrue(dao.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("findByUsername trả về đúng user")
    void findByUsername() {
        dao.insert(newUser("bob", "bob@example.com"));
        assertTrue(dao.findByUsername("bob").isPresent());
        assertTrue(dao.findByUsername("nobody").isEmpty());
    }

    @Test
    @DisplayName("update sửa balance/revenue/status")
    void update() {
        User u = newUser("carol", "carol@example.com");
        dao.insert(u);

        u.setBalance(new BigDecimal("1000.0000"));
        u.setRevenue(new BigDecimal("250.0000"));
        u.ban();
        dao.update(u);

        User reloaded = dao.findById(u.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.0000").compareTo(reloaded.getBalance()));
        assertEquals(0, new BigDecimal("250.0000").compareTo(reloaded.getRevenue()));
        assertEquals(UserStatus.BANNED, reloaded.getUserStatus());
    }

    @Test
    @DisplayName("findAll + count")
    void findAllAndCount() {
        dao.insert(newUser("user1", "u1@example.com"));
        dao.insert(newUser("user2", "u2@example.com"));

        assertEquals(2, dao.findAll().size());
        assertEquals(2, dao.count());
    }

    @Test
    @DisplayName("insert trùng username - ném exception")
    void insertDuplicateUsername_throws() {
        dao.insert(newUser("dup", "a@example.com"));
        assertThrows(RuntimeException.class,
                () -> dao.insert(newUser("dup", "b@example.com")));
    }
}
