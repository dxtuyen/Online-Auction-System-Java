package com.auction.model.entity;

import com.auction.model.enums.BidStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/*
BidTransaction: chứa thông tin của giao dịch đặt giá trong phiên đấu giá
 */

public class BidTransaction extends Entity {

    private static final long serialVersionUID = 1L;

    private final UUID auctionId; //id của phiên đấu giá
    private final UUID bidderId; //id của người đặt giá
    private final BigDecimal bidAmount; //giá tiền người đó đặt
    private final LocalDateTime timestamp; //thời gian đặt
    private BidStatus status; //tính hợp lệ

    // CREATE NEW BID
    public BidTransaction(UUID auctionId, UUID bidderId, BigDecimal bidAmount) {
        super();

        this.auctionId = Objects.requireNonNull(auctionId, "auctionId must not be null");
        this.bidderId = Objects.requireNonNull(bidderId, "bidderId must not be null");
        this.bidAmount = validateAmount(bidAmount);

        this.timestamp = LocalDateTime.now();
        this.status = BidStatus.PENDING;
    }

    // load từ database, các thông báo khi gặp lỗi sẽ thêm sau
    public BidTransaction(
            UUID id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            UUID auctionId,
            UUID bidderId,
            BigDecimal bidAmount,
            LocalDateTime timestamp,
            BidStatus status
    ) {
        super(
                Objects.requireNonNull(id),
                Objects.requireNonNull(createdAt),
                Objects.requireNonNull(updatedAt)
        );

        this.auctionId = Objects.requireNonNull(auctionId);
        this.bidderId = Objects.requireNonNull(bidderId);
        this.bidAmount = validateAmount(bidAmount);
        this.timestamp = Objects.requireNonNull(timestamp);
        this.status = Objects.requireNonNull(status);
    }

    //Getter
    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getBidderId() {
        return bidderId;
    }

    public BigDecimal getBidAmount() {
        return bidAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public BidStatus getStatus() {
        return status;
    }

    // Valid
    private void changeStatus(BidStatus newStatus) {
        if (this.status == newStatus) return;

        if (!canTransition(this.status, newStatus)) {
            throw new IllegalStateException(
                    "Invalid status transition: " + this.status + " -> " + newStatus
            );
        }

        this.status = newStatus;
        markUpdated();
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
        changeStatus(BidStatus.CANCELLED);
    }

    //Methods
    private boolean canTransition(BidStatus from, BidStatus to) {
        return switch (from) {
            case PENDING -> to == BidStatus.VALID || to == BidStatus.REJECTED || to == BidStatus.CANCELLED;
            case VALID -> to == BidStatus.OUTBID || to == BidStatus.CANCELLED;
            case OUTBID -> to == BidStatus.CANCELLED;
            case REJECTED -> false;
            case CANCELLED -> false;
        };
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("bidAmount must be > 0");
        }
        return amount;
    }

    @Override
    public String toString() {
        return "BidTransaction{" +
                "auctionId=" + auctionId +
                ", bidderId=" + bidderId +
                ", bidAmount=" + bidAmount +
                ", timestamp=" + timestamp +
                ", status=" + status +
                '}';
    }
}
