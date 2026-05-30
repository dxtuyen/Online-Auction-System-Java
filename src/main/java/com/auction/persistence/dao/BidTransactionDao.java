package com.auction.persistence.dao;

import com.auction.model.entity.BidTransaction;

import java.util.List;
import java.util.UUID;

public interface BidTransactionDao {

    void insert(BidTransaction bid);

    List<BidTransaction> findAll();

    List<BidTransaction> findByAuctionId(UUID auctionId);

    long count();
}
