package com.auction.model;

import java.time.LocalDateTime;

/*
BidTransaction: chứa thông tin của giao dịch đặt giá trong phiên đấu giá
 */

public class BidTransaction extends Entity {

    private static final long serialVersionUID = 1L;

    private int auctionId; //id của phiên đấu giá
    private int bidderId; //id của người đặt giá
    private double bidAmount; //giá tiền người đó đặt
    private LocalDateTime timestamp; //thời gian đặt
    private boolean isValid; //tính hợp lệ

    public BidTransaction() {
        super();
    }

    public BidTransaction(int auctionId, int bidderId, double bidAmount) {
        super();
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
        this.isValid = true;
    }

    public BidTransaction(int id, int auctionId, int bidderId, double bidAmount, LocalDateTime timestamp, boolean isValid) {
        super(id);
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.timestamp = timestamp;
        this.isValid = isValid;
    }

    //Getter & Setter
    public int getauctionId() {
        return auctionId;
    }

    public void setauctionId(int auctionId) {
        this.auctionId = auctionId;
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

    //Methods
    @Override
    public String toString() {
        return "BidTransaction{" +
                "auctionId=" + auctionId +
                ", bidderId=" + bidderId +
                ", bidAmount=" + bidAmount +
                ", timestamp=" + timestamp +
                ", isValid=" + isValid +
                '}';
    }
}
