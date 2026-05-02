package com.auction.model.enums;

public enum UserStatus {
    ACTIVE("Đang hoạt động"),
    BANNED("Đã bị ban");

    private final String displayName;

    UserStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
