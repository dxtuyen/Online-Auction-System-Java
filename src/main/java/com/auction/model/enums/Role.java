package com.auction.model.enums;

public enum Role {
    ADMIN("Quản trị viên"),
    NORMAL("Người đấu giá");

    private final String displayRole;

    Role(String displayRole) {
        this.displayRole = displayRole;
    }

    public String getDisplayRole() {
        return displayRole;
    }
}