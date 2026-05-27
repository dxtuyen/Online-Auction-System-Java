package com.auction.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Model enums display names")
class ModelEnumsTest {

    @Test
    @DisplayName("Role display names")
    void role() {
        assertEquals("Quản trị viên", Role.ADMIN.getDisplayRole());
        assertEquals("Người đấu giá", Role.NORMAL.getDisplayRole());
        assertEquals(2, Role.values().length);
        assertEquals(Role.ADMIN, Role.valueOf("ADMIN"));
    }

    @Test
    @DisplayName("UserStatus display names")
    void userStatus() {
        assertEquals("Đang hoạt động", UserStatus.ACTIVE.getDisplayName());
        assertEquals("Đã bị ban", UserStatus.BANNED.getDisplayName());
    }

    @Test
    @DisplayName("ItemCategory display names cho mọi giá trị")
    void itemCategory() {
        for (ItemCategory c : ItemCategory.values()) {
            assertNotNull(c.getDisplayName());
            assertFalse(c.getDisplayName().isBlank());
        }
        assertEquals("Điện tử", ItemCategory.ELECTRONICS.getDisplayName());
        assertEquals(6, ItemCategory.values().length);
    }

    @Test
    @DisplayName("ItemCondition display names")
    void itemCondition() {
        assertEquals("Mới", ItemCondition.NEW.getDisplayCondition());
        assertEquals("Đã qua sử dụng", ItemCondition.USED.getDisplayCondition());
    }

    @Test
    @DisplayName("AuctionStatus display names")
    void auctionStatus() {
        for (AuctionStatus s : AuctionStatus.values()) {
            assertNotNull(s.getDisplayName());
        }
    }

    @Test
    @DisplayName("BidStatus display names")
    void bidStatus() {
        for (BidStatus s : BidStatus.values()) {
            assertNotNull(s.getDisplayName());
        }
    }
}
