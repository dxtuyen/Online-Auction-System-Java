package com.auction.model.entity;

public class Electronic extends Item {
    private String brand; // hãng
    private int warrantyMonths; // bảo hành bao nhiêu tháng

    public Electronic(String id, String name, double startPrice,
                      String description, String sellerId,
                      String brand, int warrantyMonths) {
        super(id, name, startPrice, description, sellerId);
        this.brand = brand;
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public String getCategory() { return "Điện tử"; } // lấy ra danh mục

    public String getBrand() { return brand; } // lẩy ra hãng

    @Override
    public String printInfo() {
        return super.printInfo()
                + String.format(" | Hãng: %s | BH: %d tháng", brand, warrantyMonths);
    }
}