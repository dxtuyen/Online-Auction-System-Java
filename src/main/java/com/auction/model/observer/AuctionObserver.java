package com.auction.model.observer;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.enums.AuctionStatus;

public interface AuctionObserver {

    default void onBidPlaced(Auction auction, BidTransaction bid) {
    }

    default void onAuctionExtended(Auction auction, int extendedSeconds) {
    }

    default void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
    }
}
