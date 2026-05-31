package com.auction.client.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test các helper thuần của SellerDashboardController qua reflection. */
class SellerDashboardControllerTest {

    private static Locale original;
    private final SellerDashboardController controller = new SellerDashboardController();

    @BeforeAll
    static void forceLocale() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    @DisplayName("parseMoney: bỏ dấu chấm/phẩy ra double, lỗi trả -1")
    void parseMoney_cases() {
        assertEquals(1_000_000.0, parseMoney("1,000,000"));
        assertEquals(1_000_000.0, parseMoney("1.000.000"));
        assertEquals(500.0, parseMoney(" 500 "));
        assertEquals(-1.0, parseMoney("abc"));
        assertEquals(-1.0, parseMoney(""));
    }

    @Test
    @DisplayName("money: số ra dạng có nhóm + VNĐ, non-number ra 0 VNĐ")
    void money_cases() {
        assertEquals("1,000,000 VNĐ", money(1_000_000.0));
        assertEquals("0 VNĐ", money(null));
        assertEquals("0 VNĐ", money("x"));
    }

    @Test
    @DisplayName("s: null→\"\", Number→intValue, khác→toString")
    void s_cases() {
        Map<String, Object> m = new HashMap<>();
        m.put("price", 1500.0);
        m.put("name", "Laptop");
        assertEquals("", s(m, "missing"));
        assertEquals("1500", s(m, "price"));
        assertEquals("Laptop", s(m, "name"));
    }

    @Test
    @DisplayName("formatProfileDetails: gồm tài khoản, vai trò, số dư, doanh thu")
    void formatProfileDetails_full() {
        Map<String, Object> data = new HashMap<>();
        data.put("username", "seller1");
        data.put("displayRole", "Người dùng");
        data.put("displayStatus", "Hoạt động");
        data.put("balance", 200000.0);
        data.put("revenue", 75000.0);

        String out = formatProfileDetails(data);
        assertTrue(out.contains("seller1"));
        assertTrue(out.contains("Số dư"));
        assertTrue(out.contains("Doanh thu"));
    }

    // ===== reflection helpers =====

    private double parseMoney(String s) {
        return (double) invoke("parseMoney", new Class<?>[]{String.class}, s);
    }

    private String money(Object v) {
        return (String) invoke("money", new Class<?>[]{Object.class}, v);
    }

    private String s(Map<String, Object> m, String k) {
        return (String) invoke("s", new Class<?>[]{Map.class, String.class}, m, k);
    }

    private String formatProfileDetails(Map<String, Object> data) {
        return (String) invoke("formatProfileDetails", new Class<?>[]{Map.class}, data);
    }

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = SellerDashboardController.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(controller, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
