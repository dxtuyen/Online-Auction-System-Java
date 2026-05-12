package com.auction.protocol;

/**
 * Bảng tên action
 *
 * Cả client lẫn server đều import enum này
 */
public enum ActionType {

    // ============== USER ==============
    LOGIN,
    REGISTER,
    LOGOUT,
    GET_PROFILE,

    // ============== ITEM ==============
    CREATE_ITEM,
    LIST_MY_ITEMS,

    // ============== AUCTION ==============
    LIST_AUCTIONS,
    GET_AUCTION,
    CREATE_AUCTION,
    CLOSE_AUCTION,
    WATCH_AUCTION,
    UNWATCH_AUCTION,

    // ============== BID ==============
    PLACE_BID,
    BID_HISTORY,
    SET_AUTO_BID,

    // ============== SERVER → CLIENT (PUSH) ==============
    BID_UPDATE,         // có bid mới ở phiên đang xem
    AUCTION_STATUS,     // phiên đổi status (RUNNING → FINISHED, ...)
    AUCTION_EXTENDED;   // phiên được gia hạn do anti-sniping

    // Note: client tự build chart từ BID_HISTORY, không cần action BID_DIAGRAM riêng.

    /**
     * Parse tên action từ JSON. Trả null nếu không hợp lệ
     * — caller (RequestRouter) /sẽ trả Response.error("Action không hỗ trợ") thay vì để crash.
     */
    public static ActionType from(String name) {
        if (name == null) return null;
        try { return ActionType.valueOf(name); }
        catch (IllegalArgumentException e) { return null; }
    }
}