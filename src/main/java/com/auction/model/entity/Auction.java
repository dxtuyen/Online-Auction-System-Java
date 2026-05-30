package com.auction.model.entity;

import com.auction.model.exception.AuctionClosedException;
import com.auction.model.exception.IllegalAuctionStateException;
import com.auction.model.exception.InvalidBidException;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.observer.AuctionObserver;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class Auction extends Entity {

    private static final long serialVersionUID = 1L;

    private final UUID itemId;
    private final UUID sellerId;
    private final LocalDateTime startTime;
    private final BigDecimal startingPrice;
    private final BigDecimal minimumIncrement;

    private LocalDateTime endTime;
    private BigDecimal currentPrice;
    private UUID highestBidderId;
    private AuctionStatus status;
    private int totalBids;

    private transient ReentrantLock lock = new ReentrantLock();
    private transient List<AuctionObserver> observers = new CopyOnWriteArrayList<>();

    public Auction(UUID itemId, UUID sellerId,
                   LocalDateTime startTime, LocalDateTime endTime,
                   BigDecimal startingPrice, BigDecimal minimumIncrement) {
        super();
        this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
        validateTimeRange(startTime, endTime);
        this.startTime = startTime;
        this.endTime = endTime;
        this.startingPrice = validateNonNegative(startingPrice, "startingPrice");
        this.minimumIncrement = validatePositive(minimumIncrement, "minimumIncrement");
        this.currentPrice = startingPrice;
        this.highestBidderId = null;
        this.status = AuctionStatus.PENDING;
        this.totalBids = 0;
    }

    public Auction(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                   UUID itemId, UUID sellerId,
                   LocalDateTime startTime, LocalDateTime endTime,
                   BigDecimal startingPrice, BigDecimal currentPrice,
                   BigDecimal minimumIncrement, UUID highestBidderId,
                   AuctionStatus status, int totalBids) {
        super(id, createdAt, updatedAt);
        this.itemId = Objects.requireNonNull(itemId);
        this.sellerId = Objects.requireNonNull(sellerId);
        validateTimeRange(startTime, endTime);
        this.startTime = startTime;
        this.endTime = endTime;
        this.startingPrice = validateNonNegative(startingPrice, "startingPrice");
        this.currentPrice = validateNonNegative(currentPrice, "currentPrice");
        this.minimumIncrement = validatePositive(minimumIncrement, "minimumIncrement");
        this.highestBidderId = highestBidderId;
        this.status = Objects.requireNonNull(status);
        if (totalBids < 0) throw new IllegalArgumentException("totalBids phải >= 0");
        this.totalBids = totalBids;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getMinimumIncrement() {
        return minimumIncrement;
    }

    public UUID getHighestBidderId() {
        return highestBidderId;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public int getTotalBids() {
        return totalBids;
    }

    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return status == AuctionStatus.RUNNING
                && !now.isBefore(startTime)
                && now.isBefore(endTime);
    }

    public long getRemainingSeconds() {
        if (!isActive()) return 0;
        long sec = Duration.between(LocalDateTime.now(), endTime).getSeconds();
        return Math.max(0, sec);
    }

    public BigDecimal minNextBid() {
        return totalBids == 0 ? startingPrice : currentPrice.add(minimumIncrement);
    }

    public boolean isInSnipingWindow(int snipingSeconds) {
        if (!isActive()) return false;
        return getRemainingSeconds() <= snipingSeconds;
    }

    public record BidOutcome(UUID previousBidderId, BigDecimal previousAmount) {}

    public BidOutcome placeBid(BidTransaction bid) {
        Objects.requireNonNull(bid, "bid must not be null");
        if (!bid.getAuctionId().equals(getId())) {
            throw new InvalidBidException("Bid không thuộc phiên này");
        }

        UUID prevBidderId;
        BigDecimal prevAmount;
        boolean hadPrevious;

        lock.lock();
        try {

            if (!isActive()) {
                bid.reject();
                throw new AuctionClosedException(
                        "Phiên đấu giá không mở (status=" + status + ")");
            }

            if (sellerId.equals(bid.getBidderId())) {
                bid.reject();
                throw new InvalidBidException("Bạn không thể đấu giá phiên của chính mình");
            }

            BigDecimal required = minNextBid();
            if (bid.getBidAmount().compareTo(required) < 0) {
                bid.reject();
                throw new InvalidBidException(
                        "Giá đấu phải >= " + required + " (hiện tại: " + bid.getBidAmount() + ")");
            }

            hadPrevious = this.totalBids > 0;
            prevBidderId = this.highestBidderId;
            prevAmount = this.currentPrice;

            this.currentPrice = bid.getBidAmount();
            this.highestBidderId = bid.getBidderId();
            this.totalBids++;
            bid.markValid();
            markUpdated();

        } finally {
            lock.unlock();
        }

        notifyBidPlaced(bid);
        return hadPrevious
                ? new BidOutcome(prevBidderId, prevAmount)
                : new BidOutcome(null, null);
    }

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

    public void addObserver(AuctionObserver observer) {
        Objects.requireNonNull(observer);
        observers.add(observer);
    }

    public void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    private void notifyBidPlaced(BidTransaction bid) {
        for (AuctionObserver obs : observers) {
            try {
                obs.onBidPlaced(this, bid);
            } catch (Exception ignored) {  }
        }
    }

    private void notifyAuctionExtended(int seconds) {
        for (AuctionObserver obs : observers) {
            try {
                obs.onAuctionExtended(this, seconds);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyStatusChanged(AuctionStatus oldS, AuctionStatus newS) {
        for (AuctionObserver obs : observers) {
            try {
                obs.onStatusChanged(this, oldS, newS);
            } catch (Exception ignored) {
            }
        }
    }

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
