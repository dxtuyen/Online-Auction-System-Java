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

    private static final class Holder {
        private static final AuctionManager INSTANCE = new AuctionManager();
    }

    public static AuctionManager getInstance() {
        return Holder.INSTANCE;
    }

    private static final int AUTO_PAY_AFTER_MINUTES = 5;

    private static final BigDecimal FORFEIT_PENALTY_RATE = new BigDecimal("0.4");

    private final AuctionDao dao;
    private final ConcurrentHashMap<UUID, Auction> auctions = new ConcurrentHashMap<>();
    private final List<AuctionObserver> globalObservers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;

    private volatile int snipingThresholdSeconds;
    private volatile int snipingExtensionSeconds;

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

    AuctionManager(AuctionDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao must not be null");
        this.snipingThresholdSeconds = 10;
        this.snipingExtensionSeconds = 30;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuctionManager-Test-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

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

        if (penalty.compareTo(reserved) > 0) penalty = reserved;
        BigDecimal refund = reserved.subtract(penalty);

        User winner = UserManager.getInstance().findById(userId)
                .orElseThrow(() -> new IllegalStateException("Winner không tồn tại"));
        User seller = UserManager.getInstance().findById(auction.getSellerId())
                .orElseThrow(() -> new IllegalStateException("Seller không tồn tại"));

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

    public void addGlobalObserver(AuctionObserver observer) {
        globalObservers.add(Objects.requireNonNull(observer));
    }

    public void removeGlobalObserver(AuctionObserver observer) {
        globalObservers.remove(observer);
    }

    public void configureAntiSniping(int thresholdSeconds, int extensionSeconds) {
        if (thresholdSeconds < 0 || extensionSeconds <= 0) {
            throw new IllegalArgumentException("Thông số anti-sniping không hợp lệ");
        }
        this.snipingThresholdSeconds = thresholdSeconds;
        this.snipingExtensionSeconds = extensionSeconds;
    }

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

                    if (auction.getHighestBidderId() == null) {
                        auction.transitionTo(AuctionStatus.CANCELED);
                    } else {
                        auction.transitionTo(AuctionStatus.FINISHED);
                    }
                } else if (status == AuctionStatus.FINISHED
                        && auction.getHighestBidderId() != null
                        && auction.getUpdatedAt().plusMinutes(AUTO_PAY_AFTER_MINUTES).isBefore(now)) {

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

    private void settleAsPaid(Auction auction) {
        BigDecimal price = auction.getCurrentPrice();

        User seller = UserManager.getInstance().findById(auction.getSellerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Seller không tồn tại: " + auction.getSellerId()));

        auction.transitionTo(AuctionStatus.PAID);

        seller.addRevenue(price);
        safeSaveUser(seller);
    }

    private void safeSaveUser(User user) {
        try {
            UserManager.getInstance().save(user);
        } catch (Exception e) {
            log.severe(() -> "CRITICAL: Không sync user " + user.getId()
                    + " xuống DB. RAM-DB lệch (balance/revenue): " + e.getMessage());
        }
    }

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

    void clearForTesting() {
        auctions.values().forEach(a -> a.removeObserver(internalObserver));
        auctions.clear();
        globalObservers.clear();
    }
}
