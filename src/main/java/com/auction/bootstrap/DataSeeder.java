package com.auction.bootstrap;

import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.model.enums.Role;
import com.auction.service.AuctionManager;
import com.auction.service.ItemManager;
import com.auction.service.UserManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seed dữ liệu mẫu cho môi trường dev/demo.
 *
 * <p>Gọi {@link #run()} một lần ở {@code ServerMain} TRƯỚC khi accept connection.
 * Tự skip nếu đã có user → idempotent, an toàn khi gọi nhiều lần.</p>
 *
 * <p>Item được tạo bằng cả 3 subclass {@code Electronics}/{@code Art}/{@code Vehicle}
 * (không chỉ {@code OtherItem}) để demo inheritance + factory pattern — đây là tiêu chí
 * chấm điểm "phân cấp lớp rõ ràng" của đề BTL.</p>
 */
public final class DataSeeder {

    private DataSeeder() { /* static-only */ }

    public static void run() {
        // Idempotent: đã có user nghĩa là DB đã được seed/persist từ trước → bỏ qua.
        if (UserManager.getInstance().count() > 0) {
            System.out.println("[Seed] Bỏ qua — hệ thống đã có dữ liệu");
            return;
        }

        System.out.println("[Seed] Bắt đầu seed dữ liệu mẫu...");
        Map<String, User> users = seedUsers();
        Map<String, Item> items = seedItems(users);
        seedAuctions(users, items);
        System.out.println("[Seed] Xong: "
                + UserManager.getInstance().count() + " user, "
                + ItemManager.getInstance().count() + " item, "
                + AuctionManager.getInstance().count() + " auction.");
    }

    // ============== USERS ==============

    private static Map<String, User> seedUsers() {
        UserManager um = UserManager.getInstance();
        Map<String, User> map = new HashMap<>();

        User admin = um.register("admin", "admin123", "admin@auction.local",
                "Quản trị viên", Role.ADMIN);
        User alice = um.register("alice", "alice123", "alice@auction.local",
                "Alice (Seller)", Role.NORMAL);
        User bob = um.register("bob", "bob123", "bob@auction.local",
                "Bob (Bidder)", Role.NORMAL);
        User carol = um.register("carol", "carol123", "carol@auction.local",
                "Carol (Bidder)", Role.NORMAL);

        // Nạp balance để bob/carol có thể bid trong demo
        bob.setBalance(new BigDecimal("50000000"));
        carol.setBalance(new BigDecimal("80000000"));

        map.put("admin", admin);
        map.put("alice", alice);
        map.put("bob", bob);
        map.put("carol", carol);
        return map;
    }

    // ============== ITEMS ==============

    /**
     * Tạo item của 3 subclass khác nhau để demo polymorphism (Item → Electronics/Art/Vehicle).
     * Tên attribute phải khớp với {@link com.auction.model.factory.ItemFactory}:
     * <ul>
     *   <li>ELECTRONICS: brand, model, warrantyMonths</li>
     *   <li>ART: artist, yearCreated, medium</li>
     *   <li>VEHICLE: make, model, year, mileageKm</li>
     * </ul>
     */
    private static Map<String, Item> seedItems(Map<String, User> users) {
        ItemManager im = ItemManager.getInstance();
        Map<String, Item> map = new HashMap<>();
        List<String> noImages = List.of();

        Map<String, Object> phoneAttrs = new HashMap<>();
        phoneAttrs.put("brand", "Apple");
        phoneAttrs.put("model", "iPhone 15 Pro Max");
        phoneAttrs.put("warrantyMonths", 12);
        Item phone = im.createItem(ItemCategory.ELECTRONICS,
                "iPhone 15 Pro Max 256GB",
                "Máy chính hãng, fullbox, bảo hành 12 tháng",
                users.get("alice").getId(),
                new BigDecimal("25000000"),
                noImages, ItemCondition.NEW, phoneAttrs);

        Map<String, Object> artAttrs = new HashMap<>();
        artAttrs.put("artist", "Bùi Xuân Phái");
        artAttrs.put("yearCreated", 1985);
        artAttrs.put("medium", "Sơn dầu trên canvas");
        Item painting = im.createItem(ItemCategory.ART,
                "Tranh sơn dầu Hà Nội phố",
                "Tranh chép, kích thước 80x120cm",
                users.get("alice").getId(),
                new BigDecimal("3000000"),
                noImages, ItemCondition.USED, artAttrs);

        Map<String, Object> carAttrs = new HashMap<>();
        carAttrs.put("make", "Toyota");
        carAttrs.put("model", "Camry 2.5Q");
        carAttrs.put("year", 2022);
        carAttrs.put("mileageKm", 15000);
        Item car = im.createItem(ItemCategory.VEHICLE,
                "Toyota Camry 2.5Q 2022",
                "Xe đi 15,000 km, một đời chủ, full đồ",
                users.get("alice").getId(),
                new BigDecimal("950000000"),
                noImages, ItemCondition.USED, carAttrs);

        map.put("phone", phone);
        map.put("painting", painting);
        map.put("car", car);
        return map;
    }

    // ============== AUCTIONS ==============

    private static void seedAuctions(Map<String, User> users, Map<String, Item> items) {
        AuctionManager am = AuctionManager.getInstance();
        LocalDateTime now = LocalDateTime.now();

        // Phiên đang chạy — bid ngay được
        am.createAuction(
                items.get("phone").getId(),
                users.get("alice").getId(),
                now.minusMinutes(1),
                now.plusMinutes(30),
                items.get("phone").getStartingPrice(),
                new BigDecimal("100000"));

        // Phiên tranh — đang chạy
        am.createAuction(
                items.get("painting").getId(),
                users.get("alice").getId(),
                now.minusSeconds(10),
                now.plusMinutes(20),
                items.get("painting").getStartingPrice(),
                new BigDecimal("50000"));

        // Phiên xe — chuẩn bị bắt đầu sau 5 phút
        am.createAuction(
                items.get("car").getId(),
                users.get("alice").getId(),
                now.plusMinutes(5),
                now.plusMinutes(60),
                items.get("car").getStartingPrice(),
                new BigDecimal("1000000"));
    }
}
