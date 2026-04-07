package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.util.List;

public class Vehicle extends Item {
    private static final long serialVersionUID = 1L;

    private String brand;              // Hãng xe
    private String model;              // Dòng xe
    private int manufactureYear;       // Năm sản xuất
    private int mileage;               // Số km đã đi
    private String color;              // Màu xe
    private String fuelType;           // Xăng / Dầu / Điện
    private String transmission;       // Số sàn / Số tự động
    private int ownerCount;            // Số đời chủ
    private boolean hasRegistration;   // Có giấy tờ hay không

    public Vehicle() {
        super();
    }

    public Vehicle(String name, String description, int sellerId, double startingPrice,
                   List<String> images, ItemCategory category, ItemCondition condition,
                   String brand, String model, int manufactureYear, int mileage,
                   String color, String fuelType, String transmission,
                   int ownerCount, boolean hasRegistration) {
        super(name, description, sellerId, startingPrice, images, category, condition);
        this.brand = brand;
        this.model = model;
        this.manufactureYear = manufactureYear;
        this.mileage = mileage;
        this.color = color;
        this.fuelType = fuelType;
        this.transmission = transmission;
        this.ownerCount = ownerCount;
        this.hasRegistration = hasRegistration;
    }

    public Vehicle(int id, String name, String description, int sellerId, double startingPrice,
                   List<String> images, ItemCategory category, ItemCondition condition,
                   String brand, String model, int manufactureYear, int mileage,
                   String color, String fuelType, String transmission,
                   int ownerCount, boolean hasRegistration) {
        super(id, name, description, sellerId, startingPrice, images, category, condition);
        this.brand = brand;
        this.model = model;
        this.manufactureYear = manufactureYear;
        this.mileage = mileage;
        this.color = color;
        this.fuelType = fuelType;
        this.transmission = transmission;
        this.ownerCount = ownerCount;
        this.hasRegistration = hasRegistration;
    }

    // Getter & Setter
    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getManufactureYear() {
        return manufactureYear;
    }

    public void setManufactureYear(int manufactureYear) {
        this.manufactureYear = manufactureYear;
    }

    public int getMileage() {
        return mileage;
    }

    public void setMileage(int mileage) {
        this.mileage = mileage;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getFuelType() {
        return fuelType;
    }

    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }

    public String getTransmission() {
        return transmission;
    }

    public void setTransmission(String transmission) {
        this.transmission = transmission;
    }

    public int getOwnerCount() {
        return ownerCount;
    }

    public void setOwnerCount(int ownerCount) {
        this.ownerCount = ownerCount;
    }

    public boolean isHasRegistration() {
        return hasRegistration;
    }

    public void setHasRegistration(boolean hasRegistration) {
        this.hasRegistration = hasRegistration;
    }

    @Override
    public String toString() {
        return super.toString() +
                "\nHãng: " + brand +
                "\nDòng xe: " + model +
                "\nNăm sản xuất: " + manufactureYear +
                "\nSố km đã đi: " + mileage +
                "\nMàu: " + color +
                "\nNhiên liệu: " + fuelType +
                "\nHộp số: " + transmission +
                "\nSố đời chủ: " + ownerCount +
                "\nGiấy tờ: " + (hasRegistration ? "Có" : "Không");
    }
}
