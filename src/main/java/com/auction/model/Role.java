package com.auction.model;

public enum Role {
    ADMIN("Quản trị viên"),
    BIDDER("Người đấu giá"),
    SELLER("Người bán");

    private final String displayRole;
    Role(String displayRole) {
        this.displayRole = displayRole;
    }

    public String getDisplayRole() {
        return displayRole;
    }
}
