package com.auction.repository.user;

import com.auction.model.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User create(String username, String password);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    /*boolean update(User user);

    boolean delete(UUID id);*/

}
