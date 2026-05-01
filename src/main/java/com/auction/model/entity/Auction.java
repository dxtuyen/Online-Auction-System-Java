package com.auction.model.entity;

import com.auction.model.enums.AuctionStatus;

import java.time.LocalDateTime;

/*
Phiên đấu giá: chứa thông tin của phiên đấu giá
*/

public class Auction extends Entity {

    private static final long serialVersionUID = 1L;

    private int itemId; //id của sản phẩm được bán trong phiên đấu giá
    private int sellerId; //id của người bán
    private LocalDateTime startTime; //thời gian bắt đầu phiên đấu giá
    private LocalDateTime endTime; //thời gian kết thúc phiên đấu giá
    private double startingPrice; //giá khởi điểm
    private double currentPrice; //giá cao nhất hiện tại
    private Integer highestBidderId; //id người trả giá cao nhất
    private double minimumIncrement; //bước nhảy giá nhỏ nhất
    private AuctionStatus status; //trạng thái của phiên đấu giá(PENDING/RUNNING/FINISHED/PAID/CANCELED)
    private int totalBids; //số lương bid trong phiên đấu giá

    public Auction() {
        super();
    }

    public Auction(int itemId, int sellerId, LocalDateTime startTime, LocalDateTime endTime, double startingPrice, double minimumIncrement) {
        super();
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.highestBidderId = null;
        this.minimumIncrement = minimumIncrement;
        this.status = AuctionStatus.PENDING;
        this.totalBids = 0;
    }

    public Auction(int id, int itemId, int sellerId, LocalDateTime startTime, LocalDateTime endTime, double startingPrice, double currentPrice, int highestBidderId, double minimumIncrement, AuctionStatus status, int totalBids) {
        super(id);
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.highestBidderId = highestBidderId;
        this.minimumIncrement = minimumIncrement;
        this.status = status;
        this.totalBids = totalBids;
    }
    
    //Getter & Setter
    public int getItemId() {
        return itemId;
    }
    
    public void setItemId(int itemId) {
        this.itemId = itemId;
    }
    
    public int getSellerId() {
        return sellerId;
    }
    
    public void setSellerId(int sellerId) {
        this.sellerId = sellerId;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    /**
     * Trả về id người trả giá cao nhất.
     *
     * <p><b>Lưu ý:</b> phương thức này có thể ném {@link NullPointerException} nếu chưa có ai bid
     * vì auto-unbox {@code Integer} sang {@code int}. Khi không chắc, dùng {@link #getHighestBidderIdOrNull()}.</p>
     */
    public int getHighestBidderID() {
        return highestBidderId;
    }

    /**
     * Phiên bản an toàn của {@link #getHighestBidderID()} — trả về {@code null} nếu chưa có bid nào.
     */
    public Integer getHighestBidderIdOrNull() {
        return highestBidderId;
    }

    /**
     * Set người dẫn đầu mới. Cho phép {@code null} để reset khi auction bị huỷ.
     */
    public void setHighestBidderId(Integer highestBidderId) {
        this.highestBidderId = highestBidderId;
    }

    public double getMinimumIncrement() {
        return minimumIncrement;
    }

    /** Setter chuẩn theo JavaBean convention (match với {@link #getMinimumIncrement()}). */
    public void setMinimumIncrement(double minimumIncrement) {
        this.minimumIncrement = minimumIncrement;
    }

    /**
     * @deprecated dùng {@link #setMinimumIncrement(double)} cho đúng convention.
     */
    @Deprecated
    public void setMinimumBidIncrement(double minimumIncrement) {
        setMinimumIncrement(minimumIncrement);
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public int getTotalBids() {
        return totalBids;
    }

    public void setTotalBids(int totalBids) {
        this.totalBids = totalBids;
    }

    public void incrementTotalBids() {
        this.totalBids++;
    }

    //Methods
    public boolean isActive() {
        return status == AuctionStatus.RUNNING && LocalDateTime.now().isAfter(startTime) && LocalDateTime.now().isBefore(endTime);
    }

    public boolean snipingCheck(int snipingSeconds) {
        if (!isActive()) return false;
        LocalDateTime now = LocalDateTime.now();
        return now.plusSeconds(snipingSeconds).isAfter(endTime);
    }

    public void extend(int seconds) {
        this.endTime = this.endTime.plusSeconds(seconds);
    }

    public long getRemainedSeconds() {
        if (!isActive()) return 0;
        return java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
    }

    public void transitionTo(AuctionStatus newStatus) {
        if (status.canTransitionTo(newStatus)) {
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
