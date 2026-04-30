package com.auction.model.entity;

import com.auction.model.enums.AuctionStatus;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class Auction implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID id;
    private UUID sellerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double initialPrice;
    private double currentPrice;
    private UUID highestBidderId;
    private double minimumIncrement;
    private AuctionStatus status;

    public Auction(UUID sellerId, LocalDateTime startTime, LocalDateTime endTime, double initialPrice, double currentPrice, UUID highestBidderId, double minimumIncrement) {
        this.id = UUID.randomUUID();
        this.startTime = startTime;
        this.endTime = endTime;
        this.initialPrice = initialPrice;
        this.currentPrice = currentPrice;
        this.highestBidderId = highestBidderId;
        this.minimumIncrement = minimumIncrement;
    }

    public Auction(UUID id, UUID sellerId, LocalDateTime startTime, LocalDateTime endTime, double initialPrice, double currentPrice, UUID highestBidderId, double minimumIncrement) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.initialPrice = initialPrice;
        this.currentPrice = currentPrice;
        this.highestBidderId = highestBidderId;
        this.minimumIncrement = minimumIncrement;
    }

    //Getter
    public UUID getId() {
        return id;
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

    public double getInitialPrice() {
        return initialPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public UUID getHighestBidderId() {
        return highestBidderId;
    }

    public double getMinimumIncrement() {
        return minimumIncrement;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    //Setter
    public void setSellerId(UUID sellerId) {
        this.sellerId = sellerId;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setInitialPrice(double initialPrice) {
        this.initialPrice = initialPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public void setHighestBidderId(UUID highestBidderId) {
        this.highestBidderId = highestBidderId;
    }

    public void setMinimumIncrement(double minimumIncrement) {
        this.minimumIncrement = minimumIncrement;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }
}
