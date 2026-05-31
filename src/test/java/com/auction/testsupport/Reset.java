package com.auction.testsupport;

import com.auction.service.AuctionManager;
import com.auction.service.BidManager;
import com.auction.service.ItemManager;
import com.auction.service.UserManager;

/**
 * Reset trạng thái dùng chung cho test chạy trên singleton manager:
 * xóa sạch DB rồi reload cache (rỗng) cho cả 4 manager.
 */
public final class Reset {

    private Reset() { }

    public static void all() {
        TestDb.clean();
        UserManager.getInstance().loadAllFromDb();
        ItemManager.getInstance().loadAllFromDb();
        AuctionManager.getInstance().loadAllFromDb();
        BidManager.getInstance().loadAllFromDb();
    }
}
