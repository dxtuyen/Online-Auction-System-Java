package com.auction.model.exception;

public class InsufficientBalanceException extends AuctionException {
    private static final long serialVersionUID = 1L;

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
