package com.auction.model;

import java.util.List;

public abstract class Item extends Entity {

    private static final long serialVersionUID = 1L;

    private String name;
    private String description;
    private int sellerId;
    private double startingPrice;
    private List<String> images; //Link URl
    private ItemCategory category;
    private ItemCondition condition;

    public Item() {
    }

    public Item(String name, String description, int sellerId, double startingPrice, List<String> images, ItemCategory category, ItemCondition condition) {
        super();
        this.name = name;
        this.description = description;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
        this.images = images;
        this.category = category;
        this.condition = condition;
    }

    public Item(int id, String name, String description, int sellerId, double startingPrice, List<String> images, ItemCategory category, ItemCondition condition) {
        super(id);
        this.name = name;
        this.description = description;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
        this.images = images;
        this.category = category;
        this.condition = condition;
    }

    //Getter & Setter
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSellerId() {
        return sellerId;
    }

    public void setSellerId(int sellerId) {
        this.sellerId = sellerId;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public void setCategory(ItemCategory category) {
        this.category = category;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public void setCondition(ItemCondition condition) {
        this.condition = condition;
    }

    //Methods
    public String toString() {
        return "Tên sản phẩm: " + name +
                "Giá khởi điểm: " + startingPrice;
    }
}
