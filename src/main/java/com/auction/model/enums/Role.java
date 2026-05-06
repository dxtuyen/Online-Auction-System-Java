package com.auction.model.enums;

public enum Role {
    ADMIN("Quản trị viên"),
    BIDDER("Người đấu giá"),
    SELLER("Người bán");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}