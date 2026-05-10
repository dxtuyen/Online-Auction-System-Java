package com.auction.service;

import com.auction.model.entity.Auction;
import com.auction.model.entity.AutoBid;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.exception.InsufficientBalanceException;
import com.auction.model.exception.InvalidBidException;
import com.auction.model.observer.AuctionObserver;

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
 * ============== DESIGN PATTERNS ÁP DỤNG ==============
 * <p>
 * 1. SINGLETON (Bill Pugh idiom)
 * <p>
 * 2. FACADE
 * - placeBid() che giấu việc validate user, tạo BidTransaction, gọi auction.placeBid(),
 *   ghi history và trigger auto-bid. Client chỉ cần biết 1 entry point.
 * <p>
 * 3. OBSERVER
 * - BidManager đăng ký làm global observer của AuctionManager để biết khi phiên đổi
 *   trạng thái mà tắt auto-bid tương ứng. KHÔNG dùng observer cho recording bid:
 *   placeBid() đã xử lý record trực tiếp sau khi auction.placeBid() trả về.
 * <p>
 * 3. REPOSITORY-LIKE
 * - getBidHistory, getAutoBids... giống Repository pattern.
 * <p>
 * ============== TẠI SAO TÁCH KHỎI AuctionManager? ==============
 * <p>
 * AuctionManager đã quản lý lifecycle phiên + thread-safety cho placeBid.
 * Bid history và auto-bid là các CONCERN riêng (audit log + behavior tự động).
 * Tách ra giúp:
 * - AuctionManager không phình to, vẫn focus vào phiên.
 * - Có thể swap implementation (vd lưu history xuống DB) mà không đụng AuctionManager.
 * <p>
 * ============== CONCURRENCY ==============
 * <p>
 * - ConcurrentHashMap + CopyOnWriteArrayList cho dữ liệu shared.
 * - {@link #inAutoBid} ThreadLocal flag để chặn đệ quy:
 * auto-bid trigger 1 bid mới → bid mới đó cũng đi qua observer này.
 * Nếu không guard sẽ bùng nổ stack/storm bid liên tục.
 * - active của AutoBid là volatile, có thể tắt từ trigger thread khác.
 */
public final class BidManager {

    // ============== SINGLETON (Bill Pugh idiom) ==============

    private static final class Holder {
        private static final BidManager INSTANCE = new BidManager();
    }

    public static BidManager getInstance() {
        return Holder.INSTANCE;
    }

    // ============== FIELDS ==============

    /** Map: auctionId → list bid theo thứ tự thời gian (CopyOnWrite vì đọc nhiều ghi ít). */
    private final ConcurrentHashMap<UUID, List<BidTransaction>> bidHistory = new ConcurrentHashMap<>();

    /** Map: auctionId → list AutoBid của mọi bidder đăng ký cho phiên đó. */
    private final ConcurrentHashMap<UUID, List<AutoBid>> autoBidsByAuction = new ConcurrentHashMap<>();

    /** Chặn đệ quy khi auto-bid tự kích hoạt auto-bid khác cùng thread. */
    private final ThreadLocal<Boolean> inAutoBid = ThreadLocal.withInitial(() -> false);

    // ============== CONSTRUCTOR ==============

    private BidManager() {
        // Chỉ subscribe status-change để tắt auto-bid khi phiên đóng. Việc record bid và
        // trigger auto-bid được placeBid() xử lý trực tiếp, không cần đi qua observer.
        AuctionManager.getInstance().addGlobalObserver(new AuctionObserver() {
            @Override
            public void onBidPlaced(Auction auction, BidTransaction bid) { /* no-op */ }

            @Override
            public void onAuctionExtended(Auction auction, int seconds) { /* no-op */ }

            @Override
            public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
                // Phiên rời khỏi RUNNING/PENDING → tắt auto-bid để tránh trigger sai khi
                // có bid muộn (rất hiếm) và để con người đọc state thấy auto-bid đã đóng.
                if (newStatus != AuctionStatus.RUNNING && newStatus != AuctionStatus.PENDING) {
                    List<AutoBid> list = autoBidsByAuction.get(auction.getId());
                    if (list != null) list.forEach(AutoBid::deactivate);
                }
            }
        });
    }

    // ============== BID OPERATIONS (FACADE) ==============

    /**
     * Đặt bid vào auction.
     * <p>
     * FACADE method - che giấu việc:
     * - Tìm auction qua AuctionManager
     * - Validate bidder (tồn tại, có quyền bid, đủ balance)
     * - Tạo BidTransaction
     * - Gọi {@link Auction#placeBid(BidTransaction)} (đã thread-safe ở tầng entity)
     * - Lưu history
     * - Trigger 1 vòng auto-bid
     * <p>
     * Anti-sniping vẫn được kích hoạt qua internalObserver của AuctionManager,
     * không bị ảnh hưởng bởi việc placeBid chuyển từ AuctionManager sang đây.
     *
     * @return BidTransaction đã được xử lý (status = VALID nếu thành công)
     * @throws IllegalArgumentException nếu auction không tồn tại
     * @throws InvalidBidException nếu bid không hợp lệ về business rule
     * @throws InsufficientBalanceException nếu bidder không đủ tiền
     * @throws com.auction.model.exception.AuctionClosedException nếu phiên đã đóng
     */
    public BidTransaction placeBid(UUID auctionId, UUID userId, BigDecimal amount) {
        Objects.requireNonNull(auctionId, "auctionId");
        Objects.requireNonNull(userId, "userId");

        Auction auction = AuctionManager.getInstance().findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy auction: " + auctionId));

        // Validate bidder TRƯỚC khi reserve → fail fast và không phải refund vô ích
        User user = UserManager.getInstance().findById(userId)
                .orElseThrow(() -> new InvalidBidException("User không tồn tại: " + userId));
        if (!user.canBid()) {
            throw new InvalidBidException("User không có quyền đấu giá (Tài khoản bị khóa)");
        }

        // RESERVE atomic check-and-deduct: chống user dùng cùng số tiền cho 2 bid song song.
        // Phải reserve TRƯỚC khi gọi auction.placeBid() vì sau đó balance đã giảm sẽ không bị
        // user khác "nẫng tay trên" qua thread khác.
        if (!user.tryReserve(amount)) {
            throw new InsufficientBalanceException(
                    "Số dư không đủ. Hiện có: " + user.getBalance() + ", muốn bid: " + amount);
        }

        BidTransaction bid = new BidTransaction(auctionId, userId, amount);
        Auction.BidOutcome outcome;
        try {
            outcome = auction.placeBid(bid);   // entity-level thread-safe, throws nếu không hợp lệ
        } catch (RuntimeException e) {
            // Bid bị reject (phiên đóng / giá không đủ / bidder là seller...) → rollback reservation
            user.release(amount);
            throw e;
        }

        // Bid hợp lệ → leader cũ được hoàn lại reservation. UserManager có thể không tìm thấy
        // user (đã bị xóa) → bỏ qua, tiền "treo" rất hiếm và không vỡ flow chính.
        if (outcome.previousBidderId() != null) {
            UserManager.getInstance().findById(outcome.previousBidderId())
                    .ifPresent(prev -> prev.release(outcome.previousAmount()));
        }

        recordBid(auction, bid);
        tryTriggerAutoBids(auction, bid);
        return bid;
    }

    // ============== HISTORY ==============

    private void recordBid(Auction auction, BidTransaction bid) {
        bidHistory.computeIfAbsent(auction.getId(), k -> new CopyOnWriteArrayList<>()).add(bid);
    }

    /**
     * Lấy lịch sử bid của 1 phiên - immutable view, sắp theo thời gian xuất hiện.
     */
    public List<BidTransaction> getBidHistory(UUID auctionId) {
        Objects.requireNonNull(auctionId);
        List<BidTransaction> list = bidHistory.get(auctionId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    // ============== AUTO-BID REGISTRATION ==============

    /**
     * Đăng ký auto-bid cho 1 user trên 1 phiên. Nếu user đã có auto-bid trước đó
     * cho phiên này → registration mới ghi đè.
     *
     * <p>Validate:
     * <ul>
     *   <li>Auction phải tồn tại và chưa kết thúc.</li>
     *   <li>Bidder không được là seller của phiên (chống tự đẩy giá).</li>
     *   <li>maxBid và increment > 0 (do AutoBid constructor validate).</li>
     * </ul>
     * </p>
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
        List<AutoBid> list = autoBidsByAuction.computeIfAbsent(
                auctionId, k -> new CopyOnWriteArrayList<>());
        // Replace registration cũ của cùng bidder để 1 user chỉ có 1 auto-bid mỗi phiên
        list.removeIf(ab -> ab.getBidderId().equals(bidderId));
        list.add(newAb);
        return newAb;
    }

    public List<AutoBid> getAutoBids(UUID auctionId) {
        Objects.requireNonNull(auctionId);
        List<AutoBid> list = autoBidsByAuction.get(auctionId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    // ============== AUTO-BID TRIGGER ==============

    /**
     * Sau mỗi bid hợp lệ → đẩy 1 vòng auto-bid (nếu có).
     * <p>
     * Chỉ chọn 1 candidate có {@code maxBid} cao nhất, không cùng bidder vừa đặt,
     * còn active. Nếu nextRequired vượt maxBid → deactivate luôn registration
     * (đối thủ đã vượt trần của user, auto-bid không còn nghĩa).
     * <p>
     * Vòng tiếp theo (nếu có) sẽ tự kích hoạt khi auto-bid này tạo bid mới
     * và quay lại chính observer này — nhưng {@link #inAutoBid} chặn re-entry
     * trong cùng thread, nên ta giới hạn 1 auto-bid trên mỗi chuỗi để tránh storm.
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
            candidate.deactivate();
            return;
        }

        inAutoBid.set(true);
        try {
            // Self-call: đi qua đầy đủ validate (status, balance, role) + record vào history
            placeBid(auction.getId(), candidate.getBidderId(), nextRequired);
        } catch (Exception e) {
            // Bidder bị banned / hết balance / phiên vừa đóng → deactivate, không crash hệ thống.
            candidate.deactivate();
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
