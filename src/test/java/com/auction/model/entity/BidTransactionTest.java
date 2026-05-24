package com.auction.model.entity;

import com.auction.model.enums.BidStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BidTransaction entity")
class BidTransactionTest {

    private static final UUID AUCTION_ID = UUID.randomUUID();
    private static final UUID BIDDER_ID = UUID.randomUUID();

    private BidTransaction newBid(BigDecimal amount) {
        return new BidTransaction(AUCTION_ID, BIDDER_ID, amount);
    }

    // ========== CONSTRUCTOR ==========

    @Test
    @DisplayName("Tạo bid mới - status mặc định là PENDING")
    void constructor_newBid_statusIsPending() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        assertEquals(BidStatus.PENDING, bid.getStatus());
    }

    @Test
    @DisplayName("bidAmount = 0 - throw")
    void constructor_zeroAmount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> newBid(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("bidAmount âm - throw")
    void constructor_negativeAmount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> newBid(new BigDecimal("-100")));
    }

    @Test
    @DisplayName("auctionId null - throw")
    void constructor_nullAuctionId_throws() {
        assertThrows(NullPointerException.class,
                () -> new BidTransaction(null, BIDDER_ID, new BigDecimal("100")));
    }

    @Test
    @DisplayName("bidderId null - throw")
    void constructor_nullBidderId_throws() {
        assertThrows(NullPointerException.class,
                () -> new BidTransaction(AUCTION_ID, null, new BigDecimal("100")));
    }

    @Test
    @DisplayName("Tạo bid - ID và timestamp được sinh tự động")
    void constructor_validBid_hasIdAndTimestamp() {
        BidTransaction bid = newBid(new BigDecimal("1000"));
        assertNotNull(bid.getId());
        assertNotNull(bid.getCreatedAt());
        assertNotNull(bid.getTimestamp());
        assertEquals(bid.getCreatedAt(), bid.getTimestamp());
    }

    // ========== STATE TRANSITIONS ==========

    @Test
    @DisplayName("PENDING → VALID qua markValid() - thành công")
    void markValid_fromPending_success() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        bid.markValid();
        assertEquals(BidStatus.VALID, bid.getStatus());
    }

    @Test
    @DisplayName("PENDING → REJECTED qua reject() - thành công")
    void reject_fromPending_success() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        bid.reject();
        assertEquals(BidStatus.REJECTED, bid.getStatus());
    }

    @Test
    @DisplayName("PENDING → CANCELED qua cancel() - thành công")
    void cancel_fromPending_success() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        bid.cancel();
        assertEquals(BidStatus.CANCELED, bid.getStatus());
    }

    @Test
    @DisplayName("VALID → OUTBID qua markOutbid() - thành công")
    void markOutbid_fromValid_success() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        bid.markValid();
        bid.markOutbid();
        assertEquals(BidStatus.OUTBID, bid.getStatus());
    }

    @Test
    @DisplayName("REJECTED → VALID - throw (terminal state)")
    void markValid_fromRejected_throws() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        bid.reject();
        assertThrows(IllegalStateException.class, bid::markValid);
    }

    @Test
    @DisplayName("CANCELED → VALID - throw (terminal state)")
    void markValid_fromCanceled_throws() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        bid.cancel();
        assertThrows(IllegalStateException.class, bid::markValid);
    }

    @Test
    @DisplayName("Chuyển sang cùng status - không thay đổi (idempotent check)")
    void changeStatus_sameStatus_noException() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        // PENDING → PENDING: không throw (khi gọi lại cùng status)
        assertDoesNotThrow(bid::reject);
        // REJECTED → REJECTED: không throw
        assertDoesNotThrow(bid::reject);
    }

    // ========== IMMUTABLE FIELDS ==========

    @Test
    @DisplayName("auctionId không thay đổi sau khi tạo")
    void getAuctionId_returnsOriginalValue() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        assertEquals(AUCTION_ID, bid.getAuctionId());
    }

    @Test
    @DisplayName("bidderId không thay đổi sau khi tạo")
    void getBidderId_returnsOriginalValue() {
        BidTransaction bid = newBid(new BigDecimal("500"));
        assertEquals(BIDDER_ID, bid.getBidderId());
    }

    @Test
    @DisplayName("bidAmount không thay đổi sau khi tạo")
    void getBidAmount_returnsOriginalValue() {
        BigDecimal amount = new BigDecimal("12345");
        BidTransaction bid = newBid(amount);
        assertEquals(amount, bid.getBidAmount());
    }
}
