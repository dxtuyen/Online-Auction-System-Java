package com.auction.protocol;

public enum ActionType {

    LOGIN,
    REGISTER,
    LOGOUT,
    GET_PROFILE,

    CREATE_ITEM,
    LIST_MY_ITEMS,

    LIST_AUCTIONS,
    GET_AUCTION,
    CREATE_AUCTION,
    CLOSE_AUCTION,
    WATCH_AUCTION,
    UNWATCH_AUCTION,
    CONFIRM_PAYMENT,
    FORFEIT_AUCTION,

    PLACE_BID,
    BID_HISTORY,
    SET_AUTO_BID,

    BID_UPDATE,
    AUCTION_STATUS,
    AUCTION_EXTENDED;

    public static ActionType from(String name) {
        if (name == null) return null;
        try {
            return ActionType.valueOf(name);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }
}
