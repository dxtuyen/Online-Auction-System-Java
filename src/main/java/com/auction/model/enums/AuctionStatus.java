package com.auction.model.enums;

public enum AuctionStatus {
    PENDING("Đang chờ phiên đấu giá bắt đầu"),
    RUNNING("Phiên đấu giá đang diễn ra"),
    FINISHED("Phiên đấu giá đã kết thúc"),
    PAID("Đã thanh toán"),
    CANCELED("Đã hủy");

    private final String displayName;

    AuctionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOpenForBidding() {
        return this == RUNNING;
    }

    public boolean isTerminal() {
        return this == PAID || this == CANCELED;
    }

    public boolean canTransitionTo(AuctionStatus nextStatus) {
        if (nextStatus == null) return false;
        return switch (this) {
            case PENDING -> nextStatus == RUNNING || nextStatus == CANCELED;
            case RUNNING -> nextStatus == FINISHED || nextStatus == CANCELED;
            case FINISHED -> nextStatus == PAID || nextStatus == CANCELED;
            case PAID, CANCELED -> false;
        };
    }
}
