package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public abstract class Item extends Entity {

    private static final long serialVersionUID = 1L;

    private String name;
    private String description;
    private final UUID sellerId;
    private BigDecimal startingPrice;
    private final List<String> images;
    private final ItemCategory category;
    private ItemCondition condition;

    protected Item(String name,
                   String description,
                   UUID sellerId,
                   BigDecimal startingPrice,
                   List<String> images,
                   ItemCategory category,
                   ItemCondition condition) {
        super();
        this.name = validateName(name);
        this.description = description;
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
        this.startingPrice = validatePrice(startingPrice);
        this.images = new ArrayList<>(Objects.requireNonNull(images, "images must not be null"));
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.condition = Objects.requireNonNull(condition, "condition must not be null");
    }

    protected Item(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                   String name, String description, UUID sellerId,
                   BigDecimal startingPrice, List<String> images,
                   ItemCategory category, ItemCondition condition) {
        super(id, createdAt, updatedAt);
        this.name = validateName(name);
        this.description = description;
        this.sellerId = Objects.requireNonNull(sellerId);
        this.startingPrice = validatePrice(startingPrice);
        this.images = new ArrayList<>(Objects.requireNonNull(images));
        this.category = Objects.requireNonNull(category);
        this.condition = Objects.requireNonNull(condition);
    }

    public abstract String getSpecificInfo();

    public final String printInfo() {
        return String.format(
                "[%s] %s (%s) - Giá khởi điểm: %s%n  Mô tả: %s%n  Chi tiết: %s",
                category.getDisplayName(),
                name,
                condition.getDisplayCondition(),
                startingPrice,
                description == null ? "(không có)" : description,
                getSpecificInfo()
        );
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public List<String> getImages() {
        return Collections.unmodifiableList(images);
    }

    public ItemCategory getCategory() {
        return category;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public void rename(String newName) {
        this.name = validateName(newName);
        markUpdated();
    }

    public void updateDescription(String description) {
        this.description = description;
        markUpdated();
    }

    public void updatePrice(BigDecimal newPrice) {
        this.startingPrice = validatePrice(newPrice);
        markUpdated();
    }

    public void updateCondition(ItemCondition newCondition) {
        this.condition = Objects.requireNonNull(newCondition, "condition must not be null");
        markUpdated();
    }

    public void addImage(String url) {
        Objects.requireNonNull(url, "url must not be null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("URL ảnh không được rỗng");
        }
        if (!images.contains(url)) {
            images.add(url);
            markUpdated();
        }
    }

    public void removeImage(String url) {
        if (images.remove(url)) {
            markUpdated();
        }
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Tên sản phẩm không được rỗng");
        }
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("Tên sản phẩm tối đa 200 ký tự");
        }
        return trimmed;
    }

    private static BigDecimal validatePrice(BigDecimal price) {
        Objects.requireNonNull(price, "price must not be null");
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Giá khởi điểm phải >= 0");
        }
        return price;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", startingPrice=" + startingPrice +
                '}';
    }
}
