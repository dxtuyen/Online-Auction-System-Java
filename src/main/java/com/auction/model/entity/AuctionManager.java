package com.auction.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuctionManager extends Entity {
    // Singleton

    private static volatile AuctionManager auctionManager;
    private String data;

    private AuctionManager(String data) {
        this.data = data;
    }
    public static AuctionManager getAuctionManager(String data) {
        if (auctionManager == null) {
            synchronized (AuctionManager.class) {
                if (auctionManager == null) {
                    auctionManager = new AuctionManager(data);
                }
                return auctionManager;
            }
        }
        return auctionManager;
    }

    public String getData() {
        return data;
    }

    // Quản lý/Manager
    private final Map<String, Auction> auctions = new HashMap<>();

    /* Tao auction */
    public void createAuction() {
        Auction auction = new Auction();
    }

    /* Dat gia */

}
