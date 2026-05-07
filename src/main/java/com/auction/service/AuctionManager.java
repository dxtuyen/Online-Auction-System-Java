package com.auction.service;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
//import com.auction.model.entity.profile.BidderProfile;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.exception.AuctionClosedException;
import com.auction.model.exception.InsufficientBalanceException;
import com.auction.model.exception.InvalidBidException;
import com.auction.model.observer.AuctionObserver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AuctionManager - REGISTRY TRUNG TÂM quản lý mọi phiên đấu giá trong hệ thống.
 * <p>
 * ============== DESIGN PATTERNS ÁP DỤNG ==============
 * <p>
 * 1. SINGLETON (Bill Pugh / Initialization-on-demand holder idiom)
 * - Toàn hệ thống chỉ có 1 AuctionManager duy nhất
 * - Lazy init, thread-safe, KHÔNG cần synchronized hay volatile
 * - JVM đảm bảo class Holder chỉ load 1 lần khi getInstance() lần đầu
 * → Đây là cách Singleton chuẩn nhất trong Java hiện đại
 * <p>
 * 2. FACADE
 * - Che giấu chi tiết internal (lock, scheduler, observers)
 * - Client chỉ cần biết placeBid(), createAuction()...
 * <p>
 * 3. OBSERVER (qua Auction)
 * - Manager tự đăng ký làm observer của mọi Auction nó quản lý
 * - Khi có event ở Auction → Manager forward cho global observers
 * - Hữu ích cho: log, analytics, push notification...
 * <p>
 * 4. REPOSITORY-LIKE
 * - findById, findActive, findBySellerId... giống Repository pattern
 * <p>
 * ============== CONCURRENCY ==============
 * <p>
 * - ConcurrentHashMap: thread-safe, không cần lock thủ công cho put/get/remove
 * - ScheduledExecutorService: tự động check & chuyển status mỗi giây
 * - Mọi mutate operation trên Auction đã được Auction TỰ lock (xem Auction.java)
 * - Manager KHÔNG lock thêm → tránh nested lock / deadlock
 * <p>
 * ============== TẠI SAO KHÔNG EXTENDS Entity? ==============
 * <p>
 * AuctionManager là SERVICE/REGISTRY, không phải DOMAIN ENTITY.
 * - Entity: có id, lưu DB, được CRUD (User, Auction, Item...)
 * - Service: cung cấp behavior, không lưu DB, là singleton
 * → Trộn lẫn là vi phạm Single Responsibility Principle
 */
public final class AuctionManager {

    // ============== SINGLETON (Bill Pugh idiom) ==============

    /**
     * Holder class chỉ được JVM load khi getInstance() được gọi lần đầu.
     * Class loading trong Java thread-safe by design → không cần synchronized.
     * Đây là Singleton "đẹp" nhất: lazy + thread-safe + không overhead.
     */
    private static final class Holder {
        private static final AuctionManager INSTANCE = new AuctionManager();
    }

    public static AuctionManager getInstance() {
        return Holder.INSTANCE;
    }

    // ============== FIELDS ==============

    /**
     * Storage chính - key: auctionId, value: Auction. ConcurrentHashMap thread-safe.
     */
    private final ConcurrentHashMap<UUID, Auction> auctions = new ConcurrentHashMap<>();

    /**
     * Global observers - nhận event từ TẤT CẢ auction. CopyOnWrite vì đọc nhiều ghi ít.
     */
    private final List<AuctionObserver> globalObservers = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Scheduler tự động chuyển PENDING→RUNNING→FINISHED theo thời gian.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * Cấu hình anti-sniping (tùy chọn - chức năng nâng cao).
     */
    private volatile int snipingThresholdSeconds = 10;   // còn ≤ 10s mà có bid → extend
    private volatile int snipingExtensionSeconds = 30;   // extend thêm 30s

    // ============== INTERNAL OBSERVER ==============

    /**
     * Observer NỘI BỘ - Manager đăng ký vào MỖI Auction.
     * Khi Auction phát event → forward cho global observers + xử lý anti-sniping.
     */
    private final AuctionObserver internalObserver = new AuctionObserver() {
        @Override
        public void onBidPlaced(Auction auction, BidTransaction bid) {
            // 1. Anti-sniping: nếu bid xảy ra trong window cuối → extend auction
            if (auction.isInSnipingWindow(snipingThresholdSeconds)) {
                try {
                    auction.extend(snipingExtensionSeconds);
                } catch (Exception ignored) {
                    // Auction có thể đã FINISHED giữa chừng - ignore
                }
            }
            // 2. Forward cho global observers
            globalObservers.forEach(obs -> safeNotify(() -> obs.onBidPlaced(auction, bid)));
        }

        @Override
        public void onAuctionExtended(Auction auction, int seconds) {
            globalObservers.forEach(obs -> safeNotify(() -> obs.onAuctionExtended(auction, seconds)));
        }

        @Override
        public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
            globalObservers.forEach(obs -> safeNotify(() -> obs.onStatusChanged(auction, oldStatus, newStatus)));
        }
    };

    // ============== CONSTRUCTOR (private - Singleton) ==============

    private AuctionManager() {
        // Daemon thread - không block JVM shutdown
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuctionManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
        startLifecycleScheduler();
    }

    // ============== AUCTION LIFECYCLE MANAGEMENT ==============

    /**
     * Tạo phiên đấu giá MỚI cho item của seller, đăng ký luôn vào registry.
     * Helper để client không phải tự new Auction() rồi register().
     * <p>
     * Bảo mật:
     * - Validate seller phải có quyền sell
     * - Validate item phải tồn tại và thuộc về seller này
     */
    public Auction createAuction(UUID itemId, UUID sellerId, LocalDateTime startTime, LocalDateTime endTime, BigDecimal startingPrice, BigDecimal minimumIncrement) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(sellerId, "sellerId");

        // Check seller có quyền
        User seller = UserManager.getInstance().findById(sellerId).orElseThrow(() -> new IllegalArgumentException("Seller không tồn tại: " + sellerId));
        if (seller.canSell()) {
            throw new IllegalArgumentException("User không có quyền tạo phiên đấu giá(Tài khoản bị khóa)");
        }

        // Check item tồn tại + thuộc về seller này (chống tạo phiên cho item của người khác)
        Item item = ItemManager.getInstance().findById(itemId).orElseThrow(() -> new IllegalArgumentException("Item không tồn tại: " + itemId));
        if (!item.getSellerId().equals(sellerId)) {
            throw new IllegalArgumentException("Item này không thuộc về seller " + sellerId);
        }

        Auction auction = new Auction(itemId, sellerId, startTime, endTime, startingPrice, minimumIncrement);
        return register(auction);
    }

    /**
     * Đăng ký 1 Auction mới vào hệ thống.
     * Auction phải được tạo từ trước (vd qua Seller.createAuction() hoặc service khác).
     *
     * @return chính auction đã đăng ký (để chain)
     * @throws IllegalStateException nếu auction đã tồn tại
     */
    public Auction register(Auction auction) {
        Objects.requireNonNull(auction, "auction must not be null");

        // putIfAbsent atomic - đảm bảo không 2 thread cùng register trùng id
        Auction existed = auctions.putIfAbsent(auction.getId(), auction);
        if (existed != null) {
            throw new IllegalStateException("Auction đã tồn tại với id: " + auction.getId());
        }

        // Tự đăng ký làm observer để theo dõi lifecycle + anti-sniping
        auction.addObserver(internalObserver);
        return auction;
    }

    /**
     * Hủy đăng ký auction (vd: khi đã PAID/CANCELED và muốn dọn memory).
     * Trong production thường archive sang DB thay vì xóa hẳn.
     */
    public boolean unregister(UUID auctionId) {
        Objects.requireNonNull(auctionId);
        Auction removed = auctions.remove(auctionId);
        if (removed != null) {
            removed.removeObserver(internalObserver);
            return true;
        }
        return false;
    }

    // ============== BID OPERATIONS (FACADE) ==============

    /**
     * Đặt bid vào auction.
     * <p>
     * Đây là FACADE method - che giấu việc:
     * - Tìm auction
     * - Validate bidder (tồn tại, có role BIDDER, đang active, đủ tiền)
     * - Tạo BidTransaction
     * - Gọi Auction.placeBid() (đã thread-safe ở tầng dưới)
     * - Anti-sniping tự kích hoạt qua observer
     * <p>
     * BẢO MẬT:
     * - User bị BANNED không bid được (canBid() = false)
     * - Seller/Admin không bid được (không có role BIDDER)
     * - Bidder không đủ tiền không bid được
     * - Self-bidding (seller tự bid item của mình) → block bởi Auction.placeBid
     *
     * @return BidTransaction đã được xử lý (status = VALID nếu thành công)
     * @throws IllegalArgumentException     nếu auction/bidder không tồn tại
     * @throws InvalidBidException          nếu bid không hợp lệ về business rule
     * @throws InsufficientBalanceException nếu bidder không đủ tiền
     * @throws AuctionClosedException       nếu phiên đã đóng
     */
    public BidTransaction placeBid(UUID auctionId, UUID userId, BigDecimal amount) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Không tìm thấy auction: " + auctionId);
        }

        // Validate bidder TRƯỚC khi gọi entity → fail fast
        User user = UserManager.getInstance().findById(userId).orElseThrow(() -> new InvalidBidException("User không tồn tại: " + userId));
        if (!user.canBid()) {
            throw new InvalidBidException("User không có quyền đấu giá (Tài khoản bị khóa)");
        }
        if (!user.hasEnoughBalance(amount)) {
            throw new InsufficientBalanceException("Số dư không đủ. Hiện có: " + user.getBalance() + ", muốn bid: " + amount);
        }

        BidTransaction bid = new BidTransaction(auctionId, userId, amount);
        auction.placeBid(bid);   // tự thread-safe + tự notify
        return bid;
    }

    // ============== QUERY OPERATIONS (REPOSITORY-LIKE) ==============

    /**
     * Tìm theo id - Optional để client xử lý null gracefully
     */
    public Optional<Auction> findById(UUID auctionId) {
        return Optional.ofNullable(auctions.get(auctionId));
    }

    /**
     * Lấy tất cả auction - immutable view, không thể modify từ ngoài
     */
    public Collection<Auction> findAll() {
        return Collections.unmodifiableCollection(auctions.values());
    }

    /**
     * Chỉ lấy phiên đang RUNNING
     */
    public List<Auction> findActive() {
        return auctions.values().stream().filter(Auction::isActive).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Auction theo seller - cho dashboard người bán
     */
    public List<Auction> findBySellerId(UUID sellerId) {
        Objects.requireNonNull(sellerId);
        return auctions.values().stream().filter(a -> a.getSellerId().equals(sellerId)).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Auction theo status - cho admin dashboard, filter UI...
     */
    public List<Auction> findByStatus(AuctionStatus status) {
        Objects.requireNonNull(status);
        return auctions.values().stream().filter(a -> a.getStatus() == status).collect(Collectors.toUnmodifiableList());
    }

    public int count() {
        return auctions.size();
    }

    // ============== GLOBAL OBSERVERS ==============

    /**
     * Đăng ký observer toàn cục - nhận event từ MỌI auction
     */
    public void addGlobalObserver(AuctionObserver observer) {
        globalObservers.add(Objects.requireNonNull(observer));
    }

    public void removeGlobalObserver(AuctionObserver observer) {
        globalObservers.remove(observer);
    }

    // ============== ANTI-SNIPING CONFIG ==============

    public void configureAntiSniping(int thresholdSeconds, int extensionSeconds) {
        if (thresholdSeconds < 0 || extensionSeconds <= 0) {
            throw new IllegalArgumentException("Thông số anti-sniping không hợp lệ");
        }
        this.snipingThresholdSeconds = thresholdSeconds;
        this.snipingExtensionSeconds = extensionSeconds;
    }

    // ============== SCHEDULED TASKS ==============

    /**
     * Scheduler chạy MỖI GIÂY để:
     * - Chuyển PENDING → RUNNING khi tới startTime
     * - Chuyển RUNNING → FINISHED khi quá endTime
     * <p>
     * Dùng scheduleAtFixedRate thay vì thread + sleep vì:
     * - Tự động retry khi 1 lần chạy fail
     * - Tự shutdown gracefully khi gọi shutdown()
     * - Daemon thread → không block JVM exit
     */
    private void startLifecycleScheduler() {
        scheduler.scheduleAtFixedRate(this::tickLifecycle, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Một "tick" - quét tất cả auction và chuyển state nếu cần.
     * Wrap trong try-catch để 1 auction lỗi không làm chết scheduler.
     */
    private void tickLifecycle() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctions.values()) {
            try {
                AuctionStatus status = auction.getStatus();

                // PENDING → RUNNING
                if (status == AuctionStatus.PENDING && !now.isBefore(auction.getStartTime()) && now.isBefore(auction.getEndTime())) {
                    auction.transitionTo(AuctionStatus.RUNNING);
                }
                // RUNNING → FINISHED
                else if (status == AuctionStatus.RUNNING && !now.isBefore(auction.getEndTime())) {
                    auction.transitionTo(AuctionStatus.FINISHED);
                }
            } catch (Exception e) {
                // Log nhưng không throw - scheduler phải tiếp tục chạy
                System.err.println("[AuctionManager] Lỗi khi tick auction " + auction.getId() + ": " + e.getMessage());
            }
        }
    }

    // ============== LIFECYCLE ==============

    /**
     * Shutdown manager - gọi khi server tắt.
     * Quan trọng: nếu không gọi, scheduler thread có thể giữ resource.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ============== HELPERS ==============

    /**
     * Wrap notify để 1 observer crash không làm vỡ chuỗi
     */
    private static void safeNotify(Runnable r) {
        try {
            r.run();
        } catch (Exception ignored) { /* observer không được phép crash hệ thống */ }
    }

    // ============== TEST ONLY ==============

    /**
     * CHỈ dùng trong unit test - reset state để test isolation.
     * Production KHÔNG được gọi method này.
     */
    void clearForTesting() {
        auctions.values().forEach(a -> a.removeObserver(internalObserver));
        auctions.clear();
        globalObservers.clear();
    }
}
