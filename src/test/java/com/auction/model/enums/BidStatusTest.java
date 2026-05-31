package com.auction.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BidStatus state machine")
class BidStatusTest {

    // ========== canTransitionTo - VALID ==========

    @Test
    @DisplayName("PENDING → VALID - hợp lệ")
    void pendingToValid_valid() {
        assertTrue(BidStatus.PENDING.canTransitionTo(BidStatus.VALID));
    }

    @Test
    @DisplayName("PENDING → REJECTED - hợp lệ")
    void pendingToRejected_valid() {
        assertTrue(BidStatus.PENDING.canTransitionTo(BidStatus.REJECTED));
    }

    @Test
    @DisplayName("PENDING → CANCELED - hợp lệ")
    void pendingToCanceled_valid() {
        assertTrue(BidStatus.PENDING.canTransitionTo(BidStatus.CANCELED));
    }

    @Test
    @DisplayName("VALID → OUTBID - hợp lệ")
    void validToOutbid_valid() {
        assertTrue(BidStatus.VALID.canTransitionTo(BidStatus.OUTBID));
    }

    @Test
    @DisplayName("VALID → CANCELED - hợp lệ")
    void validToCanceled_valid() {
        assertTrue(BidStatus.VALID.canTransitionTo(BidStatus.CANCELED));
    }

    @Test
    @DisplayName("OUTBID → CANCELED - hợp lệ")
    void outbidToCanceled_valid() {
        assertTrue(BidStatus.OUTBID.canTransitionTo(BidStatus.CANCELED));
    }

    // ========== canTransitionTo - INVALID ==========

    @Test
    @DisplayName("REJECTED → bất kỳ - không hợp lệ (terminal)")
    void rejected_isTerminal() {
        for (BidStatus target : BidStatus.values()) {
            assertFalse(BidStatus.REJECTED.canTransitionTo(target),
                    "REJECTED không được chuyển sang " + target);
        }
    }

    @Test
    @DisplayName("CANCELED → bất kỳ - không hợp lệ (terminal)")
    void canceled_isTerminal() {
        for (BidStatus target : BidStatus.values()) {
            assertFalse(BidStatus.CANCELED.canTransitionTo(target),
                    "CANCELED không được chuyển sang " + target);
        }
    }

    @Test
    @DisplayName("VALID → PENDING - không hợp lệ (không đi ngược)")
    void validToPending_invalid() {
        assertFalse(BidStatus.VALID.canTransitionTo(BidStatus.PENDING));
    }

    @Test
    @DisplayName("OUTBID → VALID - không hợp lệ")
    void outbidToValid_invalid() {
        assertFalse(BidStatus.OUTBID.canTransitionTo(BidStatus.VALID));
    }

    @Test
    @DisplayName("canTransitionTo(null) - trả về false")
    void canTransitionTo_null_false() {
        assertFalse(BidStatus.PENDING.canTransitionTo(null));
    }

    // ========== isTerminal ==========

    @Test
    @DisplayName("REJECTED và CANCELED là terminal")
    void isTerminal_rejectedAndCanceled() {
        assertTrue(BidStatus.REJECTED.isTerminal());
        assertTrue(BidStatus.CANCELED.isTerminal());
    }

    @Test
    @DisplayName("PENDING, VALID, OUTBID không phải terminal")
    void isTerminal_nonTerminalStatuses_false() {
        assertFalse(BidStatus.PENDING.isTerminal());
        assertFalse(BidStatus.VALID.isTerminal());
        assertFalse(BidStatus.OUTBID.isTerminal());
    }
}
