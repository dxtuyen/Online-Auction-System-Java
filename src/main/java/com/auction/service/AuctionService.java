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
 * Service trung tâm của domain đấu giá.
 *
 * <p>Class này gom toàn bộ nghiệp vụ chính của hệ thống:
 * đăng ký user, tạo item, mở/đóng auction, đặt giá, auto-bid,
 * reserve số dư và settle giao dịch khi auction kết thúc.</p>
 *
 * <h2>Các nguyên tắc nghiệp vụ đang được áp ở đây</h2>
 * <ul>
 *   <li>Mỗi auction chỉ chấp nhận bid khi đang ở trạng thái {@link AuctionStatus#RUNNING}.</li>
 *   <li>Bid mới phải lớn hơn hoặc bằng {@code currentPrice + minimumIncrement}.</li>
 *   <li>Bidder không chỉ cần đủ số dư cho bid hiện tại, mà còn phải đủ
 *       <em>số dư khả dụng</em> sau khi trừ các auction khác mà họ đang dẫn đầu.</li>
 *   <li>Khi auction kết thúc, nếu có người thắng thì hệ thống sẽ settle:
 *       trừ tiền bidder thắng, cộng doanh thu seller và chuyển trạng thái sang {@link AuctionStatus#PAID}.</li>
 *   <li>Auto-bid dùng chung các rule về bước nhảy, số dư khả dụng và trạng thái auction như bid tay.</li>
 * </ul>
 *
 * <h2>Concurrency model</h2>
 * <ul>
 *   <li>{@link ConcurrentHashMap} cho các store trong bộ nhớ.</li>
 *   <li>Lock theo auction để serialize các thao tác thay đổi state của một phiên.</li>
 *   <li>Lock theo user để chặn trường hợp cùng một bidder over-commit trên nhiều auction cùng lúc.</li>
 *   <li>Settlement khóa user theo thứ tự id để tránh deadlock.</li>
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
    private final Map<Integer, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    // ============== OBSERVER ==============
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();

    private AuctionService() {}

    private ReentrantLock getLock(int auctionId) {
        // fair=true: thread chờ lâu nhất vào trước (tránh đói)
        return auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
    }

    /**
     * Lock theo user được dùng cho các nghiệp vụ liên quan tới balance/reserve.
     *
     * <p>Ví dụ: nếu cùng lúc có hai request bid ở hai auction khác nhau cho cùng một bidder,
     * ta cần serialize chúng để không có chuyện cả hai cùng đọc thấy "còn tiền" rồi cùng pass.</p>
     */
    private ReentrantLock getUserLock(int userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true));
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

    /**
     * Đóng auction theo cách "chuẩn nghiệp vụ".
     *
     * <p>Khác với việc tự đổi status, method này luôn đi qua finalize flow để:
     * kiểm tra quyền người thực hiện,
     * đổi trạng thái nếu cần,
     * settle tiền khi có winner,
     * và phát event realtime cho các client đang theo dõi.</p>
     *
     * <p>Quy tắc quyền hiện tại:
     * chỉ seller sở hữu auction đó hoặc admin mới được phép đóng phiên.</p>
     */
    public void closeAuction(int auctionId, int actorUserId) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            Auction auction = auctions.get(auctionId);
            if (auction == null) throw new RuntimeException("Phiên không tồn tại: " + auctionId);
            ensureUserCanCloseAuction(actorUserId, auction);
            finalizeAuction(auction);
        } finally {
            lock.unlock();
        }
    }

    public Auction getAuction(int id) { return auctions.get(id); }
    public Collection<Auction> getAllAuctions() { return auctions.values(); }

    // ======================== BID ========================

    /**
     * Entry point công khai cho bid tay.
     *
     * <p>Lock theo auction đảm bảo chỉ một luồng được sửa state của auction tại một thời điểm.
     * Bên trong sẽ đi qua cùng một pipeline:
     * validate auction -> validate bidder -> validate số dư khả dụng ->
     * ghi bid -> anti-sniping -> push event -> kích hoạt auto-bid nếu cần.</p>
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

    /**
     * Core bidding logic.
     *
     * <p>Method này giả định caller đã giữ lock theo auction.
     * Cờ {@code triggerAutoBid} dùng để phân biệt:
     * bid tay ban đầu sẽ cho phép kích hoạt auto-bid chain,
     * còn bid sinh ra từ auto-bid thì không kích hoạt lại chain mới để tránh vòng lặp vô hạn.</p>
     */
    private BidTransaction placeBidUnsafe(int auctionId, int bidderId, double bidAmount,
                                          boolean triggerAutoBid) {
        Auction auction = auctions.get(auctionId);
        if (auction == null)
            throw new RuntimeException("Phiên không tồn tại: " + auctionId);

        if (auction.getStatus() != AuctionStatus.RUNNING)
            throw new RuntimeException("Phiên " + auctionId + " không đang đấu giá!");

        if (LocalDateTime.now().isAfter(auction.getEndTime())) {
            // Nếu request tới sau thời điểm endTime, auction được finalize ngay để state
            // trong memory luôn khớp với thực tế trước khi trả lỗi hết hạn cho caller.
            finalizeAuction(auction);
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
        Bidder bidderEntity = (Bidder) bidder;
        BidTransaction bid;
        // Ngoài lock auction, ta khóa tiếp theo user để việc đọc reserve + ghi bid là một
        // transaction logic nguyên khối đối với riêng bidder đó.
        ReentrantLock userLock = getUserLock(bidderId);
        userLock.lock();
        try {
            ensureBidderCanCoverAmount(bidderEntity, auctionId, bidAmount);

            int bidId = bidIdCounter.incrementAndGet();
            bid = new BidTransaction(auctionId, bidderId, bidAmount);
            bid.setId(bidId);

            // Khi bid hợp lệ, bidder mới lập tức trở thành leader của auction này.
            auction.setCurrentPrice(bidAmount);
            auction.setHighestBidderId(bidderId);
            auction.incrementTotalBids();

            bidHistory.computeIfAbsent(auctionId, k -> new ArrayList<>()).add(bid);
        } finally {
            userLock.unlock();
        }

        // Anti-sniping: bid xuất hiện quá sát giờ kết thúc thì gia hạn thêm để tránh "chốt kèo"
        // trong vài giây cuối mà người khác không kịp phản ứng.
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

    /**
     * Chặn thao tác đóng auction từ user không đủ quyền.
     *
     * <p>Rule được giữ ở service để dù request đi từ console app hay socket server
     * thì quyền hạn vẫn được áp đồng nhất ở cùng một chỗ.</p>
     */
    private void ensureUserCanCloseAuction(int actorUserId, Auction auction) {
        User actor = users.get(actorUserId);
        if (actor == null) {
            throw new RuntimeException("Người thực hiện không tồn tại!");
        }

        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isOwnerSeller = actor.getRole() == Role.SELLER && actor.getId() == auction.getSellerId();
        if (!isAdmin && !isOwnerSeller) {
            throw new RuntimeException("Bạn không có quyền đóng phiên đấu giá này!");
        }
    }

    /**
     * Kiểm tra bidder có đủ số dư khả dụng cho mức giá mục tiêu hay không.
     *
     * <p>"Số dư khả dụng" ở đây không đơn giản là balance hiện tại.
     * Nó bằng balance thật trừ đi phần tiền đang bị giữ chỗ ở các auction khác
     * mà bidder đang dẫn đầu.</p>
     */
    private void ensureBidderCanCoverAmount(Bidder bidder, int auctionId, double targetAmount) {
        double maxAffordableBid = calculateMaxAffordableBid(bidder.getId(), auctionId);
        if (targetAmount > maxAffordableBid) {
            throw new RuntimeException(String.format(
                    "Số dư khả dụng không đủ để đặt giá %,.0f (khả dụng: %,.0f)",
                    targetAmount, maxAffordableBid));
        }
    }

    /**
     * Tính trần giá mà bidder còn có thể bid ở auction hiện tại.
     *
     * <p>Auction hiện tại được loại trừ khỏi reserve để bidder vẫn có thể tăng giá
     * trên chính auction mình đang dẫn đầu. Nếu không loại trừ, bidder sẽ bị "tự khóa"
     * vì bid cũ của chính họ cũng bị tính là tiền đã giữ chỗ.</p>
     */
    private double calculateMaxAffordableBid(int bidderId, int auctionId) {
        Bidder bidder = (Bidder) users.get(bidderId);
        double reservedAmount = calculateReservedAmount(bidderId, auctionId);
        return bidder.getBalance() - reservedAmount;
    }

    /**
     * Tính tổng tiền đang được reserve cho bidder ở các auction khác.
     *
     * <p>Reserve được suy ra động từ state hiện tại của auction, không lưu thêm một ledger riêng:
     * auction nào bidder đang dẫn đầu thì {@code currentPrice} của auction đó được coi là đang giữ chỗ.
     * Khi bidder bị outbid, reserve được giải phóng tự động vì leader đã đổi sang người khác.</p>
     */
    private double calculateReservedAmount(int bidderId, int excludeAuctionId) {
        double reserved = 0;
        for (Auction currentAuction : auctions.values()) {
            if (currentAuction.getId() == excludeAuctionId) continue;
            if (currentAuction.getStatus() != AuctionStatus.RUNNING) continue;

            Integer highestBidderId = currentAuction.getHighestBidderIdOrNull();
            if (highestBidderId != null && highestBidderId == bidderId) {
                reserved += currentAuction.getCurrentPrice();
            }
        }
        return reserved;
    }

    /**
     * Chuẩn hóa việc kết thúc auction.
     *
     * <p>Method này được dùng cả khi đóng auction thủ công lẫn khi phát hiện auction đã hết giờ
     * trong lúc có người đang cố bid. Nó có 3 trách nhiệm:
     * chuyển trạng thái sang {@code FINISHED} nếu cần,
     * settle tiền nếu có winner,
     * và phát event để client cập nhật realtime.</p>
     */
    private void finalizeAuction(Auction auction) {
        if (auction.getStatus() == AuctionStatus.CANCELED) {
            eventManager.notifyStatusChanged(auction);
            return;
        }
        if (auction.getStatus() == AuctionStatus.RUNNING) {
            auction.transitionTo(AuctionStatus.FINISHED);
        }
        if (auction.getStatus() == AuctionStatus.FINISHED) {
            settleAuction(auction);
        }
        eventManager.notifyStatusChanged(auction);
    }

    /**
     * Settlement cuối cho auction đã kết thúc.
     *
     * <p>Nếu auction không có người thắng, method này chỉ return.
     * Nếu có winner, ta khóa bidder + seller, kiểm tra lại số dư lần cuối,
     * trừ tiền bidder, cộng doanh thu seller và đánh dấu auction là {@code PAID}.</p>
     */
    private void settleAuction(Auction auction) {
        Integer highestBidderId = auction.getHighestBidderIdOrNull();
        if (highestBidderId == null) {
            return;
        }

        // Khóa theo thứ tự id để tránh deadlock khi cùng lúc có nhiều auction được settle
        // và hai luồng cố cầm lock bidder/seller theo thứ tự ngược nhau.
        int sellerId = auction.getSellerId();
        int firstUserId = Math.min(highestBidderId, sellerId);
        int secondUserId = Math.max(highestBidderId, sellerId);
        ReentrantLock firstLock = getUserLock(firstUserId);
        ReentrantLock secondLock = getUserLock(secondUserId);

        firstLock.lock();
        try {
            if (secondUserId != firstUserId) {
                secondLock.lock();
            }
            try {
                User bidderUser = users.get(highestBidderId);
                User sellerUser = users.get(sellerId);
                if (!(bidderUser instanceof Bidder bidder)) {
                    throw new RuntimeException("Người thắng không hợp lệ!");
                }
                if (!(sellerUser instanceof Seller seller)) {
                    throw new RuntimeException("Seller không hợp lệ!");
                }

                // finalPrice chính là giá thắng cuộc tại thời điểm auction kết thúc.
                double finalPrice = auction.getCurrentPrice();
                if (bidder.getBalance() < finalPrice) {
                    throw new RuntimeException(String.format(
                            "Người thắng không đủ số dư để thanh toán %,.0f", finalPrice));
                }

                bidder.setBalance(bidder.getBalance() - finalPrice);
                seller.setTotalRevenue(seller.getTotalRevenue() + finalPrice);
                auction.transitionTo(AuctionStatus.PAID);
            } finally {
                if (secondUserId != firstUserId) {
                    secondLock.unlock();
                }
            }
        } finally {
            firstLock.unlock();
        }
    }

    // ======================== AUTO-BID ========================

    /**
     * Đăng ký auto-bid cho một bidder.
     *
     * <p>Khi đăng ký, hệ thống chưa trừ tiền ngay nhưng vẫn kiểm tra số dư khả dụng
     * theo mức {@code maxBid}. Như vậy bidder không thể cấu hình một auto-bid vượt quá
     * khả năng chi trả của mình ở thời điểm đăng ký.</p>
     */
    public void registerAutoBid(int auctionId, int bidderId, double maxBid, double increment) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) throw new RuntimeException("Phiên không tồn tại!");
        if (maxBid <= 0 || increment <= 0) throw new RuntimeException("Giá trị auto-bid phải > 0!");
        User bidder = users.get(bidderId);
        if (bidder == null || bidder.getRole() != Role.BIDDER)
            throw new RuntimeException("Bidder không hợp lệ!");

        ReentrantLock userLock = getUserLock(bidderId);
        userLock.lock();
        try {
            ensureBidderCanCoverAmount((Bidder) bidder, auctionId, maxBid);
        } finally {
            userLock.unlock();
        }

        AutoBid ab = new AutoBid(bidderId, auctionId, maxBid, increment);
        autoBids.computeIfAbsent(auctionId, k -> new ArrayList<>()).add(ab);
    }

    /**
     * Chạy vòng auto-bid cho đến khi không còn ai đủ điều kiện phản ứng nữa.
     *
     * <p>Thuật toán hiện tại chọn candidate theo thứ tự đăng ký sớm hơn trước.
     * Mỗi vòng chỉ đẩy giá thêm đúng {@code increment} của candidate thắng vòng đó.
     * Nếu candidate không còn hợp lệ ở thời điểm thực thi, auto-bid của họ sẽ bị deactivate.</p>
     */
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

            // Candidate đầu tiên theo createdAt được xem là người có quyền phản ứng trước.
            AutoBid winner = candidates.get(0);
            double autoPrice = auction.getCurrentPrice() + winner.getIncrement();
            if (autoPrice > winner.getMaxBid()) autoPrice = winner.getMaxBid();

            if (autoPrice < auction.getCurrentPrice() + auction.getMinimumIncrement()) {
                // Nếu increment của candidate không còn đủ để tạo một bid hợp lệ,
                // auto-bid này không còn giá trị sử dụng nên được tắt đi.
                winner.deactivate();
                continue;
            }

            try {
                // Dùng lại cùng core bidding logic để auto-bid và bid tay luôn đi chung một bộ rule.
                placeBidUnsafe(auction.getId(), winner.getBidderId(), autoPrice, false);
                lastBidderId = winner.getBidderId();

                if (autoPrice >= winner.getMaxBid()) winner.deactivate();
            } catch (RuntimeException e) {
                // Nếu candidate fail ở đây, giữ họ active sẽ chỉ tạo lỗi lặp lại ở vòng sau.
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
