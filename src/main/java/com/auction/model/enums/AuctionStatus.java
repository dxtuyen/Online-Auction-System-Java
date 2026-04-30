package com.auction.model.enums;

public enum AuctionStatus {
    PENDING("Đang chờ phiên đấu giá bắt đầu"),
    RUNNING("PHiên đấu giá đang diễn ra"),
    FINISHED("Phiên đấu giá đã kết thúc"),
    PAID("Đã thanh toán"),
    CANCELED("Đã hủy");

    private final String displayStatus;

    AuctionStatus(String displayStatus) {
        this.displayStatus = displayStatus;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }

    public boolean canTransitionTo(AuctionStatus nextStatus) {
        return switch (this) {
            case PENDING -> nextStatus == RUNNING || nextStatus == CANCELED;
            case RUNNING -> nextStatus == FINISHED ||nextStatus == CANCELED;
            case FINISHED -> nextStatus == PAID || nextStatus == CANCELED;
            case PAID,CANCELED -> false;
        };
    }
}