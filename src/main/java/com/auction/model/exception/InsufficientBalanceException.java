package com.auction.model.exception;

/**
 * Ném khi cố trừ tiền vượt số dư.
 * Đặt ở package exception riêng để dễ scale (sau này thêm AuctionException, BidException...)
 * <p>
 * UPDATE: đã extends AuctionException để global handler chỉ cần catch
 * AuctionException là xử lý được hết các lỗi nghiệp vụ đấu giá.
 */
public class InsufficientBalanceException extends AuctionException {
    private static final long serialVersionUID = 1L;

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
