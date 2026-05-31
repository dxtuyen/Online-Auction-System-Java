package com.auction.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordEncoder")
class PasswordEncoderTest {

    // ========== generateSalt ==========

    @Test
    @DisplayName("generateSalt trả về chuỗi hex 32 ký tự")
    void generateSalt_returns32CharHex() {
        String salt = PasswordEncoder.generateSalt();
        assertNotNull(salt);
        assertEquals(32, salt.length());
        assertTrue(salt.matches("[0-9a-f]+"), "Salt phải là hex lowercase");
    }

    @RepeatedTest(5)
    @DisplayName("generateSalt tạo giá trị ngẫu nhiên mỗi lần")
    void generateSalt_isRandom() {
        String salt1 = PasswordEncoder.generateSalt();
        String salt2 = PasswordEncoder.generateSalt();
        assertNotEquals(salt1, salt2);
    }

    // ========== hash ==========

    @Test
    @DisplayName("hash cùng password + salt - kết quả giống nhau (deterministic)")
    void hash_sameSaltSamePassword_sameResult() {
        String salt = PasswordEncoder.generateSalt();
        String h1 = PasswordEncoder.hash("myPass123", salt);
        String h2 = PasswordEncoder.hash("myPass123", salt);
        assertEquals(h1, h2);
    }

    @Test
    @DisplayName("hash khác salt - kết quả khác nhau")
    void hash_differentSalt_differentResult() {
        String salt1 = PasswordEncoder.generateSalt();
        String salt2 = PasswordEncoder.generateSalt();
        String h1 = PasswordEncoder.hash("myPass123", salt1);
        String h2 = PasswordEncoder.hash("myPass123", salt2);
        assertNotEquals(h1, h2);
    }

    @Test
    @DisplayName("hash trả về chuỗi hex 64 ký tự (SHA-256 = 32 bytes = 64 hex)")
    void hash_returnsHex64Chars() {
        String hash = PasswordEncoder.hash("password", PasswordEncoder.generateSalt());
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"), "Hash phải là hex lowercase");
    }

    @Test
    @DisplayName("hash password null - throw")
    void hash_nullPassword_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordEncoder.hash(null, "someSalt"));
    }

    @Test
    @DisplayName("hash salt null - throw")
    void hash_nullSalt_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordEncoder.hash("password", null));
    }

    // ========== matches ==========

    @Test
    @DisplayName("matches với đúng password - trả về true")
    void matches_correctPassword_true() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("secret123", salt);
        assertTrue(PasswordEncoder.matches("secret123", salt, hash));
    }

    @Test
    @DisplayName("matches với sai password - trả về false")
    void matches_wrongPassword_false() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("secret123", salt);
        assertFalse(PasswordEncoder.matches("wrongPass", salt, hash));
    }

    @Test
    @DisplayName("matches với password null - trả về false (không throw)")
    void matches_nullPassword_false() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("secret123", salt);
        assertFalse(PasswordEncoder.matches(null, salt, hash));
    }

    @Test
    @DisplayName("matches với salt null - trả về false")
    void matches_nullSalt_false() {
        String hash = PasswordEncoder.hash("secret123", PasswordEncoder.generateSalt());
        assertFalse(PasswordEncoder.matches("secret123", null, hash));
    }

    @Test
    @DisplayName("matches với hash null - trả về false")
    void matches_nullHash_false() {
        String salt = PasswordEncoder.generateSalt();
        assertFalse(PasswordEncoder.matches("secret123", salt, null));
    }

    @Test
    @DisplayName("matches phân biệt hoa thường")
    void matches_caseSensitive() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("Password123", salt);
        assertFalse(PasswordEncoder.matches("password123", salt, hash));
    }
}
