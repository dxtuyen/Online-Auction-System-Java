package com.auction.model.enums;

public enum ItemCondition {
    NEW("Mới"),
    USED("Đã qua sử dụng");

    private final String displayCondition;

    ItemCondition(String displayCondition) {
        this.displayCondition = displayCondition;
    }

    public String getDisplayCondition() {
        return displayCondition;
    }
}
