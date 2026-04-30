package com.auction.model.exception;

/** Bid không hợp lệ: thấp hơn giá hiện tại, không đủ minimum increment, v.v. */
public class InvalidBidException extends AuctionException {
    private static final long serialVersionUID = 1L;
    public InvalidBidException(String message) { super(message); }
}
