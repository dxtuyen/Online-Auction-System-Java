package com.auction.service;

import com.auction.model.entity.User;
import com.auction.persistence.dao.UserDao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake in-memory implementation of UserDao for unit tests.
 * No MySQL, no HikariCP — just a plain HashMap.
 */
class InMemoryUserDao implements UserDao {

    private final Map<UUID, User> store = new LinkedHashMap<>();

    // Spy counters to assert call behaviour without Mockito
    int insertCount = 0;
    int updateCount = 0;
    boolean insertShouldFail = false;

    @Override
    public void insert(User user) {
        if (insertShouldFail) throw new RuntimeException("Simulated DB failure");
        store.put(user.getId(), user);
        insertCount++;
    }

    @Override
    public void update(User user) {
        store.put(user.getId(), user);
        updateCount++;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return store.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public long count() {
        return store.size();
    }

    void reset() {
        store.clear();
        insertCount = 0;
        updateCount = 0;
        insertShouldFail = false;
    }
}
