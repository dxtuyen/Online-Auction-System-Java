package com.auction.model.observer;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.enums.AuctionStatus;

/**
 * Observer Pattern - đăng ký để nhận event từ Auction.
 *
 * Tại sao dùng default method?
 *  Để observer chỉ phải override event NÓ QUAN TÂM, không bắt buộc impl hết.
 *  Vd: ChartObserver chỉ cần onBidPlaced, không cần onAuctionExtended.
 */
public interface AuctionObserver {

    /** Khi có bid mới được chấp nhận */
    default void onBidPlaced(Auction auction, BidTransaction bid) {}

    /** Khi auction bị extend do anti-sniping */
    default void onAuctionExtended(Auction auction, int extendedSeconds) {}

    /** Khi auction đổi trạng thái (PENDING -> RUNNING -> FINISHED...) */
    default void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {}
}