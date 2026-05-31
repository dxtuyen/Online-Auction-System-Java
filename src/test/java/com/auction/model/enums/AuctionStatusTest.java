package com.auction.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionStatus state machine")
class AuctionStatusTest {

    // ========== canTransitionTo - VALID TRANSITIONS ==========

    @Test
    @DisplayName("PENDING → RUNNING - hợp lệ")
    void pendingToRunning_valid() {
        assertTrue(AuctionStatus.PENDING.canTransitionTo(AuctionStatus.RUNNING));
    }

    @Test
    @DisplayName("PENDING → CANCELED - hợp lệ")
    void pendingToCanceled_valid() {
        assertTrue(AuctionStatus.PENDING.canTransitionTo(AuctionStatus.CANCELED));
    }

    @Test
    @DisplayName("RUNNING → FINISHED - hợp lệ")
    void runningToFinished_valid() {
        assertTrue(AuctionStatus.RUNNING.canTransitionTo(AuctionStatus.FINISHED));
    }

    @Test
    @DisplayName("RUNNING → CANCELED - hợp lệ")
    void runningToCanceled_valid() {
        assertTrue(AuctionStatus.RUNNING.canTransitionTo(AuctionStatus.CANCELED));
    }

    @Test
    @DisplayName("FINISHED → PAID - hợp lệ")
    void finishedToPaid_valid() {
        assertTrue(AuctionStatus.FINISHED.canTransitionTo(AuctionStatus.PAID));
    }

    @Test
    @DisplayName("FINISHED → CANCELED - hợp lệ")
    void finishedToCanceled_valid() {
        assertTrue(AuctionStatus.FINISHED.canTransitionTo(AuctionStatus.CANCELED));
    }

    // ========== canTransitionTo - INVALID TRANSITIONS ==========

    @Test
    @DisplayName("PENDING → PAID trực tiếp - không hợp lệ")
    void pendingToPaid_invalid() {
        assertFalse(AuctionStatus.PENDING.canTransitionTo(AuctionStatus.PAID));
    }

    @Test
    @DisplayName("PENDING → FINISHED trực tiếp - không hợp lệ")
    void pendingToFinished_invalid() {
        assertFalse(AuctionStatus.PENDING.canTransitionTo(AuctionStatus.FINISHED));
    }

    @Test
    @DisplayName("RUNNING → PENDING - không hợp lệ (không đi ngược)")
    void runningToPending_invalid() {
        assertFalse(AuctionStatus.RUNNING.canTransitionTo(AuctionStatus.PENDING));
    }

    @Test
    @DisplayName("RUNNING → PAID trực tiếp - không hợp lệ")
    void runningToPaid_invalid() {
        assertFalse(AuctionStatus.RUNNING.canTransitionTo(AuctionStatus.PAID));
    }

    @Test
    @DisplayName("PAID → bất kỳ - không hợp lệ (terminal)")
    void paid_isTerminal_noTransitionsAllowed() {
        for (AuctionStatus target : AuctionStatus.values()) {
            assertFalse(AuctionStatus.PAID.canTransitionTo(target),
                    "PAID không được chuyển sang " + target);
        }
    }

    @Test
    @DisplayName("CANCELED → bất kỳ - không hợp lệ (terminal)")
    void canceled_isTerminal_noTransitionsAllowed() {
        for (AuctionStatus target : AuctionStatus.values()) {
            assertFalse(AuctionStatus.CANCELED.canTransitionTo(target),
                    "CANCELED không được chuyển sang " + target);
        }
    }

    @Test
    @DisplayName("canTransitionTo(null) - trả về false (không throw)")
    void canTransitionTo_null_false() {
        assertFalse(AuctionStatus.PENDING.canTransitionTo(null));
    }

    // ========== isTerminal ==========

    @Test
    @DisplayName("PAID và CANCELED là terminal")
    void isTerminal_paidAndCanceled() {
        assertTrue(AuctionStatus.PAID.isTerminal());
        assertTrue(AuctionStatus.CANCELED.isTerminal());
    }

    @Test
    @DisplayName("PENDING, RUNNING, FINISHED không phải terminal")
    void isTerminal_otherStatuses_false() {
        assertFalse(AuctionStatus.PENDING.isTerminal());
        assertFalse(AuctionStatus.RUNNING.isTerminal());
        assertFalse(AuctionStatus.FINISHED.isTerminal());
    }

    // ========== isOpenForBidding ==========

    @Test
    @DisplayName("Chỉ RUNNING mới mở đấu giá")
    void isOpenForBidding_onlyRunning() {
        assertTrue(AuctionStatus.RUNNING.isOpenForBidding());
        assertFalse(AuctionStatus.PENDING.isOpenForBidding());
        assertFalse(AuctionStatus.FINISHED.isOpenForBidding());
        assertFalse(AuctionStatus.PAID.isOpenForBidding());
        assertFalse(AuctionStatus.CANCELED.isOpenForBidding());
    }
}
