package com.auction.model.entity;

public abstract class Item extends Entity {
    private String name;
    private double startPrice;
    private String description;
    private String sellerId;

    public Item(String id, String name, double startPrice,
                String description, String sellerId) {
        super(id);
        setStartPrice(startPrice); // validate
        this.name = name;
        this.description = description;
        this.sellerId = sellerId;
    }

    // Getters
    public String getName() { return name; }
    public double getStartPrice() { return startPrice; }
    public String getDescription() { return description; }
    public String getSellerId() { return sellerId; }

    // Setter có validate, ném ngoại lện nêu không phù hợp.
    public void setStartPrice(double startPrice) {
        if (startPrice <= 0) throw new IllegalArgumentException("Giá phải > 0");
        this.startPrice = startPrice;
    }

    public abstract String getCategory();

    public String printInfo() {
        return String.format("[%s] %s — Giá KĐ: %,.0f VNĐ — %s",
                getCategory(), name, startPrice, description);
    }

    @Override
    public String toDisplayString() {
        return printInfo();
    }
}