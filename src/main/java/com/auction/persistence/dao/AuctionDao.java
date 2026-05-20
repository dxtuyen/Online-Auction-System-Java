package com.auction.persistence.dao;

import com.auction.model.entity.Auction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract cho Auction.
 *
 * <p>Auction có nhiều mutation runtime (mỗi bid → currentPrice/totalBids/highestBidder
 * thay đổi; anti-sniping → endTime; scheduler → status). AuctionManager hook observer
 * để gọi update() sau mỗi event.</p>
 */
public interface AuctionDao {

    void insert(Auction auction);

    /** Update các field mutable: end_time, current_price, highest_bidder_id, status, total_bids. */
    void update(Auction auction);

    Optional<Auction> findById(UUID id);

    List<Auction> findAll();

    long count();
}
