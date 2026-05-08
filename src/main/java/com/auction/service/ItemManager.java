package com.auction.service;

import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.model.factory.ItemFactory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Quản lý sản phẩm - registry trung tâm.
 * <p>
 * ============== DESIGN PATTERNS ==============
 * <p>
 * 1. SINGLETON (Bill Pugh idiom)
 * 2. FACADE - che giấu việc gọi ItemFactory + check seller
 * 3. REPOSITORY-LIKE - findById, findBySellerId
 * <p>
 * ============== BẢO MẬT ==============
 * <p>
 * - createItem KIỂM TRA seller phải có role SELLER và đang active
 * - Bidder/Admin không tạo được item (canSell() = false)
 */
public final class ItemManager {

    // ============== SINGLETON ==============

    private static final class Holder {
        private static final ItemManager INSTANCE = new ItemManager();
    }

    public static ItemManager getInstance() {
        return Holder.INSTANCE;
    }

    // ============== FIELDS ==============

    private final ConcurrentHashMap<UUID, Item> items = new ConcurrentHashMap<>();

    private ItemManager() { /* singleton */ }

    // ============== ITEM CREATION ==============

    /**
     * Tạo sản phẩm mới. Seller phải tồn tại + có quyền sell.
     *
     * @return Item đã được lưu
     * @throws IllegalArgumentException nếu seller không tồn tại / không có quyền
     */
    public Item createItem(ItemCategory category, String name, String description,
                           UUID sellerId, BigDecimal startingPrice,
                           List<String> images, ItemCondition condition,
                           Map<String, Object> specificAttrs) {
        Objects.requireNonNull(sellerId, "sellerId must not be null");

        // Check seller hợp lệ
        User seller = UserManager.getInstance().findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Seller không tồn tại: " + sellerId));
        if (!seller.canSell()) {
            throw new IllegalArgumentException(
                    "User không có quyền tạo sản phẩm (Tài khoản bị khóa)");
        }

        Item item = ItemFactory.create(category, name, description, sellerId,
                startingPrice, images, condition, specificAttrs);
        items.put(item.getId(), item);
        return item;
    }

    // ============== QUERIES ==============

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

    // ============== TEST ONLY ==============

    void clearForTesting() {
        items.clear();
    }
}
