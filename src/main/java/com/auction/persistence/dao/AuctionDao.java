package com.auction.persistence.dao;

import com.auction.model.entity.Auction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionDao {

    void insert(Auction auction);

    void update(Auction auction);

    Optional<Auction> findById(UUID id);

    List<Auction> findAll();

    long count();
}
