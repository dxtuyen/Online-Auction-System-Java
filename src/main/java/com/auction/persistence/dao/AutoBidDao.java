package com.auction.persistence.dao;

import com.auction.model.entity.AutoBid;

import java.util.List;
import java.util.UUID;

public interface AutoBidDao {

    void insert(AutoBid autoBid);

    void deleteByBidderAndAuction(UUID bidderId, UUID auctionId);

    void updateActive(UUID bidderId, UUID auctionId, boolean active);

    List<AutoBid> findAll();

    long count();
}
