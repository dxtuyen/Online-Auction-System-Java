package com.auction.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class AutoBid {

    private final UUID bidderId;
    private final UUID auctionId;
    private final BigDecimal maxBid;
    private final BigDecimal increment;
    private final LocalDateTime createdAt;
    private volatile boolean active;

    public AutoBid(UUID bidderId, UUID auctionId, BigDecimal maxBid, BigDecimal increment) {
        this.bidderId = Objects.requireNonNull(bidderId, "bidderId must not be null");
        this.auctionId = Objects.requireNonNull(auctionId, "auctionId must not be null");
        this.maxBid = validatePositive(maxBid, "maxBid");
        this.increment = validatePositive(increment, "increment");
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public AutoBid(UUID bidderId, UUID auctionId, BigDecimal maxBid, BigDecimal increment,
                   LocalDateTime createdAt, boolean active) {
        this.bidderId = Objects.requireNonNull(bidderId, "bidderId must not be null");
        this.auctionId = Objects.requireNonNull(auctionId, "auctionId must not be null");
        this.maxBid = validatePositive(maxBid, "maxBid");
        this.increment = validatePositive(increment, "increment");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.active = active;
    }

    public UUID getBidderId()           { return bidderId; }
    public UUID getAuctionId()          { return auctionId; }
    public BigDecimal getMaxBid()       { return maxBid; }
    public BigDecimal getIncrement()    { return increment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isActive()           { return active; }
    public void deactivate()            { this.active = false; }

    private static BigDecimal validatePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(field + " phải > 0");
        }
        return value;
    }
}
