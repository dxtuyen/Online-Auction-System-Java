package com.auction.model.exception;

/** Cố đặt bid khi phiên đã đóng (PENDING / FINISHED / PAID / CANCELED). */
public class AuctionClosedException extends AuctionException {
    private static final long serialVersionUID = 1L;
    public AuctionClosedException(String message) { super(message); }
}