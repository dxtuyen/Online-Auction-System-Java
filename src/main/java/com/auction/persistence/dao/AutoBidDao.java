package com.auction.persistence.dao;

import com.auction.model.entity.AutoBid;

import java.util.List;
import java.util.UUID;

/**
 * Repository contract cho AutoBid.
 *
 * <p>Bảng auto_bids dùng (bidder_id, auction_id) làm composite PK → 1 user chỉ có
 * 1 auto-bid mỗi phiên. Khi user đăng ký lại → DELETE old + INSERT new.</p>
 */
public interface AutoBidDao {

    void insert(AutoBid autoBid);

    /** Xóa registration cũ của user cho auction này (idempotent). */
    void deleteByBidderAndAuction(UUID bidderId, UUID auctionId);

    /** Update flag active. Dùng khi BidManager gọi deactivate. */
    void updateActive(UUID bidderId, UUID auctionId, boolean active);

    List<AutoBid> findAll();

    long count();
}
