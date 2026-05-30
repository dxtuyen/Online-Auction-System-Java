package com.auction.model.entity;

import com.auction.model.enums.BidStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class BidTransaction extends Entity {

    private static final long serialVersionUID = 1L;

    private final UUID auctionId;
    private final UUID bidderId;
    private final BigDecimal bidAmount;
    private BidStatus status;

    public BidTransaction(UUID auctionId, UUID bidderId, BigDecimal bidAmount) {
        super();
        this.auctionId = Objects.requireNonNull(auctionId, "auctionId must not be null");
        this.bidderId = Objects.requireNonNull(bidderId, "bidderId must not be null");
        this.bidAmount = validateAmount(bidAmount);
        this.status = BidStatus.PENDING;
    }

    public BidTransaction(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                          UUID auctionId, UUID bidderId, BigDecimal bidAmount,
                          BidStatus status) {
        super(id, createdAt, updatedAt);
        this.auctionId = Objects.requireNonNull(auctionId);
        this.bidderId = Objects.requireNonNull(bidderId);
        this.bidAmount = validateAmount(bidAmount);
        this.status = Objects.requireNonNull(status);
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getBidderId() {
        return bidderId;
    }

    public BigDecimal getBidAmount() {
        return bidAmount;
    }

    public BidStatus getStatus() {
        return status;
    }

    public LocalDateTime getTimestamp() {
        return getCreatedAt();
    }

    public void markValid() {
        changeStatus(BidStatus.VALID);
    }

    public void markOutbid() {
        changeStatus(BidStatus.OUTBID);
    }

    public void reject() {
        changeStatus(BidStatus.REJECTED);
    }

    public void cancel() {
        changeStatus(BidStatus.CANCELED);
    }

    private synchronized void changeStatus(BidStatus newStatus) {
        if (this.status == newStatus) return;
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Không thể chuyển bid từ " + status + " sang " + newStatus);
        }
        this.status = newStatus;
        markUpdated();
    }

    private static BigDecimal validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "bidAmount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("bidAmount phải > 0");
        }
        return amount;
    }

    @Override
    public String toString() {
        return "BidTransaction{" +
                "id=" + getId() +
                ", auctionId=" + auctionId +
                ", bidderId=" + bidderId +
                ", bidAmount=" + bidAmount +
                ", status=" + status +
                ", timestamp=" + getCreatedAt() +
                '}';
    }
}
