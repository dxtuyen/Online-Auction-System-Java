package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class Item extends Entity {

    private static final long serialVersionUID = 1L;

    private String name; // tên sản phẩm
    private String description; // mô tả (optional)
    private final String sellerId; // không đổi
    private double startingPrice; // >= 0
    private final List<String> images; // không replace list
    private final ItemCategory category; // không đổi
    private ItemCondition condition;

    // có thể có lỗi null, giải pháp như sau
    public Item(String name,
                String description,
                String sellerId,
                double startingPrice,
                List<String> images,
                ItemCategory category,
                ItemCondition condition) {

        super();

        this.name = Objects.requireNonNull(name);
        this.description = description; // optional
        this.sellerId = Objects.requireNonNull(sellerId);

        if (startingPrice < 0) {
            throw new IllegalArgumentException("Giá phải >= 0");
        }
        this.startingPrice = startingPrice;

        this.images = new ArrayList<>(Objects.requireNonNull(images));
        this.category = Objects.requireNonNull(category);
        this.condition = Objects.requireNonNull(condition);
    }

    // Getter
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSellerId() { return sellerId; }
    public double getStartingPrice() { return startingPrice; }

    // sẽ thử trả về bản sao của images sau, hiện tại như này
    public List<String> getImages() { return Collections.unmodifiableList(images); }
    public ItemCategory getCategory() { return category; }
    public ItemCondition getCondition() { return condition; }

    // Setter
    public void rename(String newName) {
        this.name = Objects.requireNonNull(newName);
        markUpdated();
    }

    public void updateDescription(String description) {
        this.description = description;
        markUpdated();
    }

    public void updatePrice(double newPrice) {
        if (newPrice < 0) {
            throw new IllegalArgumentException("Giá phải >= 0");
        }
        this.startingPrice = newPrice;
        markUpdated();
    }

    public void updateCondition(ItemCondition newCondition) {
        this.condition = Objects.requireNonNull(newCondition);
        markUpdated();
    }

    public void addImage(String url) {
        images.add(Objects.requireNonNull(url));
        markUpdated();
    }

    public void removeImage(String url) {
        images.remove(url);
        markUpdated();
    }

    @Override
    public String getDisplayInfo() {
        return String.format("[%s] %s - %.2f",
                category.getDisplayName(),
                name,
                startingPrice);
    }

    @Override
    public String toString() {
        return "Item{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", sellerId=" + sellerId +
                ", startingPrice=" + startingPrice +
                ", images=" + images +
                ", category=" + category +
                ", condition=" + condition +
                '}';
    }
}
