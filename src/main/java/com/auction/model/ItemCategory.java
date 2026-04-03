package com.auction.model;

public enum ItemCategory {
    ELECTRONICS("Điện tử"),
    ART("Nghệ thuật"),
    VEHICLE("Phương tiện"),
    FASHION("Thời trang"),
    COLLECTIBLE("Sưu tầm"),
    OTHER("Khác");

    private final String displayName;

    ItemCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
