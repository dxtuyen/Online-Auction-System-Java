// File: com/auction/model/entity/Vehicle.java
package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Phương tiện - có hãng, đời, số km đã đi. */
public class Vehicle extends Item {

    private static final long serialVersionUID = 1L;

    private final String make;          // Toyota, Honda...
    private final String model;
    private final int year;
    private final int mileageKm;

    public Vehicle(String name, String description, UUID sellerId,
                   BigDecimal startingPrice, List<String> images,
                   ItemCondition condition,
                   String make, String model, int year, int mileageKm) {
        super(name, description, sellerId, startingPrice, images,
                ItemCategory.VEHICLE, condition);
        this.make = make;
        this.model = model;
        if (year < 1900) throw new IllegalArgumentException("year không hợp lệ");
        if (mileageKm < 0) throw new IllegalArgumentException("mileageKm phải >= 0");
        this.year = year;
        this.mileageKm = mileageKm;
    }

    public String getMake() { return make; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    public int getMileageKm() { return mileageKm; }

    @Override
    public String getSpecificInfo() {
        return String.format("%s %s đời %d | Đã đi: %,d km",
                make, model, year, mileageKm);
    }
}