package com.auction.service;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.model.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho UserManager — không cần MySQL.
 *
 * Dùng package-private test constructor (UserManager(UserDao)) + InMemoryUserDao
 * để cô lập hoàn toàn khỏi Database / HikariCP.
 */
@DisplayName("UserManager service")
class UserManagerTest {

    private InMemoryUserDao dao;
    private UserManager manager;

    @BeforeEach
    void setUp() {
        dao = new InMemoryUserDao();
        manager = new UserManager(dao);   // test constructor, không cần Singleton
    }

    // ============== register ==============

    @Test
    @DisplayName("register user mới - gọi dao.insert() và trả về user")
    void register_newUser_insertsAndReturnsUser() {
        User result = manager.register(
                "alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(1, dao.insertCount);
    }

    @Test
    @DisplayName("register với initialBalance - user có số dư đúng")
    void register_withInitialBalance_setsBalance() {
        BigDecimal initial = new BigDecimal("5000000");
        User result = manager.register(
                "bob", "password123", "bob@example.com", "Bob", Role.NORMAL, initial);

        assertEquals(initial, result.getBalance());
    }

    @Test
    @DisplayName("register với initialBalance = null - balance mặc định = 0")
    void register_nullInitialBalance_zeroBalance() {
        User result = manager.register(
                "charlie", "password123", "charlie@example.com", "Charlie", Role.NORMAL, null);

        assertEquals(BigDecimal.ZERO, result.getBalance());
    }

    @Test
    @DisplayName("register với initialBalance âm - throw, không gọi dao.insert()")
    void register_negativeInitialBalance_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.register("dave", "password123", "dave@example.com", "Dave",
                        Role.NORMAL, new BigDecimal("-100")));

        assertEquals(0, dao.insertCount);
    }

    @Test
    @DisplayName("register password < 6 ký tự - throw, không gọi dao.insert()")
    void register_shortPassword_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.register("eve", "abc", "eve@example.com", "Eve", Role.NORMAL));

        assertEquals(0, dao.insertCount);
    }

    @Test
    @DisplayName("register password null - throw NullPointerException")
    void register_nullPassword_throws() {
        assertThrows(NullPointerException.class, () ->
                manager.register("frank", null, "frank@example.com", "Frank", Role.NORMAL));

        assertEquals(0, dao.insertCount);
    }

    @Test
    @DisplayName("register username trùng - throw, chỉ insert lần đầu")
    void register_duplicateUsername_throws() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        assertThrows(IllegalStateException.class, () ->
                manager.register("alice", "password456", "other@example.com", "Other", Role.NORMAL));

        assertEquals(1, dao.insertCount);
    }

    @Test
    @DisplayName("register email trùng (case-insensitive) - throw")
    void register_duplicateEmail_throws() {
        manager.register("alice", "password123", "Alice@Example.com", "Alice", Role.NORMAL);

        assertThrows(IllegalStateException.class, () ->
                manager.register("bob", "password456", "alice@example.com", "Bob", Role.NORMAL));
    }

    @Test
    @DisplayName("register khi dao.insert() fail - rollback index, cho phép đăng ký lại")
    void register_dbFails_rollbacksIndexAllowsRetry() {
        dao.insertShouldFail = true;

        assertThrows(RuntimeException.class, () ->
                manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL));

        // Sau khi rollback, cùng username + email phải được dùng lại được
        dao.insertShouldFail = false;
        assertDoesNotThrow(() ->
                manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL));
    }

    @Test
    @DisplayName("register ADMIN role - user có role ADMIN")
    void register_adminRole_setsRole() {
        User result = manager.register(
                "admin_u", "password123", "admin@example.com", "Admin", Role.ADMIN);

        assertEquals(Role.ADMIN, result.getRole());
        assertFalse(result.canBid());
        assertFalse(result.canSell());
    }

    // ============== login ==============

    @Test
    @DisplayName("login đúng credentials - trả về user")
    void login_validCredentials_returnsUser() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        User result = manager.login("alice", "password123");

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
    }

    @Test
    @DisplayName("login sai password - throw AuthException")
    void login_wrongPassword_throws() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        assertThrows(AuthException.class, () -> manager.login("alice", "wrongPassword"));
    }

    @Test
    @DisplayName("login username không tồn tại - throw AuthException (cùng loại với sai password)")
    void login_nonexistentUser_throwsAuthException() {
        assertThrows(AuthException.class, () -> manager.login("nobody", "password123"));
    }

    @Test
    @DisplayName("login user bị ban - throw AuthException")
    void login_bannedUser_throws() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);
        manager.findByUsername("alice").ifPresent(User::ban);

        assertThrows(AuthException.class, () -> manager.login("alice", "password123"));
    }

    @Test
    @DisplayName("login username null - throw NullPointerException")
    void login_nullUsername_throws() {
        assertThrows(NullPointerException.class, () -> manager.login(null, "password123"));
    }

    @Test
    @DisplayName("login password null - throw NullPointerException")
    void login_nullPassword_throws() {
        assertThrows(NullPointerException.class, () -> manager.login("alice", null));
    }

    @Test
    @DisplayName("login username có khoảng trắng đầu/cuối - tự trim và tìm đúng user")
    void login_usernameWithWhitespace_success() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        assertDoesNotThrow(() -> manager.login("  alice  ", "password123"));
    }

    // ============== findById ==============

    @Test
    @DisplayName("findById user tồn tại - trả về Optional có giá trị")
    void findById_existingUser_returnsUser() {
        User registered = manager.register(
                "alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        Optional<User> result = manager.findById(registered.getId());

        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUsername());
    }

    @Test
    @DisplayName("findById không tồn tại - trả về Optional.empty()")
    void findById_nonexistentUser_returnsEmpty() {
        assertTrue(manager.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("findById null - throw NullPointerException")
    void findById_null_throws() {
        assertThrows(NullPointerException.class, () -> manager.findById(null));
    }

    // ============== findByUsername ==============

    @Test
    @DisplayName("findByUsername user tồn tại - trả về Optional có giá trị")
    void findByUsername_existingUser_returnsUser() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        Optional<User> result = manager.findByUsername("alice");

        assertTrue(result.isPresent());
        assertEquals("alice@example.com", result.get().getEmail());
    }

    @Test
    @DisplayName("findByUsername không tồn tại - trả về Optional.empty()")
    void findByUsername_nonexistentUser_returnsEmpty() {
        assertTrue(manager.findByUsername("nobody").isEmpty());
    }

    @Test
    @DisplayName("findByUsername null - trả về Optional.empty() (không throw)")
    void findByUsername_null_returnsEmpty() {
        assertTrue(manager.findByUsername(null).isEmpty());
    }

    // ============== save ==============

    @Test
    @DisplayName("save user đã register - gọi dao.update()")
    void save_registeredUser_callsDaoUpdate() {
        User user = manager.register(
                "alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        manager.save(user);

        assertEquals(1, dao.updateCount);
    }

    @Test
    @DisplayName("save user chưa register - throw IllegalArgumentException, không gọi dao.update()")
    void save_unregisteredUser_throws() {
        User stranger = new User.Builder()
                .username("stranger")
                .password("password123")
                .email("s@example.com")
                .fullName("Stranger")
                .role(Role.NORMAL)
                .build();

        assertThrows(IllegalArgumentException.class, () -> manager.save(stranger));
        assertEquals(0, dao.updateCount);
    }

    @Test
    @DisplayName("save null - throw NullPointerException")
    void save_null_throws() {
        assertThrows(NullPointerException.class, () -> manager.save(null));
    }

    // ============== count / findAll ==============

    @Test
    @DisplayName("count() trả về số user đang có trong cache")
    void count_returnsInMemoryCount() {
        assertEquals(0, manager.count());

        manager.register("alice", "password123", "a@b.com", "Alice", Role.NORMAL);
        manager.register("bob", "password456", "b@b.com", "Bob", Role.NORMAL);

        assertEquals(2, manager.count());
    }

    @Test
    @DisplayName("findAll() trả về đúng số user trong cache")
    void findAll_returnsAllUsers() {
        manager.register("alice", "password123", "a@b.com", "Alice", Role.NORMAL);
        manager.register("bob", "password456", "b@b.com", "Bob", Role.NORMAL);

        assertEquals(2, manager.findAll().size());
    }

    @Test
    @DisplayName("findAll() trả về collection bất biến (unmodifiable)")
    void findAll_returnsUnmodifiableCollection() {
        manager.register("alice", "password123", "a@b.com", "Alice", Role.NORMAL);

        assertThrows(UnsupportedOperationException.class,
                () -> manager.findAll().clear());
    }
}
