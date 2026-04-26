package com.auction.observer;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;

/**
 * Observer pattern — các đối tượng muốn nhận thông báo từ AuctionEventManager
 * sẽ implement interface này.
 *
 * <p>Ví dụ ClientHandler (mỗi client kết nối server) sẽ implement AuctionObserver
 * và "subscribe" để khi có bid mới → server push JSON xuống client đó.</p>
 */
public interface AuctionObserver {

    /** Được gọi khi có 1 bid mới hợp lệ. */
    void onNewBid(Auction auction, BidTransaction bid);

    /** Được gọi khi trạng thái phiên thay đổi (RUNNING → FINISHED, CANCELED,...). */
    void onAuctionStatusChanged(Auction auction);

    /** Được gọi khi phiên được gia hạn (anti-sniping). */
    void onAuctionExtended(Auction auction);
}
