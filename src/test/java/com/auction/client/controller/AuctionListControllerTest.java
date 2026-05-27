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

/** Test các helper thuần của AuctionListController qua reflection. */
class AuctionListControllerTest {

    private static Locale original;
    private final AuctionListController controller = new AuctionListController();

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
    @DisplayName("str: null→\"\", Number→intValue, khác→toString")
    void str_cases() {
        Map<String, Object> m = new HashMap<>();
        m.put("bids", 7.0);
        m.put("status", "OPEN");
        assertEquals("", str(m, "missing"));
        assertEquals("7", str(m, "bids"));
        assertEquals("OPEN", str(m, "status"));
    }

    @Test
    @DisplayName("formatMoney: số ra dạng có nhóm + VNĐ, non-number ra 0 VNĐ")
    void formatMoney_cases() {
        assertEquals("2,500,000 VNĐ", formatMoney(2_500_000.0));
        assertEquals("0 VNĐ", formatMoney(null));
    }

    @Test
    @DisplayName("formatProfileSummary: non-admin kèm số dư, admin chỉ tên + vai trò")
    void formatProfileSummary_byRole() {
        Map<String, Object> normal = new HashMap<>();
        normal.put("username", "bob");
        normal.put("displayRole", "Người dùng");
        normal.put("role", "NORMAL");
        normal.put("balance", 30000.0);
        String out = formatProfileSummary(normal);
        assertTrue(out.contains("bob"));
        assertTrue(out.contains("Số dư"));

        Map<String, Object> admin = new HashMap<>(normal);
        admin.put("role", "ADMIN");
        String adminOut = formatProfileSummary(admin);
        assertTrue(adminOut.contains("bob"));
        assertTrue(!adminOut.contains("Số dư"));
    }

    @Test
    @DisplayName("formatProfileDetails: admin không hiển thị số dư/doanh thu")
    void formatProfileDetails_byRole() {
        Map<String, Object> normal = new HashMap<>();
        normal.put("username", "carol");
        normal.put("displayRole", "Người dùng");
        normal.put("displayStatus", "Hoạt động");
        normal.put("role", "NORMAL");
        normal.put("balance", 10000.0);
        normal.put("revenue", 0.0);
        assertTrue(formatProfileDetails(normal).contains("Số dư"));

        Map<String, Object> admin = new HashMap<>(normal);
        admin.put("role", "ADMIN");
        assertTrue(!formatProfileDetails(admin).contains("Doanh thu"));
    }

    // ===== reflection helpers =====

    private String str(Map<String, Object> m, String k) {
        return (String) invoke("str", new Class<?>[]{Map.class, String.class}, m, k);
    }

    private String formatMoney(Object v) {
        return (String) invoke("formatMoney", new Class<?>[]{Object.class}, v);
    }

    private String formatProfileSummary(Map<String, Object> data) {
        return (String) invoke("formatProfileSummary", new Class<?>[]{Map.class}, data);
    }

    private String formatProfileDetails(Map<String, Object> data) {
        return (String) invoke("formatProfileDetails", new Class<?>[]{Map.class}, data);
    }

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = AuctionListController.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(controller, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
