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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BidManager - REGISTRY trung tâm cho bid history và auto-bid.
 * <p>
 * ============== KIẾN TRÚC PERSISTENCE ==============
 * <p>
 * Write-through cache:
 * - bidHistory + autoBidsByAuction giữ in-memory để truy cập nhanh
 * - placeBid (sau khi thành công) → INSERT BidTransaction xuống DB rồi mới cache
 * - registerAutoBid → DELETE old + INSERT new (atomic ở app-level)
 * - {@link #deactivateAutoBid} wrapper sync DB khi deactivate
 * - loadAllFromDb fill cache lúc startup
 * <p>
 * ============== DESIGN PATTERNS ==============
 * 1. SINGLETON (Bill Pugh idiom)
 * 2. FACADE - placeBid che giấu validate user, tạo BidTransaction, gọi auction.placeBid,
 *    record history, trigger auto-bid
 * 3. OBSERVER - subscribe AuctionManager để biết khi phiên đóng → deactivate auto-bid
 * <p>
 * ============== CONCURRENCY ==============
 * - ConcurrentHashMap + CopyOnWriteArrayList cho dữ liệu shared
 * - {@link #inAutoBid} ThreadLocal flag chặn re-entry khi auto-bid trigger auto-bid
 * - active của AutoBid là volatile, có thể tắt từ thread khác
 */
public final class BidManager {

    // ============== SINGLETON ==============

    private static final class Holder {
        private static final BidManager INSTANCE = new BidManager();
    }

    public static BidManager getInstance() {
        return Holder.INSTANCE;
    }

    // ============== FIELDS ==============

    private final BidTransactionDao bidDao;
    private final AutoBidDao autoBidDao;

    /** Map: auctionId → list bid theo thứ tự thời gian. */
    private final ConcurrentHashMap<UUID, List<BidTransaction>> bidHistory = new ConcurrentHashMap<>();

    /** Map: auctionId → list AutoBid của mọi bidder đăng ký cho phiên đó. */
    private final ConcurrentHashMap<UUID, List<AutoBid>> autoBidsByAuction = new ConcurrentHashMap<>();

    /** Chặn re-entry khi auto-bid kích hoạt auto-bid trong cùng thread. */
    private final ThreadLocal<Boolean> inAutoBid = ThreadLocal.withInitial(() -> false);

    // ============== CONSTRUCTOR ==============

    private BidManager() {
        this.bidDao = new MysqlBidTransactionDao();
        this.autoBidDao = new MysqlAutoBidDao();

        // Khi phiên đóng → deactivate mọi auto-bid của phiên đó (cả cache + DB)
        AuctionManager.getInstance().addGlobalObserver(new AuctionObserver() {
            @Override
            public void onBidPlaced(Auction auction, BidTransaction bid) { /* no-op */ }

            @Override
            public void onAuctionExtended(Auction auction, int seconds) { /* no-op */ }

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

    // ============== BOOTSTRAP ==============

    /**
     * Load bid history + auto-bids từ DB. Phải gọi SAU AuctionManager.loadAllFromDb()
     * (FK auction_id, bidder_id).
     */
    public void loadAllFromDb() {
        bidHistory.clear();
        autoBidsByAuction.clear();

        // Bid history: load tất cả rồi group theo auctionId
        for (BidTransaction bid : bidDao.findAll()) {
            bidHistory.computeIfAbsent(bid.getAuctionId(), k -> new CopyOnWriteArrayList<>())
                    .add(bid);
        }
        int totalBids = bidHistory.values().stream().mapToInt(List::size).sum();

        // Auto-bids: tương tự
        for (AutoBid ab : autoBidDao.findAll()) {
            autoBidsByAuction.computeIfAbsent(ab.getAuctionId(), k -> new CopyOnWriteArrayList<>())
                    .add(ab);
        }
        int totalAutoBids = autoBidsByAuction.values().stream().mapToInt(List::size).sum();

        System.out.println("[BidManager] Đã load " + totalBids + " bid + "
                + totalAutoBids + " auto-bid từ DB");
    }

    // ============== BID OPERATIONS (FACADE) ==============

    /**
     * Đặt bid vào auction. Sau khi auction.placeBid() trả về thành công:
     * - INSERT bid xuống DB
     * - Add vào cache bidHistory
     * - Trigger 1 vòng auto-bid
     *
     * @return BidTransaction đã được xử lý (status = VALID)
     * @throws IllegalArgumentException nếu auction không tồn tại
     * @throws InvalidBidException nếu bid không hợp lệ
     * @throws InsufficientBalanceException nếu bidder không đủ tiền
     * @throws com.auction.model.exception.AuctionClosedException nếu phiên đã đóng
     */
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
        if (!user.hasEnoughBalance(amount)) {
            throw new InsufficientBalanceException(
                    "Số dư không đủ. Hiện có: " + user.getBalance() + ", muốn bid: " + amount);
        }

        BidTransaction bid = new BidTransaction(auctionId, userId, amount);
        auction.placeBid(bid);   // throws nếu invalid; bid status đã VALID nếu return

        recordBid(auction, bid);            // INSERT DB + cache
        tryTriggerAutoBids(auction, bid);
        return bid;
    }

    // ============== HISTORY ==============

    /** Persist bid TRƯỚC khi add cache. Nếu DB fail → bid không bị "mất" trong cache. */
    private void recordBid(Auction auction, BidTransaction bid) {
        bidDao.insert(bid);
        bidHistory.computeIfAbsent(auction.getId(), k -> new CopyOnWriteArrayList<>()).add(bid);
    }

    public List<BidTransaction> getBidHistory(UUID auctionId) {
        Objects.requireNonNull(auctionId);
        List<BidTransaction> list = bidHistory.get(auctionId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    // ============== AUTO-BID REGISTRATION ==============

    /**
     * Đăng ký auto-bid cho 1 user trên 1 phiên. Nếu đã có registration cũ → ghi đè.
     *
     * @throws IllegalArgumentException nếu auction không tồn tại hoặc bidder là seller
     * @throws IllegalStateException    nếu phiên đã đóng
     */
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

        // DELETE old + INSERT new ở DB. Composite PK (bidder_id, auction_id) đảm bảo
        // không có 2 row trùng. Nếu DELETE/INSERT fail → throw, cache không thay đổi.
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

    /** Wrapper: deactivate cache + sync DB. Mọi nơi muốn deactivate auto-bid PHẢI gọi method này. */
    private void deactivateAutoBid(AutoBid ab) {
        ab.deactivate();
        try {
            autoBidDao.updateActive(ab.getBidderId(), ab.getAuctionId(), false);
        } catch (Exception e) {
            System.err.println("[BidManager] Lỗi sync deactivate auto-bid ("
                    + ab.getBidderId() + "/" + ab.getAuctionId() + "): " + e.getMessage());
        }
    }

    // ============== AUTO-BID TRIGGER ==============

    /**
     * Sau mỗi bid hợp lệ → đẩy 1 vòng auto-bid (nếu có).
     */
    private void tryTriggerAutoBids(Auction auction, BidTransaction triggerBid) {
        if (Boolean.TRUE.equals(inAutoBid.get())) return;

        List<AutoBid> list = autoBidsByAuction.get(auction.getId());
        if (list == null || list.isEmpty()) return;

        AutoBid candidate = null;
        for (AutoBid ab : list) {
            if (!ab.isActive()) continue;
            if (ab.getBidderId().equals(triggerBid.getBidderId())) continue;
            if (candidate == null || ab.getMaxBid().compareTo(candidate.getMaxBid()) > 0) {
                candidate = ab;
            }
        }
        if (candidate == null) return;

        BigDecimal nextRequired = auction.minNextBid();
        if (nextRequired.compareTo(candidate.getMaxBid()) > 0) {
            deactivateAutoBid(candidate);
            return;
        }

        inAutoBid.set(true);
        try {
            placeBid(auction.getId(), candidate.getBidderId(), nextRequired);
        } catch (Exception e) {
            // Bidder bị banned / hết balance / phiên vừa đóng → deactivate
            deactivateAutoBid(candidate);
        } finally {
            inAutoBid.set(false);
        }
    }

    // ============== TEST ONLY ==============

    void clearForTesting() {
        bidHistory.clear();
        autoBidsByAuction.clear();
    }
}
