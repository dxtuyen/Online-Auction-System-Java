package com.auction.persistence.dao;

import com.auction.model.entity.Item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract cho Item.
 * <p>Item dùng single-table inheritance với discriminator {@code item_type}.
 * Images lưu ở bảng phụ {@code item_images} - load/save cùng item.</p>
 */
public interface ItemDao {

    /** Insert item + tất cả image URLs trong cùng transaction. */
    void insert(Item item);

    /**
     * Update các field mutable (seller_id/current owner, name, description,
     * starting_price, item_condition, extra_info nếu là OtherItem) + sync lại images (delete all + reinsert).
     * Type-specific immutable fields (brand, year...) không cần update.
     */
    void update(Item item);

    Optional<Item> findById(UUID id);

    List<Item> findAll();

    long count();
}
