package com.auction.model.entity;

import java.time.LocalDateTime;

// BidTransaction  như là một biên lai đặt giá hoặc bản ghi mỗi khi một người dùng Bidder thực
// hiện hành động đặt giá vào một phiên đấu giá.
public class BidTransaction extends Entity {
    private String bidderId;
    private String auctionId;
    private double amount;
    // timestamp là gì : nó ghi lại thời gian mà môi lần Bidder đặt giá, nếu 2 người đặt cùng giá
    // hệ thống sẽ ưu tiên người đặt sơm hơn, đó là logic.
    private LocalDateTime timestamp;

    public BidTransaction(String id, String bidderId, String auctionId,
                          double amount) {
        super(id);
        this.bidderId = bidderId; // id của bidder
        this.auctionId = auctionId; // id của lần lần đặt giá nhất định tại một thời điểm do người nào đó đặt
        this.amount = amount; // tiền , dễ rồi
        this.timestamp = LocalDateTime.now(); // thời gian đặt vừa nói ở trên
    }
    // các phương thức này để lấy ra thông tin nhất định
    public String getBidderId() { return bidderId; }
    public String getAuctionId() { return auctionId; }
    public double getAmount() { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    // In ra các thông số tiền đặt, bidder nào đặt ( theo mã ), thời điểm đặt
    public String toDisplayString() {
        return String.format("Bid %,.0f VNĐ bởi %s lúc %s",
                amount, bidderId, timestamp);
    }
}