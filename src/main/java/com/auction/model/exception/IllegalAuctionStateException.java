package com.auction.model.exception;

/** Cố chuyển state không hợp lệ (vd: PENDING -> PAID). */
public class IllegalAuctionStateException extends AuctionException {
    private static final long serialVersionUID = 1L;
    public IllegalAuctionStateException(String message) { super(message); }
}