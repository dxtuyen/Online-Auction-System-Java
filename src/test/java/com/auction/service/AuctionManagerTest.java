package com.auction.service;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.model.enums.Role;
import com.auction.model.exception.IllegalAuctionStateException;
import com.auction.testsupport.Reset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionManager service (H2, no scheduler)")
class AuctionManagerTest {

    private AuctionManager am;     // test-ctor: không scheduler
    private User seller;
    private User winner;
    private Item item;

    @BeforeEach
    void setUp() {
        Reset.all();
        am = AuctionManager.getInstance();

        UserManager um = UserManager.getInstance();
        seller = um.register("seller", "password123", "seller@example.com", "Seller",
                Role.NORMAL, new BigDecimal("1000000"));
        winner = um.register("winner", "password123", "winner@example.com", "Winner",
                Role.NORMAL, new BigDecimal("1000000"));

        item = ItemManager.getInstance().createItem(ItemCategory.ELECTRONICS, "Phone", "d",
                seller.getId(), new BigDecimal("1000"), List.of(),
                ItemCondition.NEW, Map.of("brand", "A", "model", "B"));
    }

    private Auction createAuction() {
        LocalDateTime now = LocalDateTime.now();
        return am.createAuction(item.getId(), seller.getId(),
                now.minusMinutes(1), now.plusHours(2),
                item.getStartingPrice(), new BigDecimal("100"));
    }

    /** Đưa phiên về FINISHED với winner là highest bidder. */
    private Auction finishedWithWinner() {
        Auction a = createAuction();
        a.transitionTo(AuctionStatus.RUNNING);
        a.placeBid(new BidTransaction(a.getId(), winner.getId(), new BigDecimal("1000")));
        a.transitionTo(AuctionStatus.FINISHED);
        return a;
    }

    // ============== createAuction ==============

    @Test
    @DisplayName("createAuction thành công, mặc định PENDING")
    void createAuction_success() {
        Auction a = createAuction();
        assertEquals(AuctionStatus.PENDING, a.getStatus());
        assertTrue(am.findById(a.getId()).isPresent());
        assertEquals(1, am.count());
    }

    @Test
    @DisplayName("createAuction seller không sở hữu item - throw")
    void createAuction_notOwner_throws() {
        User other = UserManager.getInstance().register(
                "other", "password123", "other@example.com", "Other", Role.NORMAL);
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () ->
                am.createAuction(item.getId(), other.getId(),
                        now.minusMinutes(1), now.plusHours(1),
                        item.getStartingPrice(), new BigDecimal("100")));
    }

    @Test
    @DisplayName("createAuction seller không tồn tại - throw")
    void createAuction_unknownSeller_throws() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () ->
                am.createAuction(item.getId(), UUID.randomUUID(),
                        now.minusMinutes(1), now.plusHours(1),
                        item.getStartingPrice(), new BigDecimal("100")));
    }

    // ============== register / unregister ==============

    @Test
    @DisplayName("register auction trùng id - throw")
    void register_duplicate_throws() {
        Auction a = createAuction();
        assertThrows(IllegalStateException.class, () -> am.register(a));
    }

    @Test
    @DisplayName("unregister gỡ khỏi cache")
    void unregister() {
        Auction a = createAuction();
        assertTrue(am.unregister(a.getId()));
        assertFalse(am.findById(a.getId()).isPresent());
        assertFalse(am.unregister(UUID.randomUUID()));
    }

    // ============== queries ==============

    @Test
    @DisplayName("findActive / findByStatus / findBySellerId")
    void queries() {
        Auction a = createAuction();
        a.transitionTo(AuctionStatus.RUNNING);

        assertEquals(1, am.findActive().size());
        assertEquals(1, am.findByStatus(AuctionStatus.RUNNING).size());
        assertEquals(0, am.findByStatus(AuctionStatus.PENDING).size());
        assertEquals(1, am.findBySellerId(seller.getId()).size());
        assertEquals(1, am.findAll().size());
    }

    // ============== closeAuction ==============

    @Test
    @DisplayName("closeAuction PENDING -> CANCELED")
    void close_pending() {
        Auction a = createAuction();
        am.closeAuction(a.getId(), seller.getId());
        assertEquals(AuctionStatus.CANCELED, a.getStatus());
    }

    @Test
    @DisplayName("closeAuction RUNNING không bid -> CANCELED")
    void close_runningNoBid() {
        Auction a = createAuction();
        a.transitionTo(AuctionStatus.RUNNING);
        am.closeAuction(a.getId(), seller.getId());
        assertEquals(AuctionStatus.CANCELED, a.getStatus());
    }

    @Test
    @DisplayName("closeAuction RUNNING có bid -> FINISHED")
    void close_runningWithBid() {
        Auction a = createAuction();
        a.transitionTo(AuctionStatus.RUNNING);
        a.placeBid(new BidTransaction(a.getId(), winner.getId(), new BigDecimal("1000")));
        am.closeAuction(a.getId(), seller.getId());
        assertEquals(AuctionStatus.FINISHED, a.getStatus());
    }

    @Test
    @DisplayName("closeAuction bởi người không phải seller - SecurityException")
    void close_notSeller_throws() {
        Auction a = createAuction();
        assertThrows(SecurityException.class,
                () -> am.closeAuction(a.getId(), winner.getId()));
    }

    @Test
    @DisplayName("closeAuction phiên không tồn tại - throw")
    void close_missing_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> am.closeAuction(UUID.randomUUID(), seller.getId()));
    }

    // ============== confirmPayment ==============

    @Test
    @DisplayName("confirmPayment thành công -> PAID, seller nhận revenue")
    void confirmPayment_success() {
        Auction a = finishedWithWinner();
        BigDecimal before = UserManager.getInstance().findById(seller.getId())
                .orElseThrow().getRevenue();

        am.confirmPayment(a.getId(), winner.getId());

        assertEquals(AuctionStatus.PAID, a.getStatus());
        BigDecimal after = UserManager.getInstance().findById(seller.getId())
                .orElseThrow().getRevenue();
        assertEquals(0, after.subtract(before).compareTo(new BigDecimal("1000")));
    }

    @Test
    @DisplayName("confirmPayment khi chưa FINISHED - throw")
    void confirmPayment_notFinished_throws() {
        Auction a = createAuction();
        assertThrows(IllegalAuctionStateException.class,
                () -> am.confirmPayment(a.getId(), winner.getId()));
    }

    @Test
    @DisplayName("confirmPayment bởi người không phải winner - SecurityException")
    void confirmPayment_notWinner_throws() {
        Auction a = finishedWithWinner();
        assertThrows(SecurityException.class,
                () -> am.confirmPayment(a.getId(), seller.getId()));
    }

    // ============== forfeitAuction ==============

    @Test
    @DisplayName("forfeitAuction -> CANCELED, seller nhận phí phạt")
    void forfeit_success() {
        Auction a = finishedWithWinner();
        BigDecimal sellerRevBefore = UserManager.getInstance().findById(seller.getId())
                .orElseThrow().getRevenue();

        am.forfeitAuction(a.getId(), winner.getId());

        assertEquals(AuctionStatus.CANCELED, a.getStatus());
        // penalty = startingPrice(1000) * 0.4 = 400
        BigDecimal sellerRevAfter = UserManager.getInstance().findById(seller.getId())
                .orElseThrow().getRevenue();
        assertEquals(0, sellerRevAfter.subtract(sellerRevBefore).compareTo(new BigDecimal("400.0")));
    }

    @Test
    @DisplayName("forfeitAuction khi chưa FINISHED - throw")
    void forfeit_notFinished_throws() {
        Auction a = createAuction();
        assertThrows(IllegalAuctionStateException.class,
                () -> am.forfeitAuction(a.getId(), winner.getId()));
    }

    // ============== configureAntiSniping ==============

    @Test
    @DisplayName("configureAntiSniping tham số không hợp lệ - throw")
    void configureAntiSniping_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> am.configureAntiSniping(-1, 30));
        assertThrows(IllegalArgumentException.class, () -> am.configureAntiSniping(10, 0));
    }

    @Test
    @DisplayName("configureAntiSniping hợp lệ - không throw")
    void configureAntiSniping_valid() {
        assertDoesNotThrow(() -> am.configureAntiSniping(5, 15));
    }
}
