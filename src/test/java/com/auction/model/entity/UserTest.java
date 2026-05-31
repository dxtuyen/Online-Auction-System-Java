package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.security.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User entity")
class UserTest {

    private static User buildUser() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        return new User("john_doe", hash, salt, "john@example.com", "John Doe", Role.NORMAL);
    }

    // ========== CONSTRUCTOR VALIDATION ==========

    @Test
    @DisplayName("Tạo user hợp lệ - không throw")
    void constructor_validParams_noException() {
        assertDoesNotThrow(UserTest::buildUser);
    }

    @Test
    @DisplayName("Username null - throw NullPointerException")
    void constructor_nullUsername_throws() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        assertThrows(NullPointerException.class,
                () -> new User(null, hash, salt, "a@b.com", "Full Name", Role.NORMAL));
    }

    @Test
    @DisplayName("Username quá ngắn (< 3 ký tự) - throw")
    void constructor_tooShortUsername_throws() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        assertThrows(IllegalArgumentException.class,
                () -> new User("ab", hash, salt, "a@b.com", "Full Name", Role.NORMAL));
    }

    @Test
    @DisplayName("Username chứa ký tự đặc biệt - throw")
    void constructor_invalidUsernameChars_throws() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        assertThrows(IllegalArgumentException.class,
                () -> new User("user@name!", hash, salt, "a@b.com", "Full Name", Role.NORMAL));
    }

    @Test
    @DisplayName("Email không hợp lệ - throw")
    void constructor_invalidEmail_throws() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        assertThrows(IllegalArgumentException.class,
                () -> new User("john_doe", hash, salt, "not-an-email", "John Doe", Role.NORMAL));
    }

    @Test
    @DisplayName("Email null - throw")
    void constructor_nullEmail_throws() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        assertThrows(NullPointerException.class,
                () -> new User("john_doe", hash, salt, null, "John Doe", Role.NORMAL));
    }

    @Test
    @DisplayName("FullName rỗng - throw")
    void constructor_blankFullName_throws() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        assertThrows(IllegalArgumentException.class,
                () -> new User("john_doe", hash, salt, "a@b.com", "   ", Role.NORMAL));
    }

    @Test
    @DisplayName("Role null - throw")
    void constructor_nullRole_throws() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        assertThrows(NullPointerException.class,
                () -> new User("john_doe", hash, salt, "a@b.com", "Full Name", null));
    }

    @Test
    @DisplayName("User mới có balance = 0 và revenue = 0")
    void constructor_newUser_zeroBalanceAndRevenue() {
        User user = buildUser();
        assertEquals(BigDecimal.ZERO, user.getBalance());
        assertEquals(BigDecimal.ZERO, user.getRevenue());
    }

    @Test
    @DisplayName("User mới mặc định là ACTIVE")
    void constructor_newUser_isActive() {
        assertTrue(buildUser().isActive());
    }

    // ========== PASSWORD ==========

    @Test
    @DisplayName("checkPassword đúng password - trả về true")
    void checkPassword_correctPassword_true() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("mySecret99", salt);
        User user = new User("user123", hash, salt, "u@b.com", "User", Role.NORMAL);
        assertTrue(user.checkPassword("mySecret99"));
    }

    @Test
    @DisplayName("checkPassword sai password - trả về false")
    void checkPassword_wrongPassword_false() {
        User user = buildUser();
        assertFalse(user.checkPassword("wrongPass"));
    }

    @Test
    @DisplayName("checkPassword null - trả về false (không throw)")
    void checkPassword_nullPassword_false() {
        User user = buildUser();
        assertFalse(user.checkPassword(null));
    }

    @Test
    @DisplayName("changePassword cập nhật hash mới")
    void changePassword_updatesHash() {
        User user = buildUser();
        user.changePassword("newSecret!");
        assertTrue(user.checkPassword("newSecret!"));
        assertFalse(user.checkPassword("password123"));
    }

    @Test
    @DisplayName("changePassword ngắn hơn 6 ký tự - throw")
    void changePassword_tooShort_throws() {
        User user = buildUser();
        assertThrows(IllegalArgumentException.class, () -> user.changePassword("abc"));
    }

    // ========== PERMISSIONS ==========

    @Test
    @DisplayName("NORMAL user ACTIVE - canBid = true")
    void canBid_activeNormalUser_true() {
        assertTrue(buildUser().canBid());
    }

    @Test
    @DisplayName("ADMIN user - canBid = false")
    void canBid_adminUser_false() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("adminPass1", salt);
        User admin = new User("admin_user", hash, salt, "admin@b.com", "Admin", Role.ADMIN);
        assertFalse(admin.canBid());
    }

    @Test
    @DisplayName("BANNED user - canBid = false")
    void canBid_bannedUser_false() {
        User user = buildUser();
        user.ban();
        assertFalse(user.canBid());
    }

    @Test
    @DisplayName("NORMAL user ACTIVE - canSell = true")
    void canSell_activeNormalUser_true() {
        assertTrue(buildUser().canSell());
    }

    @Test
    @DisplayName("ADMIN user - canSell = false")
    void canSell_adminUser_false() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("adminPass1", salt);
        User admin = new User("admin_user", hash, salt, "admin2@b.com", "Admin", Role.ADMIN);
        assertFalse(admin.canSell());
    }

    @Test
    @DisplayName("ban() -> isActive() = false")
    void ban_deactivatesUser() {
        User user = buildUser();
        user.ban();
        assertFalse(user.isActive());
    }

    @Test
    @DisplayName("activate() sau ban() -> isActive() = true")
    void activate_reactivatesBannedUser() {
        User user = buildUser();
        user.ban();
        user.activate();
        assertTrue(user.isActive());
    }

    // ========== BALANCE OPERATIONS ==========

    @Test
    @DisplayName("tryReserve đủ tiền - trừ balance và trả true")
    void tryReserve_sufficientBalance_deductsAndReturnsTrue() {
        User user = buildUser();
        user.setBalance(new BigDecimal("500"));

        boolean result = user.tryReserve(new BigDecimal("200"));

        assertTrue(result);
        assertEquals(new BigDecimal("300"), user.getBalance());
    }

    @Test
    @DisplayName("tryReserve không đủ tiền - giữ nguyên balance và trả false")
    void tryReserve_insufficientBalance_returnsFalseNoChange() {
        User user = buildUser();
        user.setBalance(new BigDecimal("100"));

        boolean result = user.tryReserve(new BigDecimal("200"));

        assertFalse(result);
        assertEquals(new BigDecimal("100"), user.getBalance());
    }

    @Test
    @DisplayName("tryReserve amount = 0 - throw")
    void tryReserve_zeroAmount_throws() {
        User user = buildUser();
        user.setBalance(new BigDecimal("1000"));
        assertThrows(IllegalArgumentException.class,
                () -> user.tryReserve(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("tryReserve amount âm - throw")
    void tryReserve_negativeAmount_throws() {
        User user = buildUser();
        user.setBalance(new BigDecimal("1000"));
        assertThrows(IllegalArgumentException.class,
                () -> user.tryReserve(new BigDecimal("-50")));
    }

    @Test
    @DisplayName("release() cộng lại balance")
    void release_addsBackToBalance() {
        User user = buildUser();
        user.setBalance(new BigDecimal("300"));
        user.tryReserve(new BigDecimal("200"));

        user.release(new BigDecimal("200"));

        assertEquals(new BigDecimal("300"), user.getBalance());
    }

    @Test
    @DisplayName("addRevenue() tăng revenue")
    void addRevenue_increasesRevenue() {
        User user = buildUser();
        user.addRevenue(new BigDecimal("1000"));
        user.addRevenue(new BigDecimal("500"));
        assertEquals(new BigDecimal("1500"), user.getRevenue());
    }

    @Test
    @DisplayName("addRevenue amount âm - throw")
    void addRevenue_negativeAmount_throws() {
        User user = buildUser();
        assertThrows(IllegalArgumentException.class,
                () -> user.addRevenue(new BigDecimal("-100")));
    }

    @Test
    @DisplayName("setBalance âm - throw")
    void setBalance_negative_throws() {
        User user = buildUser();
        assertThrows(IllegalArgumentException.class,
                () -> user.setBalance(new BigDecimal("-1")));
    }

    // ========== BUILDER ==========

    @Test
    @DisplayName("Builder tạo user hợp lệ")
    void builder_validParams_createsUser() {
        User user = new User.Builder()
                .username("builder_u")
                .password("securePass")
                .email("builder@test.com")
                .fullName("Builder User")
                .role(Role.NORMAL)
                .build();

        assertNotNull(user.getId());
        assertTrue(user.checkPassword("securePass"));
        assertEquals("builder_u", user.getUsername());
    }

    @Test
    @DisplayName("Builder password ngắn hơn 6 ký tự - throw")
    void builder_shortPassword_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new User.Builder()
                        .username("builder_u")
                        .password("abc")
                        .email("b@b.com")
                        .fullName("Builder")
                        .role(Role.NORMAL)
                        .build());
    }
}
