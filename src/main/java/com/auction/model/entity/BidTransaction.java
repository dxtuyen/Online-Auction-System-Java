package com.auction.model.entity;

import java.time.LocalDateTime;

public class BidTransaction extends Entity {

    private static final long serialVersionUID = 1L;

    private int auctionSessionId;
    private int bidderId;
    private double bidAmount;
    private LocalDateTime timestamp;
    private boolean isValid;

    public BidTransaction() {
        super();
    }

    public BidTransaction(int auctionSessionId, int bidderId, double bidAmount) {
        super();
        this.auctionSessionId = auctionSessionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
        this.isValid = true;
    }

    public BidTransaction(int id, int auctionSessionId, int bidderId, double bidAmount, LocalDateTime timestamp, boolean isValid) {
        super(id);
        this.auctionSessionId = auctionSessionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.timestamp = timestamp;
        this.isValid = isValid;
    }

    //Getter & Setter
    public int getAuctionSessionId() {
        return auctionSessionId;
    }

    public void setAuctionSessionId(int auctionSessionId) {
        this.auctionSessionId = auctionSessionId;
    }

    public int getBidderId() {
        return bidderId;
    }

    public void setBidderId(int bidderId) {
        this.bidderId = bidderId;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(double bidAmount) {
        this.bidAmount = bidAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }
}
