package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Sản phẩm điện tử - có thêm brand, model, thời hạn bảo hành. */
public class Electronics extends Item {

    private static final long serialVersionUID = 1L;

    private final String brand;
    private final String model;
    private final int warrantyMonths;   // 0 nếu hết bảo hành / hàng cũ

    public Electronics(String name, String description, UUID sellerId,
                       BigDecimal startingPrice, List<String> images,
                       ItemCondition condition,
                       String brand, String model, int warrantyMonths) {
        super(name, description, sellerId, startingPrice, images,
                ItemCategory.ELECTRONICS, condition);   // category cố định
        this.brand = brand;
        this.model = model;
        if (warrantyMonths < 0) {
            throw new IllegalArgumentException("warrantyMonths phải >= 0");
        }
        this.warrantyMonths = warrantyMonths;
    }

    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public int getWarrantyMonths() { return warrantyMonths; }

    @Override
    public String getSpecificInfo() {
        return String.format("Brand: %s | Model: %s | Bảo hành: %d tháng",
                brand, model, warrantyMonths);
    }
}
