package com.auction.service;

import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.model.factory.ItemFactory;
import com.auction.persistence.dao.ItemDao;
import com.auction.persistence.dao.MysqlItemDao;
import com.auction.util.AppLogger;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ItemManager {

    private static final Logger log = AppLogger.get(ItemManager.class);

    private static final class Holder {
        private static final ItemManager INSTANCE = new ItemManager();
    }

    public static ItemManager getInstance() {
        return Holder.INSTANCE;
    }

    private final ItemDao dao;
    private final ConcurrentHashMap<UUID, Item> items = new ConcurrentHashMap<>();

    private ItemManager() {
        this.dao = new MysqlItemDao();
    }

    ItemManager(ItemDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao must not be null");
    }

    public void loadAllFromDb() {
        items.clear();
        for (Item item : dao.findAll()) {
            items.put(item.getId(), item);
        }
        log.info(() -> "Đã load " + items.size() + " item từ DB");
    }

    public long countInDb() {
        return dao.count();
    }

    public Item createItem(ItemCategory category, String name, String description,
                           UUID sellerId, BigDecimal startingPrice,
                           List<String> images, ItemCondition condition,
                           Map<String, Object> specificAttrs) {
        Objects.requireNonNull(sellerId, "sellerId must not be null");

        User seller = UserManager.getInstance().findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Seller không tồn tại: " + sellerId));
        if (!seller.canSell()) {
            throw new IllegalArgumentException(
                    "User không có quyền tạo sản phẩm (Tài khoản bị khóa)");
        }

        Item item = ItemFactory.create(category, name, description, sellerId,
                startingPrice, images, condition, specificAttrs);

        dao.insert(item);
        items.put(item.getId(), item);
        return item;
    }

    public void save(Item item) {
        Objects.requireNonNull(item, "item must not be null");
        if (!items.containsKey(item.getId())) {
            throw new IllegalArgumentException(
                    "Item chưa được register: " + item.getId());
        }
        dao.update(item);
    }

    public Optional<Item> findById(UUID itemId) {
        return Optional.ofNullable(items.get(Objects.requireNonNull(itemId)));
    }

    public Collection<Item> findAll() {
        return Collections.unmodifiableCollection(items.values());
    }

    public List<Item> findBySellerId(UUID sellerId) {
        Objects.requireNonNull(sellerId);
        return items.values().stream()
                .filter(i -> i.getSellerId().equals(sellerId))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<Item> findByCategory(ItemCategory category) {
        Objects.requireNonNull(category);
        return items.values().stream()
                .filter(i -> i.getCategory() == category)
                .collect(Collectors.toUnmodifiableList());
    }

    public int count() {
        return items.size();
    }

    void clearForTesting() {
        items.clear();
    }
}
