package com.auction.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoBid entity")
class AutoBidTest {

    private static final UUID BIDDER = UUID.randomUUID();
    private static final UUID AUCTION = UUID.randomUUID();

    @Test
    @DisplayName("Tạo auto-bid mới - active = true")
    void constructor_newAutoBid_active() {
        AutoBid ab = new AutoBid(BIDDER, AUCTION, new BigDecimal("5000"), new BigDecimal("100"));
        assertTrue(ab.isActive());
        assertEquals(BIDDER, ab.getBidderId());
        assertEquals(AUCTION, ab.getAuctionId());
        assertEquals(new BigDecimal("5000"), ab.getMaxBid());
        assertEquals(new BigDecimal("100"), ab.getIncrement());
        assertNotNull(ab.getCreatedAt());
    }

    @Test
    @DisplayName("deactivate() chuyển active = false")
    void deactivate() {
        AutoBid ab = new AutoBid(BIDDER, AUCTION, new BigDecimal("5000"), new BigDecimal("100"));
        ab.deactivate();
        assertFalse(ab.isActive());
    }

    @Test
    @DisplayName("maxBid <= 0 - throw")
    void constructor_nonPositiveMaxBid_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new AutoBid(BIDDER, AUCTION, BigDecimal.ZERO, new BigDecimal("100")));
    }

    @Test
    @DisplayName("increment <= 0 - throw")
    void constructor_nonPositiveIncrement_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new AutoBid(BIDDER, AUCTION, new BigDecimal("5000"), new BigDecimal("-1")));
    }

    @Test
    @DisplayName("bidderId null - throw")
    void constructor_nullBidder_throws() {
        assertThrows(NullPointerException.class,
                () -> new AutoBid(null, AUCTION, new BigDecimal("5000"), new BigDecimal("100")));
    }

    @Test
    @DisplayName("Restore từ DB giữ nguyên createdAt và active")
    void constructor_restore_keepsState() {
        LocalDateTime created = LocalDateTime.of(2020, 1, 1, 0, 0);
        AutoBid ab = new AutoBid(BIDDER, AUCTION, new BigDecimal("5000"),
                new BigDecimal("100"), created, false);
        assertEquals(created, ab.getCreatedAt());
        assertFalse(ab.isActive());
    }
}
