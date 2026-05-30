package com.auction.bootstrap;

import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.model.enums.Role;
import com.auction.service.AuctionManager;
import com.auction.service.ItemManager;
import com.auction.service.UserManager;
import com.auction.util.AppLogger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class DataSeeder {

    private static final Logger log = AppLogger.get(DataSeeder.class);

    private DataSeeder() {  }

    public static void run() {

        if (UserManager.getInstance().count() > 0) {
            log.info("Bỏ qua seed — hệ thống đã có dữ liệu");
            return;
        }

        log.info("Bắt đầu seed dữ liệu mẫu...");
        try {
            Map<String, User> users = seedUsers();
            Map<String, Item> items = seedItems(users);
            seedAuctions(users, items);
            log.info(() -> String.format("Seed xong: %d user, %d item, %d auction",
                    UserManager.getInstance().count(),
                    ItemManager.getInstance().count(),
                    AuctionManager.getInstance().count()));
        } catch (RuntimeException e) {
            log.severe("Seed thất bại: " + e.getMessage());
        }
    }

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

        bob.setBalance(new BigDecimal("50000000"));
        carol.setBalance(new BigDecimal("80000000"));
        um.save(bob);
        um.save(carol);

        map.put("admin", admin);
        map.put("alice", alice);
        map.put("bob", bob);
        map.put("carol", carol);
        return map;
    }

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

    private static void seedAuctions(Map<String, User> users, Map<String, Item> items) {
        AuctionManager am = AuctionManager.getInstance();
        LocalDateTime now = LocalDateTime.now();

        am.createAuction(
                items.get("phone").getId(),
                users.get("alice").getId(),
                now.minusMinutes(1),
                now.plusMinutes(30),
                items.get("phone").getStartingPrice(),
                new BigDecimal("100000"));

        am.createAuction(
                items.get("painting").getId(),
                users.get("alice").getId(),
                now.minusSeconds(10),
                now.plusMinutes(20),
                items.get("painting").getStartingPrice(),
                new BigDecimal("50000"));

        am.createAuction(
                items.get("car").getId(),
                users.get("alice").getId(),
                now.plusMinutes(5),
                now.plusMinutes(60),
                items.get("car").getStartingPrice(),
                new BigDecimal("1000000"));
    }
}
