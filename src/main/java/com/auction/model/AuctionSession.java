package com.auction.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AuctionSession extends Entity {

    private static final long serialVersionUID = 1L;

    private int itemId;
    private int sellerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double startingPrice;
    private double currentPrice;
    private Integer highestBidderId;
    private double minimumIncrement;
    private AuctionStatus status;
    private int totalBids;

    public AuctionSession() {
        super();
    }

    public AuctionSession(int itemId, int sellerId, LocalDateTime startTime, LocalDateTime endTime, double startingPrice, double minimumIncrement) {
        super();
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.highestBidderId = null;
        this.minimumIncrement = minimumIncrement;
        this.status = AuctionStatus.PENDING;
        this.totalBids = 0;
    }

    public  AuctionSession(int id, int itemId, int sellerId, LocalDateTime startTime, LocalDateTime endTime, double startingPrice, double currentPrice, int highestBidderId, double minimumIncrement, AuctionStatus status, int totalBids) {
        super(id);
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.highestBidderId = highestBidderId;
        this.minimumIncrement = minimumIncrement;
        this.status = status;
        this.totalBids = totalBids;
    }
    
    //Getter & Setter
    public int getItemId() {
        return itemId;
    }
    
    public void setItemId(int itemId) {
        this.itemId = itemId;
    }
    
    public int getSellerId() {
        return sellerId;
    }
    
    public void setSellerId(int sellerId) {
        this.sellerId = sellerId;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public int getHighestBidderID() {
        return highestBidderId;
    }

    public void setHighestBidderId(int highestBidderId) {
        this.highestBidderId = highestBidderId;
    }

    public double getMinimumIncrement() {
        return minimumIncrement;
    }
    public void setMinimumBidIncrement(double minimumIncrement) {
        this.minimumIncrement = minimumIncrement;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public int getTotalBids() {
        return totalBids;
    }

    public void setTotalBids(int totalBids) {
        this.totalBids = totalBids;
    }

    public void incrementTotalBids() {
        this.totalBids++;
    }

    //Methods
    public boolean isActive() {
        return status == AuctionStatus.RUNNING && LocalDateTime.now().isAfter(startTime) && LocalDateTime.now().isBefore(endTime);
    }

    public boolean snipingCheck(int snipingSeconds) {
        if (!isActive()) return false;
        LocalDateTime now = LocalDateTime.now();
        return now.plusSeconds(snipingSeconds).isAfter(endTime);
    }

    public void extend(int seconds) {
        this.endTime = this.endTime.plusSeconds(seconds);
    }

    public long getRemainedSeconds() {
        if (!isActive()) return 0;
        return java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
    }

    public void transitionTo(AuctionStatus newStatus) {
        if (status.canTransitionTo(newStatus)) {
            this.status = newStatus;
        }
    }
}
