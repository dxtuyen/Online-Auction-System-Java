package com.auction.service;

import com.auction.model.entity.*;
import com.auction.model.enums.*;
import com.auction.observer.AuctionEventManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Service trung tâm — quản lý user, item, auction, bid, auto-bid.
 *
 * <h2>Nâng cấp từ Phase 1 lên Phase 2+:</h2>
 * <ul>
 *   <li>{@link ConcurrentHashMap} thay cho HashMap — thread-safe</li>
 *   <li>{@link ReentrantLock} trên {@code placeBid()} — tránh race condition</li>
 *   <li>Tích hợp {@link AuctionEventManager} — push realtime cho client đang xem</li>
 *   <li>Anti-sniping: bid trong X giây cuối → gia hạn Y giây</li>
 *   <li>Auto-bidding: tự động bid thay user đến khi chạm maxBid</li>
 * </ul>
 */
public class AuctionService {

    // ============== CONFIG ==============
    private static final int SNIPE_THRESHOLD_SECONDS = 30;
    private static final int EXTENSION_SECONDS = 60;
    private static final int AUTO_BID_MAX_ITERATIONS = 100;

    // ============== SINGLETON ==============
    private static AuctionService instance;

    public static synchronized AuctionService getInstance() {
        if (instance == null) instance = new AuctionService();
        return instance;
    }

    // ============== DATA STORES ==============
    private final Map<Integer, User> users = new ConcurrentHashMap<>();
    private final Map<Integer, Item> items = new ConcurrentHashMap<>();
    private final Map<Integer, Auction> auctions = new ConcurrentHashMap<>();
    private final Map<Integer, List<BidTransaction>> bidHistory = new ConcurrentHashMap<>();
    private final Map<Integer, List<AutoBid>> autoBids = new ConcurrentHashMap<>();

    // ============== ID GENERATORS ==============
    private final AtomicInteger userIdCounter = new AtomicInteger(0);
    private final AtomicInteger itemIdCounter = new AtomicInteger(0);
    private final AtomicInteger auctionIdCounter = new AtomicInteger(0);
    private final AtomicInteger bidIdCounter = new AtomicInteger(0);

    // ============== LOCKS ==============
    private final Map<Integer, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();

    // ============== OBSERVER ==============
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();

    private AuctionService() {}

    private ReentrantLock getLock(int auctionId) {
        // fair=true: thread chờ lâu nhất vào trước (tránh đói)
        return auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
    }

    // ======================== USER ========================

    public User register(String username, String password, Role role, double extraValue) {
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
    public Collection<User> getAllUsers() { return users.values(); }

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
    public Collection<Item> getAllItems() { return items.values(); }

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
        eventManager.notifyStatusChanged(auction);
    }

    public Auction getAuction(int id) { return auctions.get(id); }
    public Collection<Auction> getAllAuctions() { return auctions.values(); }

    // ======================== BID ========================

    /**
     * Đặt giá — thread-safe nhờ ReentrantLock theo từng phiên.
     * Bên trong lock sẽ:
     * validate → tạo bid → cập nhật phiên → anti-snipe → push → auto-bid đệ quy.
     */
    public BidTransaction placeBid(int auctionId, int bidderId, double bidAmount) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            return placeBidUnsafe(auctionId, bidderId, bidAmount, true);
        } finally {
            lock.unlock();
        }
    }

    private BidTransaction placeBidUnsafe(int auctionId, int bidderId, double bidAmount,
                                          boolean triggerAutoBid) {
        Auction auction = auctions.get(auctionId);
        if (auction == null)
            throw new RuntimeException("Phiên không tồn tại: " + auctionId);

        if (auction.getStatus() != AuctionStatus.RUNNING)
            throw new RuntimeException("Phiên " + auctionId + " không đang đấu giá!");

        if (LocalDateTime.now().isAfter(auction.getEndTime())) {
            auction.transitionTo(AuctionStatus.FINISHED);
            eventManager.notifyStatusChanged(auction);
            throw new RuntimeException("Phiên " + auctionId + " đã hết thời gian!");
        }

        double minRequired = auction.getCurrentPrice() + auction.getMinimumIncrement();
        if (bidAmount < minRequired) {
            throw new RuntimeException(String.format(
                    "Giá %,.0f không hợp lệ! Tối thiểu: %,.0f",
                    bidAmount, minRequired));
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

        // Anti-sniping
        if (auction.snipingCheck(SNIPE_THRESHOLD_SECONDS)) {
            auction.extend(EXTENSION_SECONDS);
            eventManager.notifyExtended(auction);
        }

        eventManager.notifyNewBid(auction, bid);

        if (triggerAutoBid) {
            processAutoBidsRecursive(auction, bidderId);
        }

        return bid;
    }

    public List<BidTransaction> getBidHistory(int auctionId) {
        return bidHistory.getOrDefault(auctionId, new ArrayList<>());
    }

    // ======================== AUTO-BID ========================

    public void registerAutoBid(int auctionId, int bidderId, double maxBid, double increment) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) throw new RuntimeException("Phiên không tồn tại!");
        if (maxBid <= 0 || increment <= 0) throw new RuntimeException("Giá trị auto-bid phải > 0!");

        AutoBid ab = new AutoBid(bidderId, auctionId, maxBid, increment);
        autoBids.computeIfAbsent(auctionId, k -> new ArrayList<>()).add(ab);
    }

    private void processAutoBidsRecursive(Auction auction, int lastBidderId) {
        int iterations = 0;
        while (iterations++ < AUTO_BID_MAX_ITERATIONS) {
            int finalLastBidderId = lastBidderId;
            List<AutoBid> candidates = autoBids.getOrDefault(auction.getId(), List.of()).stream()
                    .filter(AutoBid::isActive)
                    .filter(ab -> ab.getBidderId() != finalLastBidderId)
                    .filter(ab -> ab.getMaxBid() > auction.getCurrentPrice())
                    .sorted(Comparator.comparing(AutoBid::getCreatedAt))
                    .collect(Collectors.toList());

            if (candidates.isEmpty()) break;

            AutoBid winner = candidates.get(0);
            double autoPrice = auction.getCurrentPrice() + winner.getIncrement();
            if (autoPrice > winner.getMaxBid()) autoPrice = winner.getMaxBid();

            if (autoPrice < auction.getCurrentPrice() + auction.getMinimumIncrement()) {
                winner.deactivate();
                continue;
            }

            try {
                placeBidUnsafe(auction.getId(), winner.getBidderId(), autoPrice, false);
                lastBidderId = winner.getBidderId();

                if (autoPrice >= winner.getMaxBid()) winner.deactivate();
            } catch (RuntimeException e) {
                winner.deactivate();
                break;
            }
        }
    }

    // ======================== SEED DATA ========================

    public void seedData() {
        register("alice", "123", Role.BIDDER, 100_000_000);
        register("bob", "123", Role.BIDDER, 100_000_000);
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

        System.out.println("[Seed] alice/123, bob/123, seller1/123, admin/123");
        System.out.println("[Seed] 3 items, 1 phiên MacBook (60 phút)");
    }
}
