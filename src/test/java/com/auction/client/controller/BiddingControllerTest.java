package com.auction.client.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test các helper thuần của BiddingController qua reflection.
 *
 * <p>Controller là JavaFX nhưng các hàm parse/format/str/num không đụng tới @FXML node,
 * và instance khởi tạo được không cần JavaFX toolkit (không có field initializer nào tạo
 * JavaFX object). Vì thế test được mà không cần TestFX / màn hình ảo.</p>
 */
class BiddingControllerTest {

    private static Locale original;
    private final BiddingController controller = new BiddingController();

    @BeforeAll
    static void forceLocale() {
        // formatMoney dùng String.format("%,.0f") — separator phụ thuộc locale.
        // Ép US để assertion deterministic, khôi phục ở @AfterAll.
        original = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    @DisplayName("parseMoney: bỏ dấu chấm/phẩy/space, ra BigDecimal")
    void parseMoney_grouped() {
        assertEquals(new BigDecimal("1000000"), parseMoney("1.000.000"));
        assertEquals(new BigDecimal("1000000"), parseMoney("1,000,000"));
        assertEquals(new BigDecimal("1000000"), parseMoney("1000000"));
        assertEquals(new BigDecimal("500"), parseMoney(" 500 "));
    }

    @Test
    @DisplayName("parseMoney: input rỗng/null/không phải số trả null")
    void parseMoney_invalid() {
        assertNull(parseMoney(null));
        assertNull(parseMoney(""));
        assertNull(parseMoney("   "));
        assertNull(parseMoney("abc"));
    }

    @Test
    @DisplayName("formatMoney: số ra dạng có nhóm + VNĐ, non-number ra 0 VNĐ")
    void formatMoney_cases() {
        assertEquals("1,000,000 VNĐ", formatMoney(1_000_000.0));
        assertEquals("0 VNĐ", formatMoney(0.0));
        assertEquals("0 VNĐ", formatMoney(null));
        assertEquals("0 VNĐ", formatMoney("not-a-number"));
    }

    @Test
    @DisplayName("str: null→\"\", Number→intValue, khác→toString")
    void str_cases() {
        Map<String, Object> m = new HashMap<>();
        m.put("dbl", 1234.0);
        m.put("int", 5);
        m.put("txt", "hello");
        assertEquals("", str(m, "missing"));
        assertEquals("1234", str(m, "dbl"));
        assertEquals("5", str(m, "int"));
        assertEquals("hello", str(m, "txt"));
    }

    @Test
    @DisplayName("num: Number→doubleValue, khác/null→0")
    void num_cases() {
        assertEquals(1.5, num(1.5));
        assertEquals(3.0, num(3));
        assertEquals(0.0, num(null));
        assertEquals(0.0, num("xyz"));
    }

    @Test
    @DisplayName("shortTime: ISO datetime ra HH:mm:ss, lỗi parse trả nguyên chuỗi")
    void shortTime_cases() {
        assertEquals("14:30:45", shortTime("2026-05-27T14:30:45"));
        assertEquals("garbage", shortTime("garbage"));
    }

    @Test
    @DisplayName("formatProfileDetails: non-admin có số dư/doanh thu, admin không")
    void formatProfileDetails_byRole() {
        Map<String, Object> normal = new HashMap<>();
        normal.put("username", "alice");
        normal.put("displayRole", "Người dùng");
        normal.put("displayStatus", "Hoạt động");
        normal.put("role", "NORMAL");
        normal.put("balance", 50000.0);
        normal.put("revenue", 0.0);
        String out = formatProfileDetails(normal);
        assertTrue(out.contains("alice"));
        assertTrue(out.contains("Số dư"));
        assertTrue(out.contains("Doanh thu"));

        Map<String, Object> admin = new HashMap<>(normal);
        admin.put("role", "ADMIN");
        String adminOut = formatProfileDetails(admin);
        assertTrue(adminOut.contains("Vai trò"));
        assertTrue(!adminOut.contains("Số dư"));
    }

    // ===== reflection helpers =====

    private BigDecimal parseMoney(String s) {
        return (BigDecimal) invoke("parseMoney", new Class<?>[]{String.class}, s);
    }

    private String formatMoney(Object v) {
        return (String) invoke("formatMoney", new Class<?>[]{Object.class}, v);
    }

    private String str(Map<String, Object> m, String k) {
        return (String) invoke("str", new Class<?>[]{Map.class, String.class}, m, k);
    }

    private double num(Object v) {
        return (double) invoke("num", new Class<?>[]{Object.class}, v);
    }

    private String shortTime(String t) {
        return (String) invoke("shortTime", new Class<?>[]{String.class}, t);
    }

    private String formatProfileDetails(Map<String, Object> data) {
        return (String) invoke("formatProfileDetails", new Class<?>[]{Map.class}, data);
    }

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = BiddingController.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(controller, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
