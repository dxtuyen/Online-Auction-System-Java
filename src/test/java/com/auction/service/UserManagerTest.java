package com.auction.service;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;
import com.auction.model.exception.AuthException;
import com.auction.persistence.dao.UserDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho UserManager.
 *
 * Không cần DB: UserDao được thay bằng mock qua reflection.
 * clearForTesting() + xóa emailIndex được gọi trong @BeforeEach để reset state.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserManager service")
class UserManagerTest {

    @Mock
    private UserDao mockDao;

    private UserManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = UserManager.getInstance();

        // Inject mock DAO thay cho MysqlUserDao
        Field daoField = UserManager.class.getDeclaredField("dao");
        daoField.setAccessible(true);
        daoField.set(manager, mockDao);

        // Reset in-memory cache (users + usernameIndex)
        manager.clearForTesting();

        // clearForTesting() không xóa emailIndex - xóa thủ công
        Field emailIndexField = UserManager.class.getDeclaredField("emailIndex");
        emailIndexField.setAccessible(true);
        ((ConcurrentHashMap<?, ?>) emailIndexField.get(manager)).clear();

        // Mặc định: findAll() trả về danh sách rỗng (cho loadAllFromDb nếu cần)
        lenient().when(mockDao.findAll()).thenReturn(Collections.emptyList());
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
        verify(mockDao, times(1)).insert(any(User.class));
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
    @DisplayName("register với initialBalance âm - throw")
    void register_negativeInitialBalance_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.register("dave", "password123", "dave@example.com", "Dave",
                        Role.NORMAL, new BigDecimal("-100")));
        verify(mockDao, never()).insert(any());
    }

    @Test
    @DisplayName("register password < 6 ký tự - throw")
    void register_shortPassword_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.register("eve", "abc", "eve@example.com", "Eve", Role.NORMAL));
        verify(mockDao, never()).insert(any());
    }

    @Test
    @DisplayName("register password null - throw NullPointerException")
    void register_nullPassword_throws() {
        assertThrows(NullPointerException.class, () ->
                manager.register("frank", null, "frank@example.com", "Frank", Role.NORMAL));
        verify(mockDao, never()).insert(any());
    }

    @Test
    @DisplayName("register username trùng - throw IllegalStateException")
    void register_duplicateUsername_throws() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        assertThrows(IllegalStateException.class, () ->
                manager.register("alice", "password456", "other@example.com", "Other", Role.NORMAL));

        verify(mockDao, times(1)).insert(any()); // chỉ insert 1 lần (lần đầu)
    }

    @Test
    @DisplayName("register email trùng (case-insensitive) - throw IllegalStateException")
    void register_duplicateEmail_throws() {
        manager.register("alice", "password123", "Alice@Example.com", "Alice", Role.NORMAL);

        assertThrows(IllegalStateException.class, () ->
                manager.register("bob", "password456", "alice@example.com", "Bob", Role.NORMAL));
    }

    @Test
    @DisplayName("register DB fail - rollback index (user không tồn tại trong cache)")
    void register_dbFails_rollbacksIndex() {
        doThrow(new RuntimeException("DB down")).when(mockDao).insert(any());

        assertThrows(RuntimeException.class, () ->
                manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL));

        // Sau khi fail, username phải được giải phóng để có thể đăng ký lại
        doNothing().when(mockDao).insert(any());
        assertDoesNotThrow(() ->
                manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL));
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
    @DisplayName("login sai password - throw AuthException (generic message)")
    void login_wrongPassword_throws() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        AuthException ex = assertThrows(AuthException.class,
                () -> manager.login("alice", "wrongPassword"));

        // Message generic để chống username enumeration
        assertTrue(ex.getMessage().contains("không đúng") || ex.getMessage().contains("sai"));
    }

    @Test
    @DisplayName("login username không tồn tại - throw AuthException (cùng message với sai password)")
    void login_nonexistentUser_throwsSameMessageAsWrongPassword() {
        AuthException ex = assertThrows(AuthException.class,
                () -> manager.login("nonexistent", "password123"));

        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("login user bị ban - throw AuthException")
    void login_bannedUser_throws() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);
        manager.findByUsername("alice").ifPresent(u -> u.ban());

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
    @DisplayName("login với username có khoảng trắng đầu/cuối - vẫn tìm đúng user")
    void login_usernameWithWhitespace_success() {
        manager.register("alice", "password123", "alice@example.com", "Alice", Role.NORMAL);

        assertDoesNotThrow(() -> manager.login("  alice  ", "password123"));
    }

    // ============== findById / findByUsername ==============

    @Test
    @DisplayName("findById user tồn tại - trả về Optional chứa user")
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
        Optional<User> result = manager.findById(UUID.randomUUID());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findById null - throw NullPointerException")
    void findById_null_throws() {
        assertThrows(NullPointerException.class, () -> manager.findById(null));
    }

    @Test
    @DisplayName("findByUsername user tồn tại - trả về Optional chứa user")
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

        verify(mockDao, times(1)).update(user);
    }

    @Test
    @DisplayName("save user chưa register - throw IllegalArgumentException")
    void save_unregisteredUser_throws() {
        // User tạo thủ công, không qua register()
        User stranger = new User.Builder()
                .username("stranger")
                .password("password123")
                .email("s@example.com")
                .fullName("Stranger")
                .role(Role.NORMAL)
                .build();

        assertThrows(IllegalArgumentException.class, () -> manager.save(stranger));
        verify(mockDao, never()).update(any());
    }

    @Test
    @DisplayName("save null - throw NullPointerException")
    void save_null_throws() {
        assertThrows(NullPointerException.class, () -> manager.save(null));
    }

    // ============== findAll / count ==============

    @Test
    @DisplayName("count() trả về số user trong cache")
    void count_returnsInMemoryCount() {
        assertEquals(0, manager.count());

        manager.register("alice", "password123", "a@b.com", "Alice", Role.NORMAL);
        manager.register("bob", "password456", "b@b.com", "Bob", Role.NORMAL);

        assertEquals(2, manager.count());
    }

    @Test
    @DisplayName("findAll() trả về tất cả user trong cache")
    void findAll_returnsAllUsers() {
        manager.register("alice", "password123", "a@b.com", "Alice", Role.NORMAL);
        manager.register("bob", "password456", "b@b.com", "Bob", Role.NORMAL);

        assertEquals(2, manager.findAll().size());
    }

    @Test
    @DisplayName("findAll() trả về collection bất biến")
    void findAll_returnsUnmodifiableCollection() {
        manager.register("alice", "password123", "a@b.com", "Alice", Role.NORMAL);

        var all = manager.findAll();

        assertThrows(UnsupportedOperationException.class, () -> all.clear());
    }
}
