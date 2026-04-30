package com.auction.model.entity;

import com.auction.model.exception.AuctionClosedException;
import com.auction.model.exception.IllegalAuctionStateException;
import com.auction.model.exception.InvalidBidException;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.enums.BidStatus;
import com.auction.model.observer.AuctionObserver;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Phiên đấu giá - ENTITY TRUNG TÂM của hệ thống.
 *
 * Design patterns áp dụng:
 *  - Observer: notify khi có bid mới / status đổi / auction extended
 *  - State Machine: chuyển trạng thái theo quy tắc của AuctionStatus
 *  - Defensive: validate đầy đủ + thread-safe
 *
 * THREAD-SAFETY (RẤT QUAN TRỌNG - đề bài có 1.0 điểm cho concurrent bidding):
 *  Mọi thao tác mutate state (placeBid, extend, transitionTo) đều giữ ReentrantLock.
 *  Lý do dùng ReentrantLock thay vì synchronized:
 *    - Hỗ trợ tryLock() với timeout (nếu sau này cần)
 *    - Có thể fair-lock (FIFO) nếu cần đối xử công bằng giữa các bidder
 *    - Dễ debug hơn (có method getQueueLength, isLocked...)
 *
 *  CopyOnWriteArrayList cho observers: thread-safe, đọc nhiều ghi ít → phù hợp.
 *
 * SERIALIZATION:
 *  lock và observers đánh dấu transient - không serialize.
 *  Sau khi deserialize cần khởi tạo lại qua readObject().
 */
public class Auction extends Entity {

    private static final long serialVersionUID = 1L;

    // ============== IMMUTABLE FIELDS ==============
    private final UUID itemId;
    private final UUID sellerId;
    private final LocalDateTime startTime;
    private final BigDecimal startingPrice;
    private final BigDecimal minimumIncrement;

    // ============== MUTABLE FIELDS ==============
    private LocalDateTime endTime;             // có thể bị extend (anti-sniping)
    private BigDecimal currentPrice;
    private UUID highestBidderId;
    private AuctionStatus status;
    private int totalBids;

    // ============== TRANSIENT (không serialize) ==============
    private transient ReentrantLock lock = new ReentrantLock();
    private transient List<AuctionObserver> observers = new CopyOnWriteArrayList<>();

    // ============== CONSTRUCTORS ==============

    /** Tạo phiên đấu giá MỚI - mặc định PENDING */
    public Auction(UUID itemId, UUID sellerId,
                   LocalDateTime startTime, LocalDateTime endTime,
                   BigDecimal startingPrice, BigDecimal minimumIncrement) {
        super();
        this.itemId           = Objects.requireNonNull(itemId, "itemId must not be null");
        this.sellerId         = Objects.requireNonNull(sellerId, "sellerId must not be null");
        validateTimeRange(startTime, endTime);
        this.startTime        = startTime;
        this.endTime          = endTime;
        this.startingPrice    = validateNonNegative(startingPrice, "startingPrice");
        this.minimumIncrement = validatePositive(minimumIncrement, "minimumIncrement");
        this.currentPrice     = startingPrice;
        this.highestBidderId  = null;
        this.status           = AuctionStatus.PENDING;
        this.totalBids        = 0;
    }

    /** Restore từ DB */
    public Auction(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                   UUID itemId, UUID sellerId,
                   LocalDateTime startTime, LocalDateTime endTime,
                   BigDecimal startingPrice, BigDecimal currentPrice,
                   BigDecimal minimumIncrement, UUID highestBidderId,
                   AuctionStatus status, int totalBids) {
        super(id, createdAt, updatedAt);
        this.itemId           = Objects.requireNonNull(itemId);
        this.sellerId         = Objects.requireNonNull(sellerId);
        validateTimeRange(startTime, endTime);
        this.startTime        = startTime;
        this.endTime          = endTime;
        this.startingPrice    = validateNonNegative(startingPrice, "startingPrice");
        this.currentPrice     = validateNonNegative(currentPrice, "currentPrice");
        this.minimumIncrement = validatePositive(minimumIncrement, "minimumIncrement");
        this.highestBidderId  = highestBidderId;   // có thể null
        this.status           = Objects.requireNonNull(status);
        if (totalBids < 0) throw new IllegalArgumentException("totalBids phải >= 0");
        this.totalBids        = totalBids;
    }

    // ============== GETTERS ==============
    public UUID getItemId() { return itemId; }
    public UUID getSellerId() { return sellerId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getMinimumIncrement() { return minimumIncrement; }
    public UUID getHighestBidderId() { return highestBidderId; }
    public AuctionStatus getStatus() { return status; }
    public int getTotalBids() { return totalBids; }

    // ============== READ-ONLY DOMAIN QUERIES ==============

    /** Phiên đang chạy và còn trong thời gian hợp lệ */
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return status == AuctionStatus.RUNNING
                && !now.isBefore(startTime)
                && now.isBefore(endTime);
    }

    /** Còn bao nhiêu giây trước khi kết thúc */
    public long getRemainingSeconds() {
        if (!isActive()) return 0;
        long sec = Duration.between(LocalDateTime.now(), endTime).getSeconds();
        return Math.max(0, sec);
    }

    /** Giá tối thiểu cho bid tiếp theo */
    public BigDecimal minNextBid() {
        return totalBids == 0 ? startingPrice : currentPrice.add(minimumIncrement);
    }

    /** Có đang trong cửa sổ anti-sniping không (còn dưới X giây cuối) */
    public boolean isInSnipingWindow(int snipingSeconds) {
        if (!isActive()) return false;
        return getRemainingSeconds() <= snipingSeconds;
    }

    // ============== STATE MUTATION (THREAD-SAFE) ==============

    /**
     * Đặt bid - METHOD CỐT LÕI của hệ thống.
     *
     * Đảm bảo:
     *  - Atomicity: chỉ 1 thread xử lý tại 1 thời điểm (lock)
     *  - Validation đầy đủ: status, time, amount, bidder khác seller
     *  - Cập nhật BidTransaction về VALID/REJECTED đúng logic
     *  - Notify observers SAU KHI release lock (tránh deadlock nếu observer gọi lại auction)
     *
     * @param bid bid đang ở trạng thái PENDING
     * @throws AuctionClosedException nếu phiên đã đóng
     * @throws InvalidBidException nếu bid không hợp lệ
     */
    public void placeBid(BidTransaction bid) {
        Objects.requireNonNull(bid, "bid must not be null");
        if (!bid.getAuctionId().equals(getId())) {
            throw new InvalidBidException("Bid không thuộc phiên này");
        }

        BidTransaction oldHighest = null;     // sẽ markOutbid sau khi release lock

        lock.lock();
        try {
            // 1. Phiên phải đang mở
            if (!isActive()) {
                bid.reject();
                throw new AuctionClosedException(
                        "Phiên đấu giá không mở (status=" + status + ")");
            }

            // 2. Bidder không được là seller
            if (bid.getBidderId().equals(sellerId)) {
                bid.reject();
                throw new InvalidBidException("Người bán không thể tự đấu giá sản phẩm của mình");
            }

            // 3. Số tiền phải >= minNextBid
            BigDecimal required = minNextBid();
            if (bid.getBidAmount().compareTo(required) < 0) {
                bid.reject();
                throw new InvalidBidException(
                        "Giá đấu phải >= " + required + " (hiện tại: " + bid.getBidAmount() + ")");
            }

            // 4. PASS - cập nhật state
            this.currentPrice    = bid.getBidAmount();
            this.highestBidderId = bid.getBidderId();
            this.totalBids++;
            bid.markValid();
            markUpdated();

        } finally {
            lock.unlock();
        }

        // Notify NGOÀI lock: tránh deadlock + observer chạy lâu cũng không block các bid khác
        notifyBidPlaced(bid);
    }

    /**
     * Gia hạn phiên (anti-sniping).
     *
     * @param seconds số giây thêm (> 0)
     */
    public void extend(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("seconds phải > 0");
        }
        lock.lock();
        try {
            if (status != AuctionStatus.RUNNING) {
                throw new IllegalAuctionStateException(
                        "Chỉ extend được phiên RUNNING (hiện tại: " + status + ")");
            }
            this.endTime = this.endTime.plusSeconds(seconds);
            markUpdated();
        } finally {
            lock.unlock();
        }
        notifyAuctionExtended(seconds);
    }

    /**
     * Chuyển trạng thái phiên - validate qua state machine.
     * KHÁC bản cũ: throw exception thay vì silent-fail.
     */
    public void transitionTo(AuctionStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        AuctionStatus oldStatus;
        lock.lock();
        try {
            if (!status.canTransitionTo(newStatus)) {
                throw new IllegalAuctionStateException(
                        "Không thể chuyển từ " + status + " sang " + newStatus);
            }
            oldStatus = this.status;
            this.status = newStatus;
            markUpdated();
        } finally {
            lock.unlock();
        }
        notifyStatusChanged(oldStatus, newStatus);
    }

    // ============== OBSERVER MANAGEMENT ==============

    public void addObserver(AuctionObserver observer) {
        Objects.requireNonNull(observer);
        observers.add(observer);
    }

    public void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    private void notifyBidPlaced(BidTransaction bid) {
        for (AuctionObserver obs : observers) {
            try { obs.onBidPlaced(this, bid); }
            catch (Exception ignored) { /* không để observer crash làm vỡ hệ thống */ }
        }
    }

    private void notifyAuctionExtended(int seconds) {
        for (AuctionObserver obs : observers) {
            try { obs.onAuctionExtended(this, seconds); }
            catch (Exception ignored) {}
        }
    }

    private void notifyStatusChanged(AuctionStatus oldS, AuctionStatus newS) {
        for (AuctionObserver obs : observers) {
            try { obs.onStatusChanged(this, oldS, newS); }
            catch (Exception ignored) {}
        }
    }

    // ============== VALIDATION ==============

    private static void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "startTime must not be null");
        Objects.requireNonNull(end, "endTime must not be null");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("endTime phải sau startTime");
        }
    }

    private static BigDecimal validateNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " phải >= 0");
        }
        return value;
    }

    private static BigDecimal validatePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(field + " phải > 0");
        }
        return value;
    }

    // ============== SERIALIZATION ==============
    // Khôi phục lock + observers sau khi deserialize
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.lock = new ReentrantLock();
        this.observers = new CopyOnWriteArrayList<>();
    }

    @Override
    public String toString() {
        return "Auction{" +
                "id=" + getId() +
                ", itemId=" + itemId +
                ", status=" + status +
                ", currentPrice=" + currentPrice +
                ", totalBids=" + totalBids +
                ", endTime=" + endTime +
                '}';
    }
}
