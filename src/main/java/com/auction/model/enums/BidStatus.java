package com.auction.model.enums;

/**
 * Trạng thái của một bid.
 *
 * Sơ đồ chuyển trạng thái:
 *   PENDING ──► VALID ──► OUTBID ──► CANCELED
 *       │         │
 *       └─► REJECTED
 *       └─► CANCELED
 *
 * REJECTED và CANCELED là terminal.
 */
public enum BidStatus {
    PENDING("Đang xử lý"),
    VALID("Hợp lệ"),
    OUTBID("Bị vượt giá"),
    REJECTED("Bị từ chối"),
    CANCELED("Đã hủy");        // ĐỔI: CANCELLED -> CANCELED cho đồng bộ với AuctionStatus

    private final String displayName;

    BidStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == REJECTED || this == CANCELED;
    }

    public boolean canTransitionTo(BidStatus next) {
        if (next == null) return false;
        return switch (this) {
            case PENDING  -> next == VALID || next == REJECTED || next == CANCELED;
            case VALID    -> next == OUTBID || next == CANCELED;
            case OUTBID   -> next == CANCELED;
            case REJECTED, CANCELED -> false;
        };
    }
}
