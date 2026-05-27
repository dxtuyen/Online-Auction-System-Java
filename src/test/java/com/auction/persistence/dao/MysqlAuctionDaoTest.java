package com.auction.persistence.dao;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.testsupport.Fixtures;
import com.auction.testsupport.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MysqlAuctionDao (H2)")
class MysqlAuctionDaoTest {

    private final AuctionDao dao = new MysqlAuctionDao();
    private UUID sellerId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        TestDb.clean();
        User seller = Fixtures.persistUser();
        sellerId = seller.getId();
        itemId = Fixtures.persistItem(sellerId).getId();
    }

    @Test
    @DisplayName("insert + findById round-trip")
    void insertAndFind() {
        Auction a = Fixtures.persistRunningAuction(itemId, sellerId);
        Auction found = dao.findById(a.getId()).orElseThrow();
        assertEquals(itemId, found.getItemId());
        assertEquals(sellerId, found.getSellerId());
        assertEquals(AuctionStatus.RUNNING, found.getStatus());
        assertNull(found.getHighestBidderId());
    }

    @Test
    @DisplayName("update lưu currentPrice, highestBidder, status, totalBids")
    void update() {
        Auction a = Fixtures.persistRunningAuction(itemId, sellerId);
        User bidder = Fixtures.persistUser();

        BidTransaction bid = new BidTransaction(a.getId(), bidder.getId(), new BigDecimal("1500"));
        a.placeBid(bid);          // cập nhật state trong RAM
        dao.update(a);

        Auction reloaded = dao.findById(a.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1500").compareTo(reloaded.getCurrentPrice()));
        assertEquals(bidder.getId(), reloaded.getHighestBidderId());
        assertEquals(1, reloaded.getTotalBids());
    }

    @Test
    @DisplayName("update status transition lưu được")
    void updateStatus() {
        Auction a = Fixtures.persistPendingAuction(itemId, sellerId);
        a.transitionTo(AuctionStatus.CANCELED);
        dao.update(a);
        assertEquals(AuctionStatus.CANCELED, dao.findById(a.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("findAll + count")
    void findAllAndCount() {
        Fixtures.persistRunningAuction(itemId, sellerId);
        assertEquals(1, dao.findAll().size());
        assertEquals(1, dao.count());
    }

    @Test
    @DisplayName("findById không tồn tại - empty")
    void findById_missing() {
        assertTrue(dao.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("update auction không tồn tại - throw")
    void update_missing_throws() {
        Auction ghost = new Auction(UUID.randomUUID(), sellerId,
                java.time.LocalDateTime.now().minusHours(1),
                java.time.LocalDateTime.now().plusHours(1),
                new BigDecimal("100"), new BigDecimal("10"));
        assertThrows(RuntimeException.class, () -> dao.update(ghost));
    }
}
