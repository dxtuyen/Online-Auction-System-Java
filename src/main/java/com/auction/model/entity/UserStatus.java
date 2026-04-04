package com.auction.model.entity;

public enum UserStatus {
    ACTIVE("Đang hoạt động"),
    BANNED("Đã bị ban");

    private final String displayStatus;
    UserStatus(String displayStatus) {
        this.displayStatus = displayStatus;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }
}
