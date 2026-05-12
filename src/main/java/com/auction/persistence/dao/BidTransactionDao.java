package com.auction.persistence.dao;

import com.auction.model.entity.BidTransaction;

import java.util.List;
import java.util.UUID;

/**
 * Repository contract cho BidTransaction.
 *
 * <p>Bid status được finalize TRƯỚC khi insert (markValid xong rồi mới recordBid).
 * Vì vậy không có method update — chỉ insert + read.</p>
 */
public interface BidTransactionDao {

    /** Insert bid với status đã finalize (VALID/REJECTED). */
    void insert(BidTransaction bid);

    /** Load tất cả bid - cho bootstrap, group theo auctionId ở caller. */
    List<BidTransaction> findAll();

    /** Lịch sử bid theo phiên - không cần dùng khi đã có cache. */
    List<BidTransaction> findByAuctionId(UUID auctionId);

    long count();
}
