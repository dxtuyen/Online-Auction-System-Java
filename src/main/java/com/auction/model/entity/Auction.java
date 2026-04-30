package com.auction.model.entity;

import com.auction.model.enums.AuctionStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/*
Phiên đấu giá: chứa thông tin của phiên đấu giá
*/

public class Auction extends Entity {

    private static final long serialVersionUID = 1L;

    // Người bán
    private final UUID itemId; // id của sản phẩm được bán trong phiên đấu giá
    private final UUID sellerId; // id của người bán

    // Thời gian
    private final LocalDateTime startTime; // thời gian bắt đầu phiên đấu giá
    private LocalDateTime endTime; // thời gian kết thúc phiên đấu giá

    // Giá
    private final double startingPrice; // giá khởi điểm
    private double currentPrice; // giá cao nhất hiện tại, có thể thay đổi để anti-sniping
    private UUID highestBidderId; // id người trả giá cao nhất
    private final double minimumIncrement; // bước nhảy giá nhỏ nhất để đặt giá so với bid trước

    // Trạng thái
    private AuctionStatus status; // trạng thái của phiên đấu giá(PENDING/RUNNING/FINISHED/PAID/CANCELED)

    // Linh tinh
    private int totalBids; // số lương bid (đặt giá thầu thành công) trong phiên đấu giá

    // constructor
    public Auction(UUID itemId, UUID sellerId,
                   LocalDateTime startTime,
                   LocalDateTime endTime,
                   double startingPrice,
                   double minimumIncrement) {
        super();

        this.itemId = itemId;
        this.sellerId = sellerId;

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Thời gian bắt đầu không thể là null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime phải sau startTime");
        }
        this.startTime = startTime;
        this.endTime = endTime;

        if (startingPrice < 0 || minimumIncrement < 0) {
            throw new IllegalArgumentException("Giá sàn >= 0");
        }
        this.startingPrice = startingPrice; // check giá tiền hợp lệ, sẽ fix sau
        this.currentPrice = startingPrice;
        this.minimumIncrement = minimumIncrement; // check giá trị hợp lệ, sẽ fix sau

        this.highestBidderId = null;
        this.status = AuctionStatus.PENDING;
        this.totalBids = 0;
    }

    // load từ database
    public Auction(UUID id,
                   UUID itemId,
                   UUID sellerId,
                   LocalDateTime startTime,
                   LocalDateTime endTime,
                   double startingPrice,
                   double currentPrice,
                   double minimumIncrement,
                   UUID highestBidderId,
                   AuctionStatus status,
                   int totalBids,
                   LocalDateTime createdAt,
                   LocalDateTime updatedAt) {

        super(id, createdAt, updatedAt);

        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startTime = startTime;
        this.endTime = endTime;

        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.minimumIncrement = minimumIncrement;

        this.highestBidderId = highestBidderId;
        this.status = status;
        this.totalBids = totalBids;
    }
    
    //Getter & Setter
    public UUID getItemId() {
        return itemId;
    }
    
    public UUID getSellerId() {
        return sellerId;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    public LocalDateTime getEndTime() {
        return endTime;
    }

    public double getStartingPrice() {
        return startingPrice;
    }
    public double getCurrentPrice() {
        return currentPrice;
    }

    public UUID getHighestBidderId() {
        return highestBidderId;
    }

    public double getMinimumIncrement() {
        return minimumIncrement;
    }
    public AuctionStatus getStatus() {
        return status;
    }
    public int getTotalBids() {
        return totalBids;
    }

    //Methods

    // check trạng thái RUNNING
    public boolean isActive() {
        return status == AuctionStatus.RUNNING && LocalDateTime.now().isAfter(startTime) && LocalDateTime.now().isBefore(endTime);
    }

    // còn bao nhiêu thời gian trước khi kết thúc phiên
    public long getRemainingSeconds() {
        if (!isActive()) return 0;
        return Duration.between(LocalDateTime.now(), endTime).getSeconds();
    }

    // giá đặt cược tối thiểu tiếp theo
    public double minNextBid() {
        return totalBids == 0 ? startingPrice : currentPrice + minimumIncrement;
    }

    // anti sniping
    public boolean snipingCheck(int snipingSeconds) {
        if (!isActive()) return false;
        return LocalDateTime.now()
                .plusSeconds(snipingSeconds)
                .isAfter(endTime);
    }

    // thêm thời gian cho phiên
    public void extend(int seconds) {
        this.endTime = this.endTime.plusSeconds(seconds);
    }

    //
    public void transitionTo(AuctionStatus newStatus) {
        if (status != null && status.canTransitionTo(newStatus)) {
            this.status = newStatus;
        }
    }

    //Methods
    @Override
    public String toString() {
        return "Auction{" +
                "itemId=" + itemId +
                ", sellerId=" + sellerId +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", startingPrice=" + startingPrice +
                ", currentPrice=" + currentPrice +
                ", highestBidderId=" + highestBidderId +
                ", minimumIncrement=" + minimumIncrement +
                ", status=" + status +
                ", totalBids=" + totalBids +
                '}';
    }
}
