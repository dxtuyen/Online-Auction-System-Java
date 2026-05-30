package com.auction.persistence.dao;

import com.auction.model.entity.Item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemDao {

    void insert(Item item);

    void update(Item item);

    Optional<Item> findById(UUID id);

    List<Item> findAll();

    long count();
}
