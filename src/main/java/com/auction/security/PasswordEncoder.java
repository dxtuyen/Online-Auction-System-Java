package com.auction.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Hash mật khẩu bằng SHA-256 + salt.
 *
 * Tại sao có salt?
 *  - Không có salt: 2 user cùng đặt password "123456" → cùng hash →
 *    crack 1 lần là lộ tất.
 *  - Có salt random per-user: dù trùng password thì hash vẫn khác nhau →
 *    rainbow table vô dụng.
 *
 * GHI CHÚ AN TOÀN:
 *  - Sản phẩm thật nên dùng BCrypt/Argon2 (chậm có chủ đích, kháng GPU).
 *  - SHA-256 + salt ở đây là demo, đủ cho project trường.
 */
public final class PasswordEncoder {

    /** SecureRandom share toàn class - tự seed bởi OS, an toàn cho crypto */
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordEncoder() { /* utility - không cho new */ }

    /** Sinh salt ngẫu nhiên 16 bytes → mã hex 32 ký tự */
    public static String generateSalt() {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        return HexFormat.of().formatHex(saltBytes);
    }

    /** Hash password với salt cụ thể */
    public static String hash(String plainPassword, String salt) {
        if (plainPassword == null || salt == null) {
            throw new IllegalArgumentException("password/salt không thể null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = md.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 luôn có trong JDK chuẩn → đây là lỗi không bao giờ xảy ra
            throw new IllegalStateException("SHA-256 không khả dụng trong JVM", e);
        }
    }

    /**
     * Kiểm tra password nhập có khớp với hash đã lưu không.
     *
     * Dùng MessageDigest.isEqual để so sánh constant-time → chống timing attack
     * (so 2 chuỗi bằng equals sẽ exit sớm khi gặp ký tự lệch đầu tiên,
     *  attacker có thể đoán từng ký tự bằng cách đo thời gian).
     */
    public static boolean matches(String plainPassword, String salt, String expectedHash) {
        if (plainPassword == null || salt == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(plainPassword, salt).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
