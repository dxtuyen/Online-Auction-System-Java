package com.auction.model.exception;

public class AuctionClosedException extends AuctionException {
    private static final long serialVersionUID = 1L;

    public AuctionClosedException(String message) {
        super(message);
    }
}
