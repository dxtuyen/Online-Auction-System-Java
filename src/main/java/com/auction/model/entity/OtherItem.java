package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class OtherItem extends Item {

    private static final long serialVersionUID = 1L;

    private String extraInfo;

    public OtherItem(String name, String description, UUID sellerId,
                     BigDecimal startingPrice, List<String> images,
                     ItemCategory category, ItemCondition condition,
                     String extraInfo) {
        super(name, description, sellerId, startingPrice, images,
                validateCategory(category), condition);
        this.extraInfo = extraInfo;
    }

    public OtherItem(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                     String name, String description, UUID sellerId,
                     BigDecimal startingPrice, List<String> images,
                     ItemCategory category, ItemCondition condition,
                     String extraInfo) {
        super(id, createdAt, updatedAt, name, description, sellerId,
                startingPrice, images, validateCategory(category), condition);
        this.extraInfo = extraInfo;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void updateExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
        markUpdated();
    }

    @Override
    public String getSpecificInfo() {
        if (extraInfo == null || extraInfo.isBlank()) {
            return "(không có thông tin chi tiết)";
        }
        return extraInfo;
    }

    private static ItemCategory validateCategory(ItemCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        if (category == ItemCategory.ELECTRONICS
                || category == ItemCategory.ART
                || category == ItemCategory.VEHICLE) {
            throw new IllegalArgumentException(
                    "Category '" + category + "' đã có subclass riêng. " +
                            "Hãy dùng " + getDedicatedClassName(category) + " thay vì OtherItem.");
        }
        return category;
    }

    private static String getDedicatedClassName(ItemCategory category) {
        return switch (category) {
            case ELECTRONICS -> "Electronics";
            case ART -> "Art";
            case VEHICLE -> "Vehicle";
            default -> "OtherItem";
        };
    }
}
