package com.auction.model.entity;

import java.io.Serializable;
import java.util.UUID;

public class Item implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID id;
    private String name;
    private String description;
    private double initialPrice;
    private UUID sellerId;

    public Item (String name, String description, double initialPrice, UUID sellerId) {
        this.name = name;
        this.description = description;
        this.initialPrice = initialPrice;
        this.sellerId = sellerId;
    }

    public Item(UUID id, String name, String description, double initialPrice, UUID sellerId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.initialPrice = initialPrice;
        this.sellerId = sellerId;
    }

    //Getter
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getstartingPrice() {
        return initialPrice;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    //Setter

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setInitialPrice(double initialPrice) {
        this.initialPrice = initialPrice;
    }

    public void setSellerId(UUID sellerId) {
        this.sellerId = sellerId;
    }
}
