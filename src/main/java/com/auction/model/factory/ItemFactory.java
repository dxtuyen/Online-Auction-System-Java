package com.auction.model.factory;

import com.auction.model.entity.Art;
import com.auction.model.entity.Electronics;
import com.auction.model.entity.Item;
import com.auction.model.entity.OtherItem;
import com.auction.model.entity.Vehicle;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ItemFactory {

    private ItemFactory() {  }

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

            case FASHION, COLLECTIBLE, OTHER -> new OtherItem(
                    name, description, sellerId, startingPrice, images,
                    category, condition,
                    strOrNull(specificAttrs, "extraInfo")
            );
        };
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Thiếu thuộc tính bắt buộc: " + key);
        }
        return v.toString();
    }

    private static String strOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
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
