package com.auction.model;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.util.List;

public class Electronic extends Item {

    private static final long serialVersionUID = 1L;

    private String brand; //thương hiệu
    private String model; //dòng sản phẩm
    private int warrantyMonths; //thời gian bảo hành

    public Electronic() {
        super();
    }

    public Electronic(String name, String description, int sellerId, double startingPrice, List<String> images, ItemCategory category, ItemCondition condition, String brand, String model, int warrantyMonths) {
        super(name, description, sellerId, startingPrice, images, category, condition);
        this.brand = brand;
        this.model = model;
        this.warrantyMonths = warrantyMonths;
    }

    public Electronic(int id, String name, String description, int sellerId, double startingPrice, List<String> images, ItemCategory category, ItemCondition condition, String brand, String model, int warrantyMonths) {
        super(id, name, description, sellerId, startingPrice, images, category, condition);
        this.brand = brand;
        this.model = model;
        this.warrantyMonths = warrantyMonths;
    }

    //Getter & Setter
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

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(int warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    //Methods
    @Override
    public String toString() {
        return "Electronic{" +
                "brand='" + brand + '\'' +
                ", model='" + model + '\'' +
                ", warrantyMonths=" + warrantyMonths +
                '}';
    }
}
