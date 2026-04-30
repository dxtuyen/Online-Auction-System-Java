package com.auction.model.enums;

/**
 * Trạng thái phiên đấu giá - state machine.
 *
 * Sơ đồ chuyển trạng thái:
 *   PENDING ──► RUNNING ──► FINISHED ──► PAID
 *      │           │            │
 *      └───────────┴────────────┴──► CANCELED
 *
 * PAID và CANCELED là TERMINAL state - không thể chuyển sang state khác.
 */
public enum AuctionStatus {
    PENDING("Đang chờ phiên đấu giá bắt đầu"),
    RUNNING("Phiên đấu giá đang diễn ra"),    // Fix typo: "PHiên" -> "Phiên"
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

    /** Kiểm tra phiên có đang trong trạng thái có thể nhận bid không */
    public boolean isOpenForBidding() {
        return this == RUNNING;
    }

    /** Kiểm tra trạng thái cuối (không thể đổi nữa) */
    public boolean isTerminal() {
        return this == PAID || this == CANCELED;
    }

    /**
     * State machine: kiểm tra có thể chuyển từ trạng thái hiện tại
     * sang trạng thái mới hay không.
     */
    public boolean canTransitionTo(AuctionStatus nextStatus) {
        if (nextStatus == null) return false;
        return switch (this) {
            case PENDING  -> nextStatus == RUNNING  || nextStatus == CANCELED;
            case RUNNING  -> nextStatus == FINISHED || nextStatus == CANCELED;
            case FINISHED -> nextStatus == PAID     || nextStatus == CANCELED;
            case PAID, CANCELED -> false;   // terminal
        };
    }
}
