package com.auction.service;

import com.auction.model.entity.*;
import com.auction.model.enums.*;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho AuctionService.
 * Test 3 nhóm chính: User (register/login), Bid (placeBid), Singleton.
 */
class AuctionServiceTest {

    private AuctionService service;

    // @BeforeEach: chạy TRƯỚC mỗi @Test → đảm bảo bắt đầu với state sạch
    @BeforeEach
    void setUp() {
        service = AuctionService.getInstance();
    }

    // ==================== USER TESTS ====================

    @Test
    @DisplayName("Đăng ký user mới → thành công, có ID")
    void register_newUser_success() {
        // Act: đăng ký
        User user = service.register("alice_test", "123", Role.BIDDER, 5_000_000);

        // Assert: kiểm tra kết quả
        assertNotNull(user);                            // user không null
        assertEquals("alice_test", user.getUsername()); // username đúng
        assertEquals(Role.BIDDER, user.getRole());      // role đúng
        assertTrue(user.getId() > 0);                    // có ID hợp lệ
    }

    @Test
    @DisplayName("Đăng ký username trùng → throw exception")
    void register_duplicateUsername_throws() {
        // Arrange: đăng ký lần 1
        service.register("bob_test", "123", Role.BIDDER, 1_000_000);

        // Act + Assert: đăng ký lần 2 cùng username → phải throw
        assertThrows(RuntimeException.class, () ->
                service.register("bob_test", "456", Role.SELLER, 0));
    }

    @Test
    @DisplayName("Đăng nhập đúng password → trả về user")
    void login_correctPassword_success() {
        // Arrange
        service.register("charlie_test", "mypass", Role.BIDDER, 1_000_000);

        // Act
        User user = service.login("charlie_test", "mypass");

        // Assert
        assertNotNull(user);
        assertEquals("charlie_test", user.getUsername());
    }

    @Test
    @DisplayName("Đăng nhập sai password → throw exception")
    void login_wrongPassword_throws() {
        service.register("dave_test", "correctpass", Role.BIDDER, 1_000_000);

        assertThrows(RuntimeException.class, () ->
                service.login("dave_test", "wrongpass"));
    }

    @Test
    @DisplayName("Đăng nhập user không tồn tại → throw exception")
    void login_nonExistentUser_throws() {
        assertThrows(RuntimeException.class, () ->
                service.login("khong_ton_tai_99999", "123"));
    }

    // ==================== BID TESTS ====================

    @Test
    @DisplayName("Đặt giá hợp lệ → thành công, giá cập nhật")
    void placeBid_validAmount_success() {
        // Arrange: setup đầy đủ seller → item → auction → bidder
        User seller = service.register("seller_v1", "123", Role.SELLER, 0);
        User bidder = service.register("bidder_v1", "123", Role.BIDDER, 100_000_000);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("brand", "Apple");
        attrs.put("model", "MacBook");
        attrs.put("warrantyMonths", "12");

        Item item = service.createItem(ItemCategory.ELECTRONICS, "MacBook Test",
                "Test item", 30_000_000, seller.getId(), ItemCondition.NEW, attrs);

        Auction auction = service.createAuction(item.getId(), seller.getId(),
                60, 1_000_000);

        // Act: đặt giá 32M (> 30M + 1M bước nhảy)
        BidTransaction bid = service.placeBid(auction.getId(), bidder.getId(),
                32_000_000);

        // Assert
        assertNotNull(bid);
        assertEquals(32_000_000, bid.getBidAmount());
        assertEquals(32_000_000, auction.getCurrentPrice()); // giá cập nhật
        assertEquals(bidder.getId(), auction.getHighestBidderID()); // leader đúng
    }

    @Test
    @DisplayName("Đặt giá thấp hơn bước nhảy tối thiểu → throw exception")
    void placeBid_tooLow_throws() {
        User seller = service.register("seller_v2", "123", Role.SELLER, 0);
        User bidder = service.register("bidder_v2", "123", Role.BIDDER, 100_000_000);

        Item item = service.createItem(ItemCategory.ART, "Tranh Test", "Mô tả",
                5_000_000, seller.getId(), ItemCondition.NEW,
                Map.of("artist", "Test", "year", "2024"));

        Auction auction = service.createAuction(item.getId(), seller.getId(),
                60, 500_000);

        // Giá hiện tại = 5M, bước nhảy = 500k → tối thiểu 5.5M
        // Đặt 5M → phải throw
        assertThrows(RuntimeException.class, () ->
                service.placeBid(auction.getId(), bidder.getId(), 5_000_000));
    }

    @Test
    @DisplayName("Đặt giá vượt số dư bidder → throw exception")
    void placeBid_insufficientBalance_throws() {
        User seller = service.register("seller_v_balance", "123", Role.SELLER, 0);
        User bidder = service.register("bidder_v_balance", "123", Role.BIDDER, 1_500_000);

        Item item = service.createItem(ItemCategory.OTHER, "Low Budget Item", "Mô tả",
                1_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());

        Auction auction = service.createAuction(item.getId(), seller.getId(),
                60, 500_000);

        assertThrows(RuntimeException.class, () ->
                service.placeBid(auction.getId(), bidder.getId(), 2_000_000));
        assertEquals(1_000_000, auction.getCurrentPrice());
        assertEquals(0, auction.getTotalBids());
    }

    @Test
    @DisplayName("Reserve balance: được tăng giá trên chính auction đang dẫn đầu nhưng không được over-commit auction khác")
    void placeBid_reservedBalanceAcrossAuctions_throws() {
        User seller = service.register("seller_reserved", "123", Role.SELLER, 0);
        User bidder = service.register("bidder_reserved", "123", Role.BIDDER, 10_000_000);

        Item item1 = service.createItem(ItemCategory.OTHER, "Reserved Item 1", "Mô tả",
                3_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());
        Item item2 = service.createItem(ItemCategory.OTHER, "Reserved Item 2", "Mô tả",
                2_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());

        Auction auction1 = service.createAuction(item1.getId(), seller.getId(), 60, 1_000_000);
        Auction auction2 = service.createAuction(item2.getId(), seller.getId(), 60, 1_000_000);

        service.placeBid(auction1.getId(), bidder.getId(), 5_000_000);
        service.placeBid(auction1.getId(), bidder.getId(), 7_000_000);

        assertThrows(RuntimeException.class, () ->
                service.placeBid(auction2.getId(), bidder.getId(), 4_000_000));
        assertEquals(7_000_000, auction1.getCurrentPrice());
        assertEquals(2_000_000, auction2.getCurrentPrice());
        assertEquals(0, auction2.getTotalBids());
    }

    @Test
    @DisplayName("Đặt giá phiên đã đóng → throw exception")
    void placeBid_closedAuction_throws() {
        User seller = service.register("seller_v3", "123", Role.SELLER, 0);
        User bidder = service.register("bidder_v3", "123", Role.BIDDER, 100_000_000);

        Item item = service.createItem(ItemCategory.OTHER, "Other Test", "Mô tả",
                1_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());

        Auction auction = service.createAuction(item.getId(), seller.getId(),
                60, 100_000);

        // Đóng phiên trước
        service.closeAuction(auction.getId(), seller.getId());

        // Act + Assert: đặt giá trong phiên đã đóng → throw
        assertThrows(RuntimeException.class, () ->
                service.placeBid(auction.getId(), bidder.getId(), 2_000_000));
    }

    @Test
    @DisplayName("Đặt giá phiên không tồn tại → throw exception")
    void placeBid_nonExistentAuction_throws() {
        User bidder = service.register("bidder_v4", "123", Role.BIDDER, 100_000_000);

        assertThrows(RuntimeException.class, () ->
                service.placeBid(99999, bidder.getId(), 10_000_000));
    }

    @Test
    @DisplayName("Bidder không sở hữu auction → không được đóng phiên")
    void closeAuction_unauthorizedBidder_throws() {
        User seller = service.register("seller_close_auth", "123", Role.SELLER, 0);
        User bidder = service.register("bidder_close_auth", "123", Role.BIDDER, 10_000_000);

        Item item = service.createItem(ItemCategory.OTHER, "Close Auth Item", "Mô tả",
                1_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());

        Auction auction = service.createAuction(item.getId(), seller.getId(),
                60, 100_000);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.closeAuction(auction.getId(), bidder.getId()));
        assertEquals("Bạn không có quyền đóng phiên đấu giá này!", ex.getMessage());
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    @Test
    @DisplayName("Đóng phiên có bidder thắng → trừ tiền bidder, cộng doanh thu seller")
    void closeAuction_withWinner_settlesBalances() {
        Seller seller = (Seller) service.register("seller_settle", "123", Role.SELLER, 0);
        Bidder bidder = (Bidder) service.register("bidder_settle", "123", Role.BIDDER, 100_000_000);

        Item item = service.createItem(ItemCategory.ELECTRONICS, "MacBook Settle",
                "Test item", 30_000_000, seller.getId(), ItemCondition.NEW,
                Map.of("brand", "Apple", "model", "MacBook", "warrantyMonths", "12"));

        Auction auction = service.createAuction(item.getId(), seller.getId(),
                60, 1_000_000);

        service.placeBid(auction.getId(), bidder.getId(), 32_000_000);
        service.closeAuction(auction.getId(), seller.getId());

        assertEquals(AuctionStatus.PAID, auction.getStatus());
        assertEquals(68_000_000, bidder.getBalance());
        assertEquals(32_000_000, seller.getTotalRevenue());

        service.closeAuction(auction.getId(), seller.getId());
        assertEquals(68_000_000, bidder.getBalance());
        assertEquals(32_000_000, seller.getTotalRevenue());
    }

    @Test
    @DisplayName("Admin được phép đóng auction của seller khác")
    void closeAuction_adminCanClose_success() {
        User seller = service.register("seller_admin_close", "123", Role.SELLER, 0);
        User admin = service.register("admin_close_auth", "123", Role.ADMIN, 0);

        Item item = service.createItem(ItemCategory.OTHER, "Admin Close Item", "Mô tả",
                1_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());

        Auction auction = service.createAuction(item.getId(), seller.getId(),
                60, 100_000);

        service.closeAuction(auction.getId(), admin.getId());
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    @Test
    @DisplayName("Đăng ký auto-bid vượt số dư bidder → throw exception")
    void registerAutoBid_insufficientBalance_throws() {
        User seller = service.register("seller_auto_balance", "123", Role.SELLER, 0);
        User bidder = service.register("bidder_auto_balance", "123", Role.BIDDER, 3_000_000);

        Item item = service.createItem(ItemCategory.OTHER, "AutoBid Item", "Mô tả",
                1_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());

        Auction auction = service.createAuction(item.getId(), seller.getId(),
                60, 500_000);

        assertThrows(RuntimeException.class, () ->
                service.registerAutoBid(auction.getId(), bidder.getId(), 5_000_000, 500_000));
    }

    @Test
    @DisplayName("Reserve balance: đăng ký auto-bid ở auction khác vượt số dư khả dụng → throw exception")
    void registerAutoBid_reservedBalance_throws() {
        User seller = service.register("seller_auto_reserved", "123", Role.SELLER, 0);
        User bidder = service.register("bidder_auto_reserved", "123", Role.BIDDER, 10_000_000);

        Item item1 = service.createItem(ItemCategory.OTHER, "Auto Reserved 1", "Mô tả",
                3_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());
        Item item2 = service.createItem(ItemCategory.OTHER, "Auto Reserved 2", "Mô tả",
                2_000_000, seller.getId(), ItemCondition.NEW, new HashMap<>());

        Auction auction1 = service.createAuction(item1.getId(), seller.getId(), 60, 1_000_000);
        Auction auction2 = service.createAuction(item2.getId(), seller.getId(), 60, 1_000_000);

        service.placeBid(auction1.getId(), bidder.getId(), 7_000_000);

        assertThrows(RuntimeException.class, () ->
                service.registerAutoBid(auction2.getId(), bidder.getId(), 4_000_000, 1_000_000));
    }

    // ==================== SINGLETON TEST ====================

    @Test
    @DisplayName("Singleton: getInstance() luôn trả về cùng 1 object")
    void singleton_alwaysSameInstance() {
        AuctionService s1 = AuctionService.getInstance();
        AuctionService s2 = AuctionService.getInstance();

        // assertSame kiểm tra s1 == s2 (cùng reference, cùng 1 object)
        assertSame(s1, s2);
    }
}
