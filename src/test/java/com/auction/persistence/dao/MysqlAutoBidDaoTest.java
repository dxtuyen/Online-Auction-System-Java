package com.auction.persistence.dao;

import com.auction.model.entity.Auction;
import com.auction.model.entity.AutoBid;
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

@DisplayName("MysqlAutoBidDao (H2)")
class MysqlAutoBidDaoTest {

    private final AutoBidDao dao = new MysqlAutoBidDao();
    private UUID auctionId;
    private UUID bidderId;

    @BeforeEach
    void setUp() {
        TestDb.clean();
        User seller = Fixtures.persistUser();
        UUID itemId = Fixtures.persistItem(seller.getId()).getId();
        auctionId = Fixtures.persistRunningAuction(itemId, seller.getId()).getId();
        bidderId = Fixtures.persistUser().getId();
    }

    @Test
    @DisplayName("insert + findAll")
    void insertAndFindAll() {
        dao.insert(new AutoBid(bidderId, auctionId, new BigDecimal("5000"), new BigDecimal("100")));
        List<AutoBid> all = dao.findAll();
        assertEquals(1, all.size());
        assertTrue(all.get(0).isActive());
        assertEquals(1, dao.count());
    }

    @Test
    @DisplayName("updateActive đổi cờ active")
    void updateActive() {
        dao.insert(new AutoBid(bidderId, auctionId, new BigDecimal("5000"), new BigDecimal("100")));
        dao.updateActive(bidderId, auctionId, false);
        assertFalse(dao.findAll().get(0).isActive());
    }

    @Test
    @DisplayName("deleteByBidderAndAuction xóa record")
    void delete() {
        dao.insert(new AutoBid(bidderId, auctionId, new BigDecimal("5000"), new BigDecimal("100")));
        dao.deleteByBidderAndAuction(bidderId, auctionId);
        assertEquals(0, dao.count());
    }

    @Test
    @DisplayName("updateActive trên record không tồn tại - không throw (idempotent)")
    void updateActive_missing_noThrow() {
        assertDoesNotThrow(() -> dao.updateActive(bidderId, auctionId, false));
    }
}
