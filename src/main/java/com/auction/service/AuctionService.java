package com.auction.service;

import com.auction.model.entity.*;
import com.auction.model.enums.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service trung tâm — quản lý user, item, auction, bid.
 * Singleton: chỉ có 1 instance trong toàn hệ thống.
 */
public class AuctionService {

    //SINGLETON
    private static AuctionService instance;

    // Nếu nhiểu luồng gọi vẫn an toàn vì dùng synchornized rồi
    public static synchronized AuctionService getInstance() {
        if (instance == null) instance = new AuctionService();
        return instance;
    }

    // Nơi lưu trữ dữ liệu
    private final Map<Integer, User> users = new HashMap<>(); // Lưu người người dùng vào map
    private final Map<Integer, Item> items = new HashMap<>(); // Lưu vật phẩm
    private final Map<Integer, Auction> auctions = new HashMap<>(); // Lưu phiên đấu giá theo id.
    private final Map<Integer, List<BidTransaction>> bidHistory = new HashMap<>();

    // ====== AUTO ID ======
    private final AtomicInteger userIdCounter = new AtomicInteger(0);
    private final AtomicInteger itemIdCounter = new AtomicInteger(0);
    private final AtomicInteger auctionIdCounter = new AtomicInteger(0);
    private final AtomicInteger bidIdCounter = new AtomicInteger(0);

    private AuctionService() {}

    // ======================== USER ========================

    public User register(String username, String password, Role role, double extraValue) {
        // Check trùng username
        for (User u : users.values()) {
            if (u.getUsername().equals(username)) {
                throw new RuntimeException("Username '" + username + "' đã được sử dụng!");
            }
        }

        int id = userIdCounter.incrementAndGet();
        User user = switch (role) {
            case BIDDER -> new Bidder(id, username, password, extraValue);
            case SELLER -> new Seller(id, username, password, extraValue);
            case ADMIN  -> new Admin(id, username, password, extraValue);
        };

        users.put(id, user);
        return user;
    }

    public User login(String username, String password) {
        for (User u : users.values()) {
            if (u.getUsername().equals(username)) {
                if (u.getPassword().equals(password)) {
                    if (u.getUserStatus() == UserStatus.BANNED) {
                        throw new RuntimeException("Tài khoản đã bị khóa!");
                    }
                    return u;
                } else {
                    throw new RuntimeException("Sai mật khẩu!");
                }
            }
        }
        throw new RuntimeException("Không tìm thấy user: " + username);
    }

    public User getUser(int id) { return users.get(id); }

    // ======================== ITEM ========================

    public Item createItem(ItemCategory category, String name, String description,
                           double startingPrice, int sellerId, ItemCondition condition,
                           Map<String, String> specificAttributes) {
        User seller = users.get(sellerId);
        if (seller == null || seller.getRole() != Role.SELLER) {
            throw new RuntimeException("Seller không tồn tại!");
        }

        int id = itemIdCounter.incrementAndGet();
        Item item = ItemFactory.createItem(category, name, description, startingPrice,
                sellerId, new ArrayList<>(), condition, specificAttributes);
        item.setId(id);
        items.put(id, item);
        return item;
    }

    public Item getItem(int id) { return items.get(id); }

    public List<Item> getItemsBySeller(int sellerId) {
        List<Item> result = new ArrayList<>();
        for (Item item : items.values()) {
            if (item.getSellerId() == sellerId) result.add(item);
        }
        return result;
    }

    // ======================== AUCTION ========================

    public Auction createAuction(int itemId, int sellerId, int durationMinutes,
                                 double minimumIncrement) {
        Item item = items.get(itemId);
        if (item == null) throw new RuntimeException("Sản phẩm không tồn tại: " + itemId);
        if (item.getSellerId() != sellerId) throw new RuntimeException("Bạn không sở hữu sản phẩm này!");

        int id = auctionIdCounter.incrementAndGet();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusMinutes(durationMinutes);

        Auction auction = new Auction(itemId, sellerId, start, end,
                item.getStartingPrice(), minimumIncrement);
        auction.setId(id);
        auction.transitionTo(AuctionStatus.RUNNING);

        auctions.put(id, auction);
        bidHistory.put(id, new ArrayList<>());
        return auction;
    }

    public void closeAuction(int auctionId) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) throw new RuntimeException("Phiên không tồn tại: " + auctionId);
        auction.transitionTo(AuctionStatus.FINISHED);
    }

    public Auction getAuction(int id) { return auctions.get(id); }
    public Collection<Auction> getAllAuctions() { return auctions.values(); }

    // ======================== BID ========================

    public BidTransaction placeBid(int auctionId, int bidderId, double bidAmount) {
        Auction auction = auctions.get(auctionId);
        if (auction == null)
            throw new RuntimeException("Phiên không tồn tại: " + auctionId);

        if (auction.getStatus() != AuctionStatus.RUNNING)
            throw new RuntimeException("Phiên " + auctionId + " không đang đấu giá!");

        if (LocalDateTime.now().isAfter(auction.getEndTime())) {
            auction.transitionTo(AuctionStatus.FINISHED);
            throw new RuntimeException("Phiên " + auctionId + " đã hết thời gian!");
        }

        double minRequired = auction.getCurrentPrice() + auction.getMinimumIncrement();
        if (bidAmount < minRequired) {
            throw new RuntimeException(String.format(
                    "Giá %,.0f không hợp lệ! Tối thiểu: %,.0f (hiện tại %,.0f + bước nhảy %,.0f)",
                    bidAmount, minRequired, auction.getCurrentPrice(), auction.getMinimumIncrement()));
        }

        User bidder = users.get(bidderId);
        if (bidder == null || bidder.getRole() != Role.BIDDER)
            throw new RuntimeException("Bidder không hợp lệ!");

        int bidId = bidIdCounter.incrementAndGet();
        BidTransaction bid = new BidTransaction(auctionId, bidderId, bidAmount);
        bid.setId(bidId);

        auction.setCurrentPrice(bidAmount);
        auction.setHighestBidderId(bidderId);
        auction.incrementTotalBids();

        bidHistory.computeIfAbsent(auctionId, k -> new ArrayList<>()).add(bid);
        return bid;
    }

    public List<BidTransaction> getBidHistory(int auctionId) {
        return bidHistory.getOrDefault(auctionId, new ArrayList<>());
    }

    // ======================== SEED DATA ========================

    public void seedData() {
        register("alice", "123", Role.BIDDER, 10_000_000);
        register("bob", "123", Role.BIDDER, 15_000_000);
        register("seller1", "123", Role.SELLER, 0);
        register("admin", "123", Role.ADMIN, 0);

        createItem(ItemCategory.ELECTRONICS, "MacBook Pro M3", "Mới nguyên seal, BH 12 tháng",
                35_000_000, 3, ItemCondition.NEW,
                Map.of("brand", "Apple", "model", "MacBook Pro", "warrantyMonths", "12"));

        createItem(ItemCategory.ART, "Tranh sơn dầu Phong cảnh", "Tranh gốc, có chứng nhận",
                5_000_000, 3, ItemCondition.NEW,
                Map.of("artist", "Nguyễn Văn A", "year", "2024"));

        createItem(ItemCategory.VEHICLE, "Honda Wave Alpha 2022", "Đã đi 5000km",
                15_000_000, 3, ItemCondition.USED,
                Map.of("brand", "Honda", "model", "Wave Alpha", "manufactureYear", "2022",
                        "mileage", "5000", "color", "Đỏ", "fuelType", "Xăng",
                        "transmission", "Số tay", "ownerCount", "1", "hasRegistration", "true"));

        createAuction(1, 3, 60, 1_000_000);

        System.out.println(" Seed data đã tạo:");
        System.out.println("   Tài khoản: alice/123 (Bidder), bob/123 (Bidder), seller1/123 (Seller)");
        System.out.println("   Sản phẩm: 3 items | Phiên đấu giá: 1 phiên MacBook (60 phút)");
    }
}