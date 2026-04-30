package com.auction.model.exception;

/**
 * Ném khi cố trừ tiền vượt số dư.
 * Đặt ở package exception riêng để dễ scale (sau này thêm AuctionException, BidException...)
 */
public class InsufficientBalanceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InsufficientBalanceException(String message) {
        super(message);
    }
}