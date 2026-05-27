package com.auction.service;

import com.auction.model.entity.Auction;
import com.auction.model.entity.AutoBid;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.model.enums.Role;
import com.auction.model.exception.InsufficientBalanceException;
import com.auction.model.exception.InvalidBidException;
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

@DisplayName("BidManager service (H2, singleton)")
class BidManagerTest {

    private final BidManager bidManager = BidManager.getInstance();
    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private final UserManager userManager = UserManager.getInstance();

    private User seller;
    private User bidder1;
    private User bidder2;
    private Auction auction;

    @BeforeEach
    void setUp() {
        Reset.all();
        seller = userManager.register("seller", "password123", "seller@example.com",
                "Seller", Role.NORMAL, new BigDecimal("1000000"));
        bidder1 = userManager.register("bidder1", "password123", "b1@example.com",
                "Bidder1", Role.NORMAL, new BigDecimal("1000000"));
        bidder2 = userManager.register("bidder2", "password123", "b2@example.com",
                "Bidder2", Role.NORMAL, new BigDecimal("1000000"));

        Item item = ItemManager.getInstance().createItem(ItemCategory.ELECTRONICS, "Phone", "d",
                seller.getId(), new BigDecimal("1000"), List.of(),
                ItemCondition.NEW, Map.of("brand", "A", "model", "B"));

        LocalDateTime now = LocalDateTime.now();
        auction = auctionManager.createAuction(item.getId(), seller.getId(),
                now.minusMinutes(1), now.plusHours(2),
                item.getStartingPrice(), new BigDecimal("100"));
        ensureRunning(auction);
    }

    /** RUNNING-hoá phiên; bỏ qua race với scheduler nếu nó đã chuyển trước. */
    private void ensureRunning(Auction a) {
        if (a.getStatus() == AuctionStatus.PENDING) {
            try {
                a.transitionTo(AuctionStatus.RUNNING);
            } catch (RuntimeException ignored) { /* scheduler đã chuyển */ }
        }
    }

    // ============== placeBid ==============

    @Test
    @DisplayName("placeBid hợp lệ - cập nhật leader + reserve balance")
    void placeBid_success() {
        BidTransaction bid = bidManager.placeBid(auction.getId(), bidder1.getId(),
                new BigDecimal("1000"));

        assertNotNull(bid);
        assertEquals(bidder1.getId(), auction.getHighestBidderId());
        // balance giảm đúng số tiền reserve
        BigDecimal bal = userManager.findById(bidder1.getId()).orElseThrow().getBalance();
        assertEquals(0, bal.compareTo(new BigDecimal("999000")));
    }

    @Test
    @DisplayName("placeBid dưới giá tối thiểu - InvalidBidException")
    void placeBid_belowMin_throws() {
        bidManager.placeBid(auction.getId(), bidder1.getId(), new BigDecimal("1000"));
        assertThrows(InvalidBidException.class,
                () -> bidManager.placeBid(auction.getId(), bidder2.getId(), new BigDecimal("1050")));
    }

    @Test
    @DisplayName("placeBid vượt số dư - InsufficientBalanceException")
    void placeBid_insufficientBalance_throws() {
        User poor = userManager.register("poor", "password123", "poor@example.com",
                "Poor", Role.NORMAL, new BigDecimal("500"));
        assertThrows(InsufficientBalanceException.class,
                () -> bidManager.placeBid(auction.getId(), poor.getId(), new BigDecimal("1000")));
    }

    @Test
    @DisplayName("placeBid outbid - hoàn tiền cho leader cũ")
    void placeBid_outbid_refundsPrevious() {
        bidManager.placeBid(auction.getId(), bidder1.getId(), new BigDecimal("1000"));
        bidManager.placeBid(auction.getId(), bidder2.getId(), new BigDecimal("1100"));

        // bidder1 được hoàn lại 1000 → về full balance
        BigDecimal bal1 = userManager.findById(bidder1.getId()).orElseThrow().getBalance();
        assertEquals(0, bal1.compareTo(new BigDecimal("1000000")));
        assertEquals(bidder2.getId(), auction.getHighestBidderId());
    }

    @Test
    @DisplayName("placeBid user không tồn tại - InvalidBidException")
    void placeBid_unknownUser_throws() {
        assertThrows(InvalidBidException.class,
                () -> bidManager.placeBid(auction.getId(), UUID.randomUUID(), new BigDecimal("1000")));
    }

    @Test
    @DisplayName("placeBid auction không tồn tại - throw")
    void placeBid_unknownAuction_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> bidManager.placeBid(UUID.randomUUID(), bidder1.getId(), new BigDecimal("1000")));
    }

    // ============== getBidHistory ==============

    @Test
    @DisplayName("getBidHistory trả về các bid theo thứ tự")
    void getBidHistory() {
        bidManager.placeBid(auction.getId(), bidder1.getId(), new BigDecimal("1000"));
        bidManager.placeBid(auction.getId(), bidder2.getId(), new BigDecimal("1100"));

        List<BidTransaction> history = bidManager.getBidHistory(auction.getId());
        assertEquals(2, history.size());
    }

    @Test
    @DisplayName("getBidHistory phiên chưa có bid - rỗng")
    void getBidHistory_empty() {
        assertTrue(bidManager.getBidHistory(UUID.randomUUID()).isEmpty());
    }

    // ============== auto-bid ==============

    @Test
    @DisplayName("registerAutoBid + trigger proxy-bid khi có người bid")
    void autoBid_triggered() {
        // bidder2 đăng ký auto-bid tối đa 2000
        AutoBid ab = bidManager.registerAutoBid(auction.getId(), bidder2.getId(),
                new BigDecimal("2000"), new BigDecimal("100"));
        assertTrue(ab.isActive());

        // bidder1 đặt bid 1000 → kích hoạt auto-bid của bidder2 lên 1100
        bidManager.placeBid(auction.getId(), bidder1.getId(), new BigDecimal("1000"));

        assertEquals(bidder2.getId(), auction.getHighestBidderId());
        assertEquals(0, auction.getCurrentPrice().compareTo(new BigDecimal("1100")));
    }

    @Test
    @DisplayName("registerAutoBid cho phiên của chính seller - throw")
    void autoBid_sellerOwnAuction_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> bidManager.registerAutoBid(auction.getId(), seller.getId(),
                        new BigDecimal("2000"), new BigDecimal("100")));
    }

    @Test
    @DisplayName("getAutoBids trả về auto-bid đã đăng ký")
    void getAutoBids() {
        bidManager.registerAutoBid(auction.getId(), bidder2.getId(),
                new BigDecimal("2000"), new BigDecimal("100"));
        assertEquals(1, bidManager.getAutoBids(auction.getId()).size());
    }

    @Test
    @DisplayName("registerAutoBid trên phiên đã FINISHED - throw")
    void autoBid_finishedAuction_throws() {
        auction.placeBid(new BidTransaction(auction.getId(), bidder1.getId(), new BigDecimal("1000")));
        auctionManager.closeAuction(auction.getId(), seller.getId()); // -> FINISHED
        assertThrows(IllegalStateException.class,
                () -> bidManager.registerAutoBid(auction.getId(), bidder2.getId(),
                        new BigDecimal("2000"), new BigDecimal("100")));
    }
}
