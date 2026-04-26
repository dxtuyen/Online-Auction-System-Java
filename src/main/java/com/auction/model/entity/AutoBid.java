package com.auction.model.entity;

import java.time.LocalDateTime;

/**
 * Auto-bid: user đăng ký "trả giá tự động" cho 1 phiên đấu giá.
 *
 * <p>Khi có đối thủ bid → hệ thống tự động bid thay cho user,
 * tăng theo {@code increment} nhưng không vượt {@code maxBid}.</p>
 */
public class AutoBid {

    private final int bidderId;
    private final int auctionId;
    private final double maxBid;           // trần giá
    private final double increment;         // bước nhảy
    private final LocalDateTime createdAt;  // để sắp xếp ưu tiên
    private boolean active;                 // khi đã chạm maxBid → vô hiệu

    public AutoBid(int bidderId, int auctionId, double maxBid, double increment) {
        this.bidderId = bidderId;
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public int getBidderId()         { return bidderId; }
    public int getAuctionId()        { return auctionId; }
    public double getMaxBid()        { return maxBid; }
    public double getIncrement()     { return increment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isActive()        { return active; }
    public void deactivate()         { this.active = false; }
}
