package com.auction.model.exception;

public class IllegalAuctionStateException extends AuctionException {
    private static final long serialVersionUID = 1L;

    public IllegalAuctionStateException(String message) {
        super(message);
    }
}
