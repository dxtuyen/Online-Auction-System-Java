package com.auction.model.factory;

import com.auction.model.entity.Art;
import com.auction.model.entity.Electronics;
import com.auction.model.entity.Item;
import com.auction.model.entity.Vehicle;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factory Method Pattern - tạo Item theo category.
 *
 * Lý do dùng Factory:
 *  1. CLIENT KHÔNG cần biết về các subclass cụ thể (Electronics, Art, Vehicle...).
 *     Chỉ cần truyền category + thuộc tính → factory lo phần "new".
 *  2. Khi thêm category mới (vd Fashion), CHỈ sửa 1 chỗ là factory.
 *     → Open/Closed Principle (SOLID).
 *  3. Encapsulate logic validation đặc thù của từng loại.
 *
 * Ví dụ sử dụng:
 *   Item phone = ItemFactory.create(
 *       ItemCategory.ELECTRONICS,
 *       "iPhone 15", "Like new", sellerId,
 *       new BigDecimal("20000000"), List.of("img.jpg"), ItemCondition.USED,
 *       Map.of("brand", "Apple", "model", "iPhone 15", "warrantyMonths", 6)
 *   );
 */
public final class ItemFactory {

    private ItemFactory() { /* utility class */ }

    /**
     * Tạo item theo category.
     *
     * @param specificAttrs thuộc tính đặc thù của từng loại (xem doc của từng case)
     */
    public static Item create(ItemCategory category,
                              String name,
                              String description,
                              UUID sellerId,
                              BigDecimal startingPrice,
                              List<String> images,
                              ItemCondition condition,
                              Map<String, Object> specificAttrs) {

        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (specificAttrs == null) specificAttrs = Map.of();

        return switch (category) {
            case ELECTRONICS -> new Electronics(
                    name, description, sellerId, startingPrice, images, condition,
                    str(specificAttrs, "brand"),
                    str(specificAttrs, "model"),
                    integer(specificAttrs, "warrantyMonths", 0)
            );
            case ART -> new Art(
                    name, description, sellerId, startingPrice, images, condition,
                    str(specificAttrs, "artist"),
                    integerOrNull(specificAttrs, "yearCreated"),
                    str(specificAttrs, "medium")
            );
            case VEHICLE -> new Vehicle(
                    name, description, sellerId, startingPrice, images, condition,
                    str(specificAttrs, "make"),
                    str(specificAttrs, "model"),
                    integer(specificAttrs, "year", 0),
                    integer(specificAttrs, "mileageKm", 0)
            );
            // FASHION, COLLECTIBLE, OTHER chưa có subclass riêng -> tạm throw
            // Khi thêm subclass mới, chỉ cần thêm case ở đây
            default -> throw new UnsupportedOperationException(
                    "Chưa hỗ trợ tạo item cho category: " + category);
        };
    }

    // ============== HELPERS ==============
    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Thiếu thuộc tính bắt buộc: " + key);
        }
        return v.toString();
    }

    private static int integer(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private static Integer integerOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }
}