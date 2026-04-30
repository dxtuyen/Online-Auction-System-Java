package com.auction.model.exception;

/** Base cho tất cả exception nghiệp vụ liên quan đấu giá. */
public abstract class AuctionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    protected AuctionException(String message) { super(message); }
    protected AuctionException(String message, Throwable cause) { super(message, cause); }
}
