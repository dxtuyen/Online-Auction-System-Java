package com.auction.service;

import com.auction.config.AppConfig;
import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.exception.IllegalAuctionStateException;
import com.auction.model.observer.AuctionObserver;
import com.auction.persistence.dao.AuctionDao;
import com.auction.persistence.dao.MysqlAuctionDao;
import com.auction.util.AppLogger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class AuctionManager {

    private static final Logger log = AppLogger.get(AuctionManager.class);

    // ============== SINGLETON ==============

    private static final class Holder {
        private static final AuctionManager INSTANCE = new AuctionManager();
    }

    public static AuctionManager getInstance() {
        return Holder.INSTANCE;
    }

    // ============== FIELDS ==============

    /**
     * Phiên ở FINISHED quá ngần này phút mà winner chưa quyết định → tự PAY giúp.
     * Đổi 1 chỗ ở đây là xong (không cần đụng nhiều file).
     */
    private static final int AUTO_PAY_AFTER_MINUTES = 5;

    /**
     * Tỷ lệ phạt khi winner từ chối thanh toán. Tính trên startingPrice của item.
     * Đổi 1 chỗ ở đây là xong.
     */
    private static final BigDecimal FORFEIT_PENALTY_RATE = new BigDecimal("0.4");

    private final AuctionDao dao;
    private final ConcurrentHashMap<UUID, Auction> auctions = new ConcurrentHashMap<>();
    private final List<AuctionObserver> globalObservers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;

    // Anti-sniping: cấu hình qua .env (SNIPING_THRESHOLD_SECONDS, SNIPING_EXTENSION_SECONDS).
    private volatile int snipingThresholdSeconds;
    private volatile int snipingExtensionSeconds;

    // ============== INTERNAL OBSERVER ==============

    private final AuctionObserver internalObserver = new AuctionObserver() {
        @Override
        public void onBidPlaced(Auction auction, BidTransaction bid) {
            if (auction.isInSnipingWindow(snipingThresholdSeconds)) {
                try {
                    auction.extend(snipingExtensionSeconds);
                } catch (Exception ignored) { }
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
        this.snipingThresholdSeconds = AppConfig.getInt("SNIPING_THRESHOLD_SECONDS", 10);
        this.snipingExtensionSeconds = AppConfig.getInt("SNIPING_EXTENSION_SECONDS", 30);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuctionManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
        startLifecycleScheduler();
    }

    // ============== BOOTSTRAP ==============

    public void loadAllFromDb() {
        auctions.values().forEach(a -> a.removeObserver(internalObserver));
        auctions.clear();
        for (Auction a : dao.findAll()) {
            auctions.put(a.getId(), a);
            a.addObserver(internalObserver);
        }
        log.info(() -> "Đã load " + auctions.size() + " auction từ DB");
    }

    public long countInDb() {
        return dao.count();
    }

    // ============== AUCTION LIFECYCLE ==============

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

        dao.insert(auction);
        auctions.put(auction.getId(), auction);
        auction.addObserver(internalObserver);
        return auction;
    }

    public Auction register(Auction auction) {
        Objects.requireNonNull(auction, "auction must not be null");
        Auction existed = auctions.putIfAbsent(auction.getId(), auction);
        if (existed != null) {
            throw new IllegalStateException("Auction đã tồn tại với id: " + auction.getId());
        }
        auction.addObserver(internalObserver);
        return auction;
    }

    public boolean unregister(UUID auctionId) {
        Objects.requireNonNull(auctionId);
        Auction removed = auctions.remove(auctionId);
        if (removed != null) {
            removed.removeObserver(internalObserver);
            return true;
        }
        return false;
    }

    // ============== MANUAL CLOSE ==============

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
            case RUNNING -> {
                // Đóng tay: nếu chưa có bid → CANCELED ngay; có bid → FINISHED chờ winner quyết định.
                if (auction.getHighestBidderId() == null) {
                    auction.transitionTo(AuctionStatus.CANCELED);
                } else {
                    auction.transitionTo(AuctionStatus.FINISHED);
                }
            }
            default -> throw new IllegalStateException(
                    "Phiên đang ở trạng thái " + current + ", không thể đóng thủ công");
        }
        return auction;
    }

    /**
     * Admin đóng cưỡng bức phiên — KHÔNG check seller, đóng được mọi auction
     * đang ở PENDING/RUNNING. Nếu RUNNING có bid → vẫn cancel luôn (admin override
     * khác seller-close): hoàn lại reserve cho người đang dẫn đầu để money không thất thoát.
     *
     * <p>Router đã enforce role ADMIN trước khi gọi method này.</p>
     */
    public Auction adminCloseAuction(UUID auctionId) {
        Objects.requireNonNull(auctionId, "auctionId");

        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Không tìm thấy auction: " + auctionId);
        }

        AuctionStatus current = auction.getStatus();
        if (current != AuctionStatus.PENDING && current != AuctionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Phiên đang ở trạng thái " + current + ", không thể đóng");
        }

        // Pre-lookup leader nếu có để refund sau khi transition.
        UUID leaderId = auction.getHighestBidderId();
        BigDecimal refund = leaderId == null ? null : auction.getCurrentPrice();
        User leader = leaderId == null ? null
                : UserManager.getInstance().findById(leaderId).orElse(null);

        // transitionTo atomic — nếu thread khác (auto-tick) vừa đổi status, throw trước
        // khi mutate balance → không double-refund.
        auction.transitionTo(AuctionStatus.CANCELED);

        if (leader != null && refund != null && refund.signum() > 0) {
            leader.release(refund);
            safeSaveUser(leader);
        }
        return auction;
    }

    // ============== SETTLEMENT (USER-FACING) ==============

    /**
     * Winner xác nhận thanh toán: tiền đã reserve khi bid chuyển vào revenue seller,
     * status → PAID.
     *
     * @throws SecurityException nếu actor không phải winner
     * @throws IllegalStateException nếu phiên không ở trạng thái FINISHED
     */
    public Auction confirmPayment(UUID auctionId, UUID userId) {
        Objects.requireNonNull(auctionId, "auctionId");
        Objects.requireNonNull(userId, "userId");

        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Không tìm thấy auction: " + auctionId);
        }
        if (auction.getStatus() != AuctionStatus.FINISHED) {
            throw new IllegalAuctionStateException(
                    "Phiên đang ở trạng thái " + auction.getStatus() + ", không thể thanh toán");
        }
        if (!userId.equals(auction.getHighestBidderId())) {
            throw new SecurityException("Bạn không phải người thắng phiên đấu giá này");
        }

        settleAsPaid(auction);
        return auction;
    }

    /**
     * Winner từ chối thanh toán: mất {@link #FORFEIT_PENALTY_RATE} × startingPrice
     * cho seller, phần còn lại của tiền đã reserve được hoàn về balance của winner.
     * Status → CANCELED.
     */
    public Auction forfeitAuction(UUID auctionId, UUID userId) {
        Objects.requireNonNull(auctionId, "auctionId");
        Objects.requireNonNull(userId, "userId");

        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Không tìm thấy auction: " + auctionId);
        }
        if (auction.getStatus() != AuctionStatus.FINISHED) {
            throw new IllegalAuctionStateException(
                    "Phiên đang ở trạng thái " + auction.getStatus() + ", không thể hủy");
        }
        if (!userId.equals(auction.getHighestBidderId())) {
            throw new SecurityException("Bạn không phải người thắng phiên đấu giá này");
        }

        BigDecimal reserved = auction.getCurrentPrice();
        BigDecimal penalty = auction.getStartingPrice().multiply(FORFEIT_PENALTY_RATE);
        // Defensive: nếu startingPrice * 0.4 > reserved (về lý thuyết không thể vì
        // currentPrice >= startingPrice), kẹp lại để không trừ âm.
        if (penalty.compareTo(reserved) > 0) penalty = reserved;
        BigDecimal refund = reserved.subtract(penalty);

        // Pre-lookup users TRƯỚC khi transition để fail-fast nếu thiếu dữ liệu.
        User winner = UserManager.getInstance().findById(userId)
                .orElseThrow(() -> new IllegalStateException("Winner không tồn tại"));
        User seller = UserManager.getInstance().findById(auction.getSellerId())
                .orElseThrow(() -> new IllegalStateException("Seller không tồn tại"));

        // transitionTo là gate atomic: nếu thread khác (vd auto-PAY hoặc confirmPayment)
        // đã đổi status, call này sẽ throw IllegalAuctionStateException trước khi money
        // bị mutate → không có double-charge.
        auction.transitionTo(AuctionStatus.CANCELED);

        if (refund.signum() > 0) {
            winner.release(refund);
            safeSaveUser(winner);
        }
        if (penalty.signum() > 0) {
            seller.addRevenue(penalty);
            safeSaveUser(seller);
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
                    // Hết giờ: chưa có bid → CANCELED luôn; có bid → FINISHED chờ winner quyết định.
                    if (auction.getHighestBidderId() == null) {
                        auction.transitionTo(AuctionStatus.CANCELED);
                    } else {
                        auction.transitionTo(AuctionStatus.FINISHED);
                    }
                } else if (status == AuctionStatus.FINISHED
                        && auction.getHighestBidderId() != null
                        && auction.getUpdatedAt().plusMinutes(AUTO_PAY_AFTER_MINUTES).isBefore(now)) {
                    // Winner không thao tác sau AUTO_PAY_AFTER_MINUTES phút → tự PAY giúp.
                    // updatedAt sau khi RUNNING→FINISHED chính là thời điểm phiên kết thúc.
                    try {
                        settleAsPaid(auction);
                    } catch (Exception e) {
                        log.warning(() -> "Auto-PAY thất bại cho auction "
                                + auction.getId() + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warning(() -> "Lỗi khi tick auction " + auction.getId() + ": " + e.getMessage());
            }
        }
    }

    // ============== SETTLEMENT (INTERNAL) ==============

    /**
     * Chuyển tiền winner reserved → revenue seller, chuyển item cho winner, status → PAID.
     * Dùng chung cho {@link #confirmPayment(UUID, UUID)} (user gọi) và
     * tickLifecycle (scheduler tự PAY sau timeout).
     * <p>Caller đã đảm bảo {@code auction.getHighestBidderId() != null}
     * và auction ở trạng thái FINISHED.</p>
     */
    private void settleAsPaid(Auction auction) {
        BigDecimal price = auction.getCurrentPrice();
        UUID winnerId = auction.getHighestBidderId();
        if (winnerId == null) {
            throw new IllegalStateException("Auction không có winner: " + auction.getId());
        }

        // Pre-lookup TRƯỚC khi transition để fail-fast (không transition rồi mới thấy thiếu dữ liệu).
        User seller = UserManager.getInstance().findById(auction.getSellerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Seller không tồn tại: " + auction.getSellerId()));
        UserManager.getInstance().findById(winnerId)
                .orElseThrow(() -> new IllegalStateException("Winner không tồn tại: " + winnerId));
        ItemManager.getInstance().findById(auction.getItemId())
                .orElseThrow(() -> new IllegalStateException("Item không tồn tại: " + auction.getItemId()));

        // transitionTo là gate atomic: chống race giữa confirmPayment vs forfeit vs auto-PAY.
        auction.transitionTo(AuctionStatus.PAID);

        safeTransferItemOwnership(auction, winnerId);
        seller.addRevenue(price);
        safeSaveUser(seller);
    }

    private void safeTransferItemOwnership(Auction auction, UUID winnerId) {
        try {
            ItemManager.getInstance().transferOwnership(auction.getItemId(), winnerId);
        } catch (Exception e) {
            log.severe(() -> "CRITICAL: Không chuyển ownership item " + auction.getItemId()
                    + " cho winner " + winnerId + " sau khi auction " + auction.getId()
                    + " đã PAID. RAM-DB có thể lệch: " + e.getMessage());
        }
    }

    /**
     * Persist user state với log SEVERE nếu fail.
     *
     * <p>Lưu ý: caller (forfeitAuction / settleAsPaid) đã mutate balance/revenue
     * trong RAM TRƯỚC khi gọi method này. Nếu DB fail, RAM-DB lệch — devs phải
     * sync manual hoặc restart sẽ rollback partial. Đây là TOCTOU đã biết; fix
     * triệt để cần transactional outbox bao cả auction + user trong 1 tx.</p>
     */
    private void safeSaveUser(User user) {
        try {
            UserManager.getInstance().save(user);
        } catch (Exception e) {
            log.severe(() -> "CRITICAL: Không sync user " + user.getId()
                    + " xuống DB. RAM-DB lệch (balance/revenue): " + e.getMessage());
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

    /**
     * Persist auction state với log SEVERE nếu fail.
     *
     * <p>Method này được gọi từ {@link #internalObserver} sau khi state đã mutate
     * trong RAM (placeBid / extend / transitionTo). Nếu DB fail, RAM mới còn DB cũ.
     * notifyXxx() ở entity swallow exception nên ta KHÔNG throw — chỉ log loud để
     * devs nhìn thấy ngay. Fix triệt để cần move persistence ra khỏi observer + DI
     * vào explicit caller (BidManager / lifecycle scheduler).</p>
     */
    private void safeSave(Auction auction) {
        try {
            dao.update(auction);
        } catch (Exception e) {
            log.severe(() -> "CRITICAL: Không sync auction " + auction.getId()
                    + " xuống DB. RAM-DB lệch (status/price/totalBids/endTime): " + e.getMessage());
        }
    }

    private static void safeNotify(Runnable r) {
        try {
            r.run();
        } catch (Exception ignored) { }
    }

    // ============== TEST ONLY ==============

    void clearForTesting() {
        auctions.values().forEach(a -> a.removeObserver(internalObserver));
        auctions.clear();
        globalObservers.clear();
    }
}
