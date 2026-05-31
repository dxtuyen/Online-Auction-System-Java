package com.auction.service;

import com.auction.model.entity.Auction;
import com.auction.model.entity.AutoBid;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.exception.InsufficientBalanceException;
import com.auction.model.exception.InvalidBidException;
import com.auction.model.observer.AuctionObserver;
import com.auction.persistence.dao.AutoBidDao;
import com.auction.persistence.dao.BidTransactionDao;
import com.auction.persistence.dao.MysqlAutoBidDao;
import com.auction.persistence.dao.MysqlBidTransactionDao;
import com.auction.util.AppLogger;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public final class BidManager {

    private static final Logger log = AppLogger.get(BidManager.class);

    private static final class Holder {
        private static final BidManager INSTANCE = new BidManager();
    }

    public static BidManager getInstance() {
        return Holder.INSTANCE;
    }

    private final BidTransactionDao bidDao;
    private final AutoBidDao autoBidDao;

    private final ConcurrentHashMap<UUID, List<BidTransaction>> bidHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<AutoBid>> autoBidsByAuction = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> inAutoBid = ThreadLocal.withInitial(() -> false);

    private BidManager() {
        this.bidDao = new MysqlBidTransactionDao();
        this.autoBidDao = new MysqlAutoBidDao();

        AuctionManager.getInstance().addGlobalObserver(new AuctionObserver() {
            @Override
            public void onBidPlaced(Auction auction, BidTransaction bid) { }

            @Override
            public void onAuctionExtended(Auction auction, int seconds) { }

            @Override
            public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
                if (newStatus != AuctionStatus.RUNNING && newStatus != AuctionStatus.PENDING) {
                    List<AutoBid> list = autoBidsByAuction.get(auction.getId());
                    if (list != null) {
                        for (AutoBid ab : list) {
                            if (ab.isActive()) deactivateAutoBid(ab);
                        }
                    }
                }
            }
        });
    }

    public void loadAllFromDb() {
        bidHistory.clear();
        autoBidsByAuction.clear();

        for (BidTransaction bid : bidDao.findAll()) {
            bidHistory.computeIfAbsent(bid.getAuctionId(), k -> new CopyOnWriteArrayList<>())
                    .add(bid);
        }
        int totalBids = bidHistory.values().stream().mapToInt(List::size).sum();

        for (AutoBid ab : autoBidDao.findAll()) {
            autoBidsByAuction.computeIfAbsent(ab.getAuctionId(), k -> new CopyOnWriteArrayList<>())
                    .add(ab);
        }
        int totalAutoBids = autoBidsByAuction.values().stream().mapToInt(List::size).sum();

        log.info("Đã load " + totalBids + " bid + " + totalAutoBids + " auto-bid từ DB");
    }

    public Map<UUID, Set<UUID>> getParticipantsSnapshot() {
        Map<UUID, Set<UUID>> result = new HashMap<>();

        bidHistory.forEach((auctionId, bids) -> {
            Set<UUID> set = result.computeIfAbsent(auctionId, k -> new HashSet<>());
            for (BidTransaction bid : bids) {
                set.add(bid.getBidderId());
            }
        });

        autoBidsByAuction.forEach((auctionId, autoBids) -> {
            Set<UUID> set = result.computeIfAbsent(auctionId, k -> new HashSet<>());
            for (AutoBid autoBid : autoBids) {
                set.add(autoBid.getBidderId());
            }
        });

        return result;
    }

    public BidTransaction placeBid(UUID auctionId, UUID userId, BigDecimal amount) {
        Objects.requireNonNull(auctionId, "auctionId");
        Objects.requireNonNull(userId, "userId");

        Auction auction = AuctionManager.getInstance().findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy auction: " + auctionId));

        User user = UserManager.getInstance().findById(userId)
                .orElseThrow(() -> new InvalidBidException("User không tồn tại: " + userId));
        if (!user.canBid()) {
            throw new InvalidBidException("User không có quyền đấu giá (Tài khoản bị khóa)");
        }

        if (!user.tryReserve(amount)) {
            throw new InsufficientBalanceException(
                    "Số dư không đủ. Hiện có: " + user.getBalance() + ", muốn bid: " + amount);
        }

        try {
            UserManager.getInstance().save(user);
        } catch (RuntimeException e) {
            user.release(amount);
            throw e;
        }

        BidTransaction bid = new BidTransaction(auctionId, userId, amount);
        Auction.BidOutcome outcome;
        try {
            outcome = auction.placeBid(bid);
        } catch (RuntimeException e) {
            user.release(amount);
            persistUserBestEffort(user, "rollback reserve");
            throw e;
        }

        if (outcome.previousBidderId() != null) {
            UserManager.getInstance().findById(outcome.previousBidderId())
                    .ifPresent(prev -> {
                        prev.release(outcome.previousAmount());
                        persistUserBestEffort(prev, "refund previous bidder");
                    });
        }

        recordBid(auction, bid);
        tryTriggerAutoBids(auction, bid);
        return bid;
    }

    private void persistUserBestEffort(User user, String context) {
        try {
            UserManager.getInstance().save(user);
        } catch (RuntimeException e) {
            log.severe(() -> "CRITICAL: Không persist được user " + user.getId()
                    + " (" + context + "). RAM-DB lệch: " + e.getMessage());
        }
    }

    private void recordBid(Auction auction, BidTransaction bid) {
        bidDao.insert(bid);
        bidHistory.computeIfAbsent(auction.getId(), k -> new CopyOnWriteArrayList<>()).add(bid);
    }

    public List<BidTransaction> getBidHistory(UUID auctionId) {
        Objects.requireNonNull(auctionId);
        List<BidTransaction> list = bidHistory.get(auctionId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public AutoBid registerAutoBid(UUID auctionId, UUID bidderId,
                                   BigDecimal maxBid, BigDecimal increment) {
        Objects.requireNonNull(auctionId, "auctionId");
        Objects.requireNonNull(bidderId, "bidderId");

        Auction auction = AuctionManager.getInstance().findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy auction: " + auctionId));

        AuctionStatus status = auction.getStatus();
        if (status == AuctionStatus.FINISHED || status.isTerminal()) {
            throw new IllegalStateException(
                    "Phiên đang ở trạng thái " + status + ", không đăng ký auto-bid được");
        }
        if (auction.getSellerId().equals(bidderId)) {
            throw new IllegalArgumentException(
                    "Người bán không thể đăng ký auto-bid cho phiên của mình");
        }

        AutoBid newAb = new AutoBid(bidderId, auctionId, maxBid, increment);

        autoBidDao.deleteByBidderAndAuction(bidderId, auctionId);
        autoBidDao.insert(newAb);

        List<AutoBid> list = autoBidsByAuction.computeIfAbsent(
                auctionId, k -> new CopyOnWriteArrayList<>());
        list.removeIf(ab -> ab.getBidderId().equals(bidderId));
        list.add(newAb);
        return newAb;
    }

    public List<AutoBid> getAutoBids(UUID auctionId) {
        Objects.requireNonNull(auctionId);
        List<AutoBid> list = autoBidsByAuction.get(auctionId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    private void deactivateAutoBid(AutoBid ab) {
        ab.deactivate();
        try {
            autoBidDao.updateActive(ab.getBidderId(), ab.getAuctionId(), false);
        } catch (Exception e) {
            log.warning(() -> "Lỗi sync deactivate auto-bid ("
                    + ab.getBidderId() + "/" + ab.getAuctionId() + "): " + e.getMessage());
        }
    }

    private void tryTriggerAutoBids(Auction auction, BidTransaction triggerBid) {
        if (Boolean.TRUE.equals(inAutoBid.get())) return;

        inAutoBid.set(true);
        try {
            UUID lastBidder = triggerBid.getBidderId();
            while (true) {
                List<AutoBid> list = autoBidsByAuction.get(auction.getId());
                if (list == null || list.isEmpty()) return;

                AutoBid candidate = null;
                for (AutoBid ab : list) {
                    if (!ab.isActive()) continue;
                    if (ab.getBidderId().equals(lastBidder)) continue;
                    if (candidate == null
                            || ab.getMaxBid().compareTo(candidate.getMaxBid()) > 0) {
                        candidate = ab;
                    }
                }
                if (candidate == null) return;

                BigDecimal nextRequired = auction.minNextBid();
                if (nextRequired.compareTo(candidate.getMaxBid()) > 0) {
                    deactivateAutoBid(candidate);
                    return;
                }

                try {
                    placeBid(auction.getId(), candidate.getBidderId(), nextRequired);
                    lastBidder = candidate.getBidderId();
                } catch (Exception e) {
                    deactivateAutoBid(candidate);
                    return;
                }
            }
        } finally {
            inAutoBid.set(false);
        }
    }

    void clearForTesting() {
        bidHistory.clear();
        autoBidsByAuction.clear();
    }
}