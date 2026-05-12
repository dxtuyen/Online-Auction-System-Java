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
 * <p>Gọi {@link #run()} một lần ở {@code Main} TRƯỚC khi server bắt đầu accept connection.
 * Tự skip nếu đã có user trong hệ thống → idempotent, gọi nhiều lần vô hại.</p>
 *
 * <p>Bật/tắt qua biến môi trường {@code SEED_ENABLED=true}. Mặc định tắt
 * để tránh dữ liệu giả lọt vào production.</p>
 */
public final class DataSeeder {

    private DataSeeder() { /* static-only */ }

    public static void run() {
        if (!"true".equalsIgnoreCase(System.getenv("SEED_ENABLED"))) {
            return;
        }
        // Check DB thay vì cache: nếu DB đã có user (vd từ lần seed trước),
        // skip để tránh insert trùng và vi phạm unique constraint username/email.
        if (UserManager.getInstance().countInDb() > 0) {
            System.out.println("[Seed] Bỏ qua — DB đã có dữ liệu");
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

    /**
     * Tạo vài user mẫu. Cấp balance để bidder đủ tiền đặt giá khi test luồng đấu giá.
     */
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

        // Nạp balance để bob/carol có thể bid trong demo - phải save() để flush DB
        bob.setBalance(new BigDecimal("10000000"));
        um.save(bob);
        carol.setBalance(new BigDecimal("20000000"));
        um.save(carol);

        map.put("admin", admin);
        map.put("alice", alice);
        map.put("bob", bob);
        map.put("carol", carol);
        return map;
    }

    // ============== ITEMS ==============

    /**
     * Tạo vài item cho seller alice. {@code attrs} để rỗng — nếu category khác
     * yêu cầu attribute đặc thù (vd ELECTRONICS cần "brand"), điền vào map dưới đây.
     */
    private static Map<String, Item> seedItems(Map<String, User> users) {
        ItemManager im = ItemManager.getInstance();
        Map<String, Item> map = new HashMap<>();

        Map<String, Object> emptyAttrs = new HashMap<>();
        List<String> noImages = List.of();

        Item iphone = im.createItem(ItemCategory.OTHER,
                "iPhone 15 Pro Max",
                "Máy chính hãng, fullbox, bảo hành 12 tháng",
                users.get("alice").getId(),
                new BigDecimal("25000000"),
                noImages,
                ItemCondition.NEW,
                emptyAttrs);

        Item painting = im.createItem(ItemCategory.OTHER,
                "Tranh sơn dầu Hà Nội phố",
                "Tranh chép, kích thước 80x120cm",
                users.get("alice").getId(),
                new BigDecimal("3000000"),
                noImages,
                ItemCondition.USED,
                emptyAttrs);

        map.put("iphone", iphone);
        map.put("painting", painting);
        return map;
    }

    // ============== AUCTIONS ==============

    /**
     * Tạo 2 phiên: 1 đang chạy (RUNNING ngay khi scheduler tick) và 1 sắp diễn ra (PENDING).
     */
    private static void seedAuctions(Map<String, User> users, Map<String, Item> items) {
        AuctionManager am = AuctionManager.getInstance();
        LocalDateTime now = LocalDateTime.now();

        // Phiên đang chạy
        am.createAuction(
                items.get("iphone").getId(),
                users.get("alice").getId(),
                now.minusMinutes(1),
                now.plusMinutes(30),
                items.get("iphone").getStartingPrice(),
                new BigDecimal("100000"));

        // Phiên chuẩn bị bắt đầu sau 5 phút
        am.createAuction(
                items.get("painting").getId(),
                users.get("alice").getId(),
                now.plusMinutes(5),
                now.plusMinutes(60),
                items.get("painting").getStartingPrice(),
                new BigDecimal("50000"));
    }
}
