package com.auction.persistence.dao;

import com.auction.model.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDao {

    void insert(User user);

    void update(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    List<User> findAll();

    long count();
}
