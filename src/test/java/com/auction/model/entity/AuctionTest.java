package com.auction.model.entity;

import com.auction.model.enums.AuctionStatus;
import com.auction.model.exception.AuctionClosedException;
import com.auction.model.exception.IllegalAuctionStateException;
import com.auction.model.exception.InvalidBidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Auction entity")
class AuctionTest {

    private static final BigDecimal STARTING_PRICE = new BigDecimal("1000");
    private static final BigDecimal MIN_INCREMENT = new BigDecimal("100");

    private UUID itemId;
    private UUID sellerId;
    private UUID bidderId;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        bidderId = UUID.randomUUID();
    }

    /** Tạo auction PENDING với thời gian hợp lệ (start quá khứ, end tương lai). */
    private Auction pendingAuction() {
        LocalDateTime now = LocalDateTime.now();
        return new Auction(itemId, sellerId,
                now.minusHours(1), now.plusHours(1),
                STARTING_PRICE, MIN_INCREMENT);
    }

    /** Tạo auction đã RUNNING (start quá khứ, end tương lai, chuyển sang RUNNING). */
    private Auction runningAuction() {
        Auction a = pendingAuction();
        a.transitionTo(AuctionStatus.RUNNING);
        return a;
    }

    private BidTransaction bid(UUID bidder, BigDecimal amount) {
        return new BidTransaction(itemId /* dùng auctionId thật sau */, bidder, amount);
    }

    // ========== CONSTRUCTOR ==========

    @Test
    @DisplayName("Auction mới có status PENDING và totalBids = 0")
    void constructor_newAuction_pendingAndZeroBids() {
        Auction a = pendingAuction();
        assertEquals(AuctionStatus.PENDING, a.getStatus());
        assertEquals(0, a.getTotalBids());
        assertNull(a.getHighestBidderId());
        assertEquals(STARTING_PRICE, a.getCurrentPrice());
    }

    @Test
    @DisplayName("endTime trước startTime - throw")
    void constructor_endBeforeStart_throws() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Auction(itemId, sellerId,
                        now.plusHours(2), now.plusHours(1),
                        STARTING_PRICE, MIN_INCREMENT));
    }

    @Test
    @DisplayName("endTime bằng startTime - throw")
    void constructor_endEqualsStart_throws() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Auction(itemId, sellerId, now, now, STARTING_PRICE, MIN_INCREMENT));
    }

    @Test
    @DisplayName("startingPrice âm - throw")
    void constructor_negativeStartingPrice_throws() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Auction(itemId, sellerId,
                        now.minusHours(1), now.plusHours(1),
                        new BigDecimal("-1"), MIN_INCREMENT));
    }

    @Test
    @DisplayName("minimumIncrement = 0 - throw")
    void constructor_zeroIncrement_throws() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Auction(itemId, sellerId,
                        now.minusHours(1), now.plusHours(1),
                        STARTING_PRICE, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("itemId null - throw")
    void constructor_nullItemId_throws() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(NullPointerException.class,
                () -> new Auction(null, sellerId,
                        now.minusHours(1), now.plusHours(1),
                        STARTING_PRICE, MIN_INCREMENT));
    }

    // ========== minNextBid ==========

    @Test
    @DisplayName("minNextBid khi chưa có bid - trả về startingPrice")
    void minNextBid_noBids_returnsStartingPrice() {
        assertEquals(STARTING_PRICE, runningAuction().minNextBid());
    }

    @Test
    @DisplayName("minNextBid sau khi có bid - trả về currentPrice + minimumIncrement")
    void minNextBid_afterBid_returnsPricesPlusIncrement() {
        Auction a = runningAuction();
        BidTransaction b = new BidTransaction(a.getId(), bidderId, new BigDecimal("1000"));
        a.placeBid(b);

        assertEquals(new BigDecimal("1100"), a.minNextBid());
    }

    // ========== placeBid ==========

    @Test
    @DisplayName("Bid hợp lệ - cập nhật currentPrice và highestBidder")
    void placeBid_validBid_updatesState() {
        Auction a = runningAuction();
        UUID bidder = UUID.randomUUID();
        BidTransaction b = new BidTransaction(a.getId(), bidder, new BigDecimal("1500"));

        a.placeBid(b);

        assertEquals(new BigDecimal("1500"), a.getCurrentPrice());
        assertEquals(bidder, a.getHighestBidderId());
        assertEquals(1, a.getTotalBids());
    }

    @Test
    @DisplayName("Bid đúng startingPrice (bid đầu tiên) - chấp nhận")
    void placeBid_exactStartingPrice_accepted() {
        Auction a = runningAuction();
        BidTransaction b = new BidTransaction(a.getId(), bidderId, STARTING_PRICE);
        assertDoesNotThrow(() -> a.placeBid(b));
        assertEquals(STARTING_PRICE, a.getCurrentPrice());
    }

    @Test
    @DisplayName("Bid dưới minNextBid - throw InvalidBidException")
    void placeBid_belowMinimum_throws() {
        Auction a = runningAuction();
        // Đặt bid đầu tại 1000, thì min tiếp = 1100
        BidTransaction first = new BidTransaction(a.getId(), UUID.randomUUID(), new BigDecimal("1000"));
        a.placeBid(first);

        BidTransaction low = new BidTransaction(a.getId(), bidderId, new BigDecimal("1050"));
        assertThrows(InvalidBidException.class, () -> a.placeBid(low));
    }

    @Test
    @DisplayName("Seller tự bid - throw InvalidBidException")
    void placeBid_sellerBids_throws() {
        Auction a = runningAuction();
        BidTransaction b = new BidTransaction(a.getId(), sellerId, new BigDecimal("2000"));
        assertThrows(InvalidBidException.class, () -> a.placeBid(b));
    }

    @Test
    @DisplayName("Bid trên auction PENDING - throw AuctionClosedException")
    void placeBid_pendingAuction_throws() {
        Auction a = pendingAuction();
        BidTransaction b = new BidTransaction(a.getId(), bidderId, new BigDecimal("2000"));
        assertThrows(AuctionClosedException.class, () -> a.placeBid(b));
    }

    @Test
    @DisplayName("Bid trên auction CANCELED - throw AuctionClosedException")
    void placeBid_canceledAuction_throws() {
        Auction a = pendingAuction();
        a.transitionTo(AuctionStatus.CANCELED);
        BidTransaction b = new BidTransaction(a.getId(), bidderId, new BigDecimal("2000"));
        assertThrows(AuctionClosedException.class, () -> a.placeBid(b));
    }

    @Test
    @DisplayName("Bid đầu tiên - BidOutcome.previousBidderId = null")
    void placeBid_firstBid_outcomeHasNoPrevious() {
        Auction a = runningAuction();
        BidTransaction b = new BidTransaction(a.getId(), bidderId, new BigDecimal("1000"));
        Auction.BidOutcome outcome = a.placeBid(b);

        assertNull(outcome.previousBidderId());
        assertNull(outcome.previousAmount());
    }

    @Test
    @DisplayName("Bid thứ hai - BidOutcome trả về bidder cũ và giá cũ")
    void placeBid_secondBid_outcomeHasPrevious() {
        Auction a = runningAuction();
        UUID firstBidder = UUID.randomUUID();
        BidTransaction first = new BidTransaction(a.getId(), firstBidder, new BigDecimal("1000"));
        a.placeBid(first);

        BidTransaction second = new BidTransaction(a.getId(), bidderId, new BigDecimal("1100"));
        Auction.BidOutcome outcome = a.placeBid(second);

        assertEquals(firstBidder, outcome.previousBidderId());
        assertEquals(new BigDecimal("1000"), outcome.previousAmount());
    }

    @Test
    @DisplayName("Bid sai auctionId - throw InvalidBidException")
    void placeBid_wrongAuctionId_throws() {
        Auction a = runningAuction();
        BidTransaction b = new BidTransaction(UUID.randomUUID(), bidderId, new BigDecimal("2000"));
        assertThrows(InvalidBidException.class, () -> a.placeBid(b));
    }

    // ========== transitionTo (STATE MACHINE) ==========

    @Test
    @DisplayName("PENDING → RUNNING - hợp lệ")
    void transitionTo_pendingToRunning_succeeds() {
        Auction a = pendingAuction();
        assertDoesNotThrow(() -> a.transitionTo(AuctionStatus.RUNNING));
        assertEquals(AuctionStatus.RUNNING, a.getStatus());
    }

    @Test
    @DisplayName("PENDING → CANCELED - hợp lệ")
    void transitionTo_pendingToCanceled_succeeds() {
        Auction a = pendingAuction();
        assertDoesNotThrow(() -> a.transitionTo(AuctionStatus.CANCELED));
        assertEquals(AuctionStatus.CANCELED, a.getStatus());
    }

    @Test
    @DisplayName("RUNNING → FINISHED - hợp lệ")
    void transitionTo_runningToFinished_succeeds() {
        Auction a = runningAuction();
        assertDoesNotThrow(() -> a.transitionTo(AuctionStatus.FINISHED));
        assertEquals(AuctionStatus.FINISHED, a.getStatus());
    }

    @Test
    @DisplayName("FINISHED → PAID - hợp lệ")
    void transitionTo_finishedToPaid_succeeds() {
        Auction a = runningAuction();
        a.transitionTo(AuctionStatus.FINISHED);
        assertDoesNotThrow(() -> a.transitionTo(AuctionStatus.PAID));
        assertEquals(AuctionStatus.PAID, a.getStatus());
    }

    @Test
    @DisplayName("PENDING → PAID trực tiếp - throw (không hợp lệ)")
    void transitionTo_pendingToPaid_throws() {
        Auction a = pendingAuction();
        assertThrows(IllegalAuctionStateException.class,
                () -> a.transitionTo(AuctionStatus.PAID));
    }

    @Test
    @DisplayName("PAID → CANCELED - throw (terminal state)")
    void transitionTo_paidToCanceled_throws() {
        Auction a = runningAuction();
        a.transitionTo(AuctionStatus.FINISHED);
        a.transitionTo(AuctionStatus.PAID);
        assertThrows(IllegalAuctionStateException.class,
                () -> a.transitionTo(AuctionStatus.CANCELED));
    }

    @Test
    @DisplayName("CANCELED → bất kỳ - throw (terminal state)")
    void transitionTo_canceledToAny_throws() {
        Auction a = pendingAuction();
        a.transitionTo(AuctionStatus.CANCELED);
        assertThrows(IllegalAuctionStateException.class,
                () -> a.transitionTo(AuctionStatus.RUNNING));
    }

    // ========== extend ==========

    @Test
    @DisplayName("extend() trên RUNNING - cộng thêm giây vào endTime")
    void extend_runningAuction_extendsEndTime() {
        Auction a = runningAuction();
        LocalDateTime before = a.getEndTime();

        a.extend(60);

        assertEquals(before.plusSeconds(60), a.getEndTime());
    }

    @Test
    @DisplayName("extend() với seconds <= 0 - throw")
    void extend_nonPositiveSeconds_throws() {
        Auction a = runningAuction();
        assertThrows(IllegalArgumentException.class, () -> a.extend(0));
        assertThrows(IllegalArgumentException.class, () -> a.extend(-10));
    }

    @Test
    @DisplayName("extend() trên PENDING - throw (chỉ cho RUNNING)")
    void extend_pendingAuction_throws() {
        Auction a = pendingAuction();
        assertThrows(IllegalAuctionStateException.class, () -> a.extend(30));
    }

    // ========== isInSnipingWindow ==========

    @Test
    @DisplayName("Trong cửa sổ anti-sniping - trả về true")
    void isInSnipingWindow_endingSoon_true() {
        LocalDateTime now = LocalDateTime.now();
        Auction a = new Auction(itemId, sellerId,
                now.minusHours(1), now.plusSeconds(5),
                STARTING_PRICE, MIN_INCREMENT);
        a.transitionTo(AuctionStatus.RUNNING);

        assertTrue(a.isInSnipingWindow(10));
    }

    @Test
    @DisplayName("Ngoài cửa sổ anti-sniping - trả về false")
    void isInSnipingWindow_notEndingSoon_false() {
        Auction a = runningAuction(); // endTime = now + 1 hour
        assertFalse(a.isInSnipingWindow(10));
    }

    @Test
    @DisplayName("Auction PENDING - isInSnipingWindow = false")
    void isInSnipingWindow_pendingAuction_false() {
        assertFalse(pendingAuction().isInSnipingWindow(999));
    }

    // ========== Observer ==========

    @Test
    @DisplayName("Observer nhận được onBidPlaced khi có bid")
    void observer_onBidPlaced_calledOnValidBid() {
        Auction a = runningAuction();
        boolean[] called = {false};
        a.addObserver(new com.auction.model.observer.AuctionObserver() {
            @Override public void onBidPlaced(Auction auction, BidTransaction bid) { called[0] = true; }
            @Override public void onAuctionExtended(Auction auction, int seconds) {}
            @Override public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {}
        });

        BidTransaction b = new BidTransaction(a.getId(), bidderId, new BigDecimal("1000"));
        a.placeBid(b);

        assertTrue(called[0]);
    }

    @Test
    @DisplayName("Observer nhận được onStatusChanged khi transitionTo")
    void observer_onStatusChanged_calledOnTransition() {
        Auction a = pendingAuction();
        AuctionStatus[] captured = {null, null};
        a.addObserver(new com.auction.model.observer.AuctionObserver() {
            @Override public void onBidPlaced(Auction auction, BidTransaction bid) {}
            @Override public void onAuctionExtended(Auction auction, int seconds) {}
            @Override public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
                captured[0] = oldStatus;
                captured[1] = newStatus;
            }
        });

        a.transitionTo(AuctionStatus.RUNNING);

        assertEquals(AuctionStatus.PENDING, captured[0]);
        assertEquals(AuctionStatus.RUNNING, captured[1]);
    }
}
