package com.auction.persistence.dao;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.User;
import com.auction.testsupport.Fixtures;
import com.auction.testsupport.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MysqlBidTransactionDao (H2)")
class MysqlBidTransactionDaoTest {

    private final BidTransactionDao dao = new MysqlBidTransactionDao();
    private UUID auctionId;
    private UUID bidderId;

    @BeforeEach
    void setUp() {
        TestDb.clean();
        User seller = Fixtures.persistUser();
        UUID itemId = Fixtures.persistItem(seller.getId()).getId();
        Auction a = Fixtures.persistRunningAuction(itemId, seller.getId());
        auctionId = a.getId();
        bidderId = Fixtures.persistUser().getId();
    }

    @Test
    @DisplayName("insert + findByAuctionId")
    void insertAndFindByAuction() {
        BidTransaction bid = new BidTransaction(auctionId, bidderId, new BigDecimal("1500"));
        bid.markValid();
        dao.insert(bid);

        List<BidTransaction> bids = dao.findByAuctionId(auctionId);
        assertEquals(1, bids.size());
        assertEquals(0, new BigDecimal("1500").compareTo(bids.get(0).getBidAmount()));
    }

    @Test
    @DisplayName("findAll + count")
    void findAllAndCount() {
        dao.insert(new BidTransaction(auctionId, bidderId, new BigDecimal("1100")));
        dao.insert(new BidTransaction(auctionId, bidderId, new BigDecimal("1200")));
        assertEquals(2, dao.findAll().size());
        assertEquals(2, dao.count());
    }

    @Test
    @DisplayName("findByAuctionId trả về rỗng khi không có bid")
    void findByAuction_empty() {
        assertTrue(dao.findByAuctionId(UUID.randomUUID()).isEmpty());
    }
}
