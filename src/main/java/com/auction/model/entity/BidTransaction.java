package com.auction.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class BidTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID id;
    private UUID bidderId;
    private double bidAmount;
    private LocalDateTime timestamp;

    public BidTransaction(UUID bidderId, double bidAmount, LocalDateTime timestamp) {
        this.id = UUID.randomUUID();
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.timestamp = timestamp;
    }

    public BidTransaction(UUID id, UUID bidderId, double bidAmount, LocalDateTime timestamp) {
        this.id = id;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.timestamp = timestamp;
    }

    //Getter
    public UUID getId() {
        return id;
    }

    public UUID getBidderId() {
        return bidderId;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    //Setter

    public void setBidderId(UUID bidderId) {
        this.bidderId = bidderId;
    }

    public void setBidAmount(double bidAmount) {
        this.bidAmount = bidAmount;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
