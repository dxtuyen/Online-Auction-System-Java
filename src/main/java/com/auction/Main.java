package com.auction;

import com.auction.model.entity.*;
import com.auction.model.enums.*;
import com.auction.service.AuctionService;

import java.util.*;

public class Main {

    private static final Scanner sc = new Scanner(System.in);
    private static final AuctionService service = AuctionService.getInstance();
    private static User currentUser = null;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   🏛️  HỆ THỐNG ĐẤU GIÁ TRỰC TUYẾN      ║");
        System.out.println("╚══════════════════════════════════════════╝");

        service.seedData();
        System.out.println();

        while (true) {
            if (currentUser == null) {
                showAuthMenu();
            } else {
                showMainMenu();
            }
        }
    }

    // ==================== ĐĂNG NHẬP / ĐĂNG KÝ ====================

    private static void showAuthMenu() {
        System.out.println("\n════════ ĐĂNG NHẬP / ĐĂNG KÝ ════════");
        System.out.println("  1. Đăng nhập");
        System.out.println("  2. Đăng ký tài khoản mới");
        System.out.println("  0. Thoát");
        System.out.print("  Chọn: ");

        int choice = readInt();
        switch (choice) {
            case 1 -> handleLogin();
            case 2 -> handleRegister();
            case 0 -> { System.out.println("👋 Tạm biệt!"); System.exit(0); }
        }
    }

    private static void handleLogin() {
        System.out.println("\n── Đăng nhập ──");
        System.out.print("  Username: ");
        String username = sc.nextLine().trim();
        System.out.print("  Password: ");
        String password = sc.nextLine().trim();

        try {
            currentUser = service.login(username, password);
            System.out.printf("  ✅ Xin chào %s! (%s)%n",
                    currentUser.getUsername(), currentUser.getRole().getDisplayRole());
        } catch (RuntimeException e) {
            System.out.println("  ❌ " + e.getMessage());
        }
    }

    private static void handleRegister() {
        System.out.println("\n── Đăng ký ──");
        System.out.print("  Username: ");
        String username = sc.nextLine().trim();
        System.out.print("  Password: ");
        String password = sc.nextLine().trim();

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("  ❌ Không được để trống!");
            return;
        }

        System.out.println("  Vai trò: 1=Bidder  2=Seller");
        System.out.print("  Chọn: ");
        int roleChoice = readInt();

        Role role;
        double extra;
        switch (roleChoice) {
            case 1 -> { role = Role.BIDDER; System.out.print("  Số dư (VNĐ): "); extra = readDouble(); if (extra <= 0) extra = 10_000_000; }
            case 2 -> { role = Role.SELLER; extra = 0; }
            default -> { System.out.println("  ❌ Không hợp lệ!"); return; }
        }

        try {
            User user = service.register(username, password, role, extra);
            System.out.printf("  ✅ Thành công! ID: %d — Hãy đăng nhập.%n", user.getId());
        } catch (RuntimeException e) {
            System.out.println("  ❌ " + e.getMessage());
        }
    }

    // ==================== MENU CHÍNH ====================

    private static void showMainMenu() {
        System.out.printf("%n════════ [%s — %s] ════════%n",
                currentUser.getUsername(), currentUser.getRole().getDisplayRole());

        System.out.println("  1. Xem phiên đấu giá");
        System.out.println("  2. Xem chi tiết phiên");

        if (currentUser instanceof Bidder) {
            System.out.println("  3. 💰 Đặt giá");
            System.out.println("  4. 💳 Xem số dư");
        }
        if (currentUser instanceof Seller) {
            System.out.println("  5. 📦 Thêm sản phẩm");
            System.out.println("  6. 🏛️ Tạo phiên đấu giá");
            System.out.println("  7. 📋 Sản phẩm của tôi");
            System.out.println("  8. 🔒 Đóng phiên");
        }
        System.out.println("  9. 🚪 Đăng xuất");
        System.out.print("  Chọn: ");

        int choice = readInt();
        switch (choice) {
            case 1 -> listAuctions();
            case 2 -> viewAuctionDetail();
            case 3 -> { if (currentUser instanceof Bidder) handlePlaceBid(); }
            case 4 -> { if (currentUser instanceof Bidder b) System.out.printf("%n  💳 Số dư: %,.0f VNĐ%n", b.getBalance()); }
            case 5 -> { if (currentUser instanceof Seller) handleCreateItem(); }
            case 6 -> { if (currentUser instanceof Seller) handleCreateAuction(); }
            case 7 -> { if (currentUser instanceof Seller) listMyItems(); }
            case 8 -> { if (currentUser instanceof Seller) handleCloseAuction(); }
            case 9 -> { currentUser = null; System.out.println("  👋 Đã đăng xuất."); }
        }
    }

    // ==================== XEM PHIÊN ====================

    private static void listAuctions() {
        Collection<Auction> auctions = service.getAllAuctions();
        if (auctions.isEmpty()) { System.out.println("\n  📭 Chưa có phiên nào."); return; }

        System.out.println("\n  ┌─────┬──────────────────────┬──────────────┬───────┬───────────┐");
        System.out.println("  │ ID  │ Sản phẩm             │ Giá hiện tại │ Bids  │ Trạng thái│");
        System.out.println("  ├─────┼──────────────────────┼──────────────┼───────┼───────────┤");
        for (Auction a : auctions) {
            Item item = service.getItem(a.getItemId());
            String name = item != null ? item.getName() : "???";
            if (name.length() > 18) name = name.substring(0, 15) + "...";

            String icon = switch (a.getStatus()) {
                case RUNNING -> "🟢"; case FINISHED -> "🔴"; case PENDING -> "🟡";
                case PAID -> "✅"; case CANCELED -> "⛔";
            };
            System.out.printf("  │ %-3d │ %-20s │ %,12.0f │ %5d │%s %-8s│%n",
                    a.getId(), name, a.getCurrentPrice(), a.getTotalBids(), icon, a.getStatus());
        }
        System.out.println("  └─────┴──────────────────────┴──────────────┴───────┴───────────┘");
    }

    private static void viewAuctionDetail() {
        System.out.print("\n  Auction ID: ");
        int id = readInt();
        Auction a = service.getAuction(id);
        if (a == null) { System.out.println("  ❌ Không tìm thấy!"); return; }

        Item item = service.getItem(a.getItemId());
        User seller = service.getUser(a.getSellerId());

        System.out.println("\n  ══════ PHIÊN #" + a.getId() + " ══════");
        System.out.println("  📦 " + (item != null ? item.getName() : "N/A"));
        System.out.println("  📝 " + (item != null ? item.getDescription() : ""));
        System.out.println("  🏷️ " + (item != null ? item.getCategory().getDisplayName() : ""));
        System.out.println("  👤 Người bán: " + (seller != null ? seller.getUsername() : ""));
        System.out.printf("  💵 Giá khởi điểm: %,.0f VNĐ%n", a.getStartingPrice());
        System.out.printf("  💰 Giá hiện tại:  %,.0f VNĐ%n", a.getCurrentPrice());
        System.out.printf("  📈 Bước nhảy:     %,.0f VNĐ%n", a.getMinimumIncrement());
        System.out.printf("  🔢 Tổng bids:     %d%n", a.getTotalBids());

        if (a.getHighestBidderID() > 0) {
            User leader = service.getUser(a.getHighestBidderID());
            System.out.println("  👑 Dẫn đầu: " + (leader != null ? leader.getUsername() : "ID " + a.getHighestBidderID()));
        } else {
            System.out.println("  👑 Dẫn đầu: (chưa ai bid)");
        }

        System.out.println("  📊 " + a.getStatus().getDisplayStatus());
        System.out.println("  ⏰ " + a.getStartTime() + " → " + a.getEndTime());
        if (a.isActive()) System.out.printf("  ⏳ Còn %d giây%n", a.getRemainedSeconds());

        // Lịch sử bid
        List<BidTransaction> bids = service.getBidHistory(id);
        if (!bids.isEmpty()) {
            System.out.println("\n  📜 Lịch sử bid:");
            for (int i = bids.size() - 1; i >= 0; i--) {
                BidTransaction b = bids.get(i);
                User bidder = service.getUser(b.getBidderId());
                String bName = bidder != null ? bidder.getUsername() : "ID " + b.getBidderId();
                String marker = (i == bids.size() - 1) ? " ← Dẫn đầu" : "";
                System.out.printf("     %s | %-8s | %,12.0f VNĐ%s%n",
                        b.getTimestamp(), bName, b.getBidAmount(), marker);
            }
        }
    }

    // ==================== BIDDER: ĐẶT GIÁ ====================

    private static void handlePlaceBid() {
        System.out.print("\n  Auction ID: ");
        int auctionId = readInt();

        Auction a = service.getAuction(auctionId);
        if (a == null) { System.out.println("  ❌ Phiên không tồn tại!"); return; }

        Item item = service.getItem(a.getItemId());
        System.out.printf("  📦 %s | Giá: %,.0f | Bước nhảy: %,.0f%n",
                item != null ? item.getName() : "???", a.getCurrentPrice(), a.getMinimumIncrement());
        System.out.printf("  → Tối thiểu: %,.0f VNĐ%n", a.getCurrentPrice() + a.getMinimumIncrement());
        System.out.print("  Nhập giá: ");
        double amount = readDouble();

        try {
            BidTransaction bid = service.placeBid(auctionId, currentUser.getId(), amount);
            System.out.printf("  ✅ Đặt giá %,.0f VNĐ thành công!%n", bid.getBidAmount());
        } catch (RuntimeException e) {
            System.out.println("  ❌ " + e.getMessage());
        }
    }

    // ==================== SELLER: SẢN PHẨM ====================

    private static void handleCreateItem() {
        System.out.println("\n── Thêm sản phẩm ──");

        ItemCategory[] cats = ItemCategory.values();
        for (int i = 0; i < cats.length; i++)
            System.out.printf("  %d. %s%n", i + 1, cats[i].getDisplayName());
        System.out.print("  Chọn loại: ");
        int catIdx = readInt();
        if (catIdx < 1 || catIdx > cats.length) { System.out.println("  ❌ Không hợp lệ!"); return; }
        ItemCategory category = cats[catIdx - 1];

        System.out.print("  Tên: ");
        String name = sc.nextLine().trim();
        System.out.print("  Mô tả: ");
        String desc = sc.nextLine().trim();
        System.out.print("  Giá khởi điểm (VNĐ): ");
        double price = readDouble();

        System.out.print("  Tình trạng (1=Mới, 2=Cũ): ");
        ItemCondition cond = (readInt() == 2) ? ItemCondition.USED : ItemCondition.NEW;

        Map<String, String> attrs = new HashMap<>();
        switch (category) {
            case ELECTRONICS -> {
                System.out.print("  Hãng: "); attrs.put("brand", sc.nextLine().trim());
                System.out.print("  Dòng SP: "); attrs.put("model", sc.nextLine().trim());
                System.out.print("  BH (tháng): "); attrs.put("warrantyMonths", String.valueOf(readInt()));
            }
            case ART -> {
                System.out.print("  Tác giả: "); attrs.put("artist", sc.nextLine().trim());
                System.out.print("  Năm: "); attrs.put("year", String.valueOf(readInt()));
            }
            case VEHICLE -> {
                System.out.print("  Hãng: "); attrs.put("brand", sc.nextLine().trim());
                System.out.print("  Dòng xe: "); attrs.put("model", sc.nextLine().trim());
                System.out.print("  Năm SX: "); attrs.put("manufactureYear", String.valueOf(readInt()));
                System.out.print("  Km đã đi: "); attrs.put("mileage", String.valueOf(readInt()));
                System.out.print("  Màu: "); attrs.put("color", sc.nextLine().trim());
                System.out.print("  Nhiên liệu: "); attrs.put("fuelType", sc.nextLine().trim());
                System.out.print("  Hộp số: "); attrs.put("transmission", sc.nextLine().trim());
            }
            default -> {}
        }

        try {
            Item item = service.createItem(category, name, desc, price, currentUser.getId(), cond, attrs);
            System.out.printf("  ✅ Đã thêm: [%d] %s — %,.0f VNĐ%n",
                    item.getId(), item.getName(), item.getStartingPrice());
        } catch (RuntimeException e) {
            System.out.println("  ❌ " + e.getMessage());
        }
    }

    // ==================== SELLER: PHIÊN ĐẤU GIÁ ====================

    private static void handleCreateAuction() {
        System.out.println("\n── Tạo phiên đấu giá ──");
        listMyItems();
        System.out.print("  Item ID: ");
        int itemId = readInt();
        System.out.print("  Thời gian (phút): ");
        int duration = readInt(); if (duration <= 0) duration = 30;
        System.out.print("  Bước nhảy (VNĐ): ");
        double incr = readDouble(); if (incr <= 0) incr = 100_000;

        try {
            Auction a = service.createAuction(itemId, currentUser.getId(), duration, incr);
            System.out.printf("  ✅ Phiên #%d — kết thúc lúc %s%n", a.getId(), a.getEndTime());
        } catch (RuntimeException e) {
            System.out.println("  ❌ " + e.getMessage());
        }
    }

    private static void listMyItems() {
        List<Item> my = service.getItemsBySeller(currentUser.getId());
        if (my.isEmpty()) { System.out.println("  📭 Chưa có sản phẩm."); return; }
        System.out.println("  📦 Sản phẩm của bạn:");
        for (Item i : my)
            System.out.printf("     [%d] %s — %,.0f VNĐ (%s)%n",
                    i.getId(), i.getName(), i.getStartingPrice(), i.getCategory().getDisplayName());
    }

    private static void handleCloseAuction() {
        System.out.print("\n  Auction ID cần đóng: ");
        int id = readInt();
        try {
            service.closeAuction(id);
            Auction a = service.getAuction(id);
            System.out.println("  ✅ Đã đóng phiên #" + id);
            if (a.getHighestBidderID() > 0) {
                User w = service.getUser(a.getHighestBidderID());
                System.out.printf("  🏆 Thắng: %s | Giá: %,.0f VNĐ%n",
                        w != null ? w.getUsername() : "?", a.getCurrentPrice());
            } else {
                System.out.println("  📭 Không ai bid.");
            }
        } catch (RuntimeException e) {
            System.out.println("  ❌ " + e.getMessage());
        }
    }

    // ==================== HELPERS ====================

    private static int readInt() {
        try { return Integer.parseInt(sc.nextLine().trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private static double readDouble() {
        try { return Double.parseDouble(sc.nextLine().trim().replace(",", "")); }
        catch (NumberFormatException e) { return -1; }
    }
}