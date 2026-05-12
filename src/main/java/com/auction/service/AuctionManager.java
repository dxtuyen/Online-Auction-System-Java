package com.auction.service;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.observer.AuctionObserver;
import com.auction.persistence.dao.AuctionDao;
import com.auction.persistence.dao.MysqlAuctionDao;

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
 * AuctionManager - REGISTRY trung tâm quản lý mọi phiên đấu giá.
 * <p>
 * ============== KIẾN TRÚC PERSISTENCE ==============
 * <p>
 * Write-through cache:
 * - In-memory map làm cache + chứa runtime state (lock, observers)
 * - createAuction insert DB → add cache → register observer
 * - Internal observer hook DAO.update() sau MỌI event (bid/extend/status-change)
 *   → mọi mutation đều flush xuống DB tự động
 * - loadAllFromDb add cache + register observer (KHÔNG insert)
 * <p>
 * ============== DESIGN PATTERNS ==============
 * 1. SINGLETON (Bill Pugh idiom)
 * 2. FACADE - che giấu lock, scheduler, DAO
 * 3. OBSERVER - manager tự đăng ký observer của mọi auction
 * 4. REPOSITORY-LIKE
 * <p>
 * ============== CONCURRENCY ==============
 * - ConcurrentHashMap thread-safe cho cache
 * - ScheduledExecutorService chuyển status mỗi giây
 * - Mọi mutation trên Auction được entity TỰ lock (xem Auction.java)
 * - Manager KHÔNG lock thêm → tránh deadlock
 */
public final class AuctionManager {

    // ============== SINGLETON ==============

    private static final class Holder {
        private static final AuctionManager INSTANCE = new AuctionManager();
    }

    public static AuctionManager getInstance() {
        return Holder.INSTANCE;
    }

    // ============== FIELDS ==============

    private final AuctionDao dao;
    private final ConcurrentHashMap<UUID, Auction> auctions = new ConcurrentHashMap<>();
    private final List<AuctionObserver> globalObservers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;

    private volatile int snipingThresholdSeconds = 10;
    private volatile int snipingExtensionSeconds = 30;

    // ============== INTERNAL OBSERVER ==============

    /**
     * Observer NỘI BỘ - đăng ký vào MỖI auction. Khi auction phát event:
     * 1. Anti-sniping (chỉ với onBidPlaced)
     * 2. Persist state mới xuống DB qua DAO
     * 3. Forward cho global observers
     */
    private final AuctionObserver internalObserver = new AuctionObserver() {
        @Override
        public void onBidPlaced(Auction auction, BidTransaction bid) {
            if (auction.isInSnipingWindow(snipingThresholdSeconds)) {
                try {
                    auction.extend(snipingExtensionSeconds);   // sẽ fire onAuctionExtended
                } catch (Exception ignored) { /* auction có thể đã FINISHED */ }
            }
            safeSave(auction);
            globalObservers.forEach(obs -> safeNotify(() -> obs.onBidPlaced(auction, bid)));
        }

        @Override
        public void onAuctionExtended(Auction auction, int seconds) {
            safeSave(auction);
            globalObservers.forEach(obs -> safeNotify(() -> obs.onAuctionExtended(auction, seconds)));
        }

        @Override
        public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
            safeSave(auction);
            globalObservers.forEach(obs -> safeNotify(() -> obs.onStatusChanged(auction, oldStatus, newStatus)));
        }
    };

    // ============== CONSTRUCTOR ==============

    private AuctionManager() {
        this.dao = new MysqlAuctionDao();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuctionManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
        startLifecycleScheduler();
    }

    // ============== BOOTSTRAP ==============

    /**
     * Load toàn bộ auction từ DB vào cache + register observer cho mỗi cái.
     * Phải gọi SAU User+Item (FK item_id, seller_id, highest_bidder_id).
     */
    public void loadAllFromDb() {
        // Cần dọn observer cũ nếu gọi lại để tránh duplicate
        auctions.values().forEach(a -> a.removeObserver(internalObserver));
        auctions.clear();
        for (Auction a : dao.findAll()) {
            auctions.put(a.getId(), a);
            a.addObserver(internalObserver);
        }
        System.out.println("[AuctionManager] Đã load " + auctions.size() + " auction từ DB");
    }

    public long countInDb() {
        return dao.count();
    }

    // ============== AUCTION LIFECYCLE ==============

    /**
     * Tạo phiên đấu giá MỚI cho item của seller, persist DB, register observer.
     */
    public Auction createAuction(UUID itemId, UUID sellerId,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 BigDecimal startingPrice, BigDecimal minimumIncrement) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(sellerId, "sellerId");

        User seller = UserManager.getInstance().findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("Seller không tồn tại: " + sellerId));
        if (!seller.canSell()) {
            throw new IllegalArgumentException("User không có quyền tạo phiên đấu giá (Tài khoản bị khóa)");
        }

        Item item = ItemManager.getInstance().findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item không tồn tại: " + itemId));
        if (!item.getSellerId().equals(sellerId)) {
            throw new IllegalArgumentException("Item này không thuộc về seller " + sellerId);
        }

        Auction auction = new Auction(itemId, sellerId, startTime, endTime,
                startingPrice, minimumIncrement);

        // Persist DB TRƯỚC khi commit cache. Nếu DB fail → cache không thay đổi.
        dao.insert(auction);
        auctions.put(auction.getId(), auction);
        auction.addObserver(internalObserver);
        return auction;
    }

    // ============== MANUAL CLOSE ==============

    /**
     * Đóng phiên theo yêu cầu seller (PENDING→CANCELED hoặc RUNNING→FINISHED).
     * State change sẽ tự fire onStatusChanged → DAO update.
     *
     * @throws IllegalArgumentException nếu auction không tồn tại
     * @throws SecurityException        nếu actor không phải seller của phiên
     * @throws IllegalStateException    nếu phiên đã ở terminal state
     */
    public Auction closeAuction(UUID auctionId, UUID actorUserId) {
        Objects.requireNonNull(auctionId, "auctionId");
        Objects.requireNonNull(actorUserId, "actorUserId");

        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Không tìm thấy auction: " + auctionId);
        }
        if (!auction.getSellerId().equals(actorUserId)) {
            throw new SecurityException("Bạn không có quyền đóng phiên đấu giá này");
        }

        AuctionStatus current = auction.getStatus();
        switch (current) {
            case PENDING -> auction.transitionTo(AuctionStatus.CANCELED);
            case RUNNING -> auction.transitionTo(AuctionStatus.FINISHED);
            default -> throw new IllegalStateException(
                    "Phiên đang ở trạng thái " + current + ", không thể đóng thủ công");
        }
        return auction;
    }

    // ============== QUERIES ==============

    public Optional<Auction> findById(UUID auctionId) {
        return Optional.ofNullable(auctions.get(auctionId));
    }

    public Collection<Auction> findAll() {
        return Collections.unmodifiableCollection(auctions.values());
    }

    public List<Auction> findActive() {
        return auctions.values().stream().filter(Auction::isActive)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<Auction> findBySellerId(UUID sellerId) {
        Objects.requireNonNull(sellerId);
        return auctions.values().stream()
                .filter(a -> a.getSellerId().equals(sellerId))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<Auction> findByStatus(AuctionStatus status) {
        Objects.requireNonNull(status);
        return auctions.values().stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toUnmodifiableList());
    }

    public int count() {
        return auctions.size();
    }

    // ============== GLOBAL OBSERVERS ==============

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

    private void startLifecycleScheduler() {
        scheduler.scheduleAtFixedRate(this::tickLifecycle, 1, 1, TimeUnit.SECONDS);
    }

    private void tickLifecycle() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctions.values()) {
            try {
                AuctionStatus status = auction.getStatus();
                if (status == AuctionStatus.PENDING
                        && !now.isBefore(auction.getStartTime())
                        && now.isBefore(auction.getEndTime())) {
                    auction.transitionTo(AuctionStatus.RUNNING);
                } else if (status == AuctionStatus.RUNNING
                        && !now.isBefore(auction.getEndTime())) {
                    auction.transitionTo(AuctionStatus.FINISHED);
                }
            } catch (Exception e) {
                System.err.println("[AuctionManager] Lỗi khi tick auction "
                        + auction.getId() + ": " + e.getMessage());
            }
        }
    }

    // ============== LIFECYCLE ==============

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

    /** Wrap DAO update để 1 lỗi DB không làm vỡ chain observer. */
    private void safeSave(Auction auction) {
        try {
            dao.update(auction);
        } catch (Exception e) {
            System.err.println("[AuctionManager] Lỗi sync DB cho auction "
                    + auction.getId() + ": " + e.getMessage());
        }
    }

    private static void safeNotify(Runnable r) {
        try {
            r.run();
        } catch (Exception ignored) { /* observer không được phép crash hệ thống */ }
    }

    // ============== TEST ONLY ==============

    void clearForTesting() {
        auctions.values().forEach(a -> a.removeObserver(internalObserver));
        auctions.clear();
        globalObservers.clear();
    }
}
