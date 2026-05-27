package com.auction.bootstrap;

import com.auction.service.AuctionManager;
import com.auction.service.ItemManager;
import com.auction.service.UserManager;
import com.auction.testsupport.Reset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataSeeder (H2)")
class DataSeederTest {

    @BeforeEach
    void setUp() {
        Reset.all();
    }

    @Test
    @DisplayName("run() seed đầy đủ user/item/auction")
    void run_seedsData() {
        DataSeeder.run();

        assertEquals(4, UserManager.getInstance().count());
        assertEquals(3, ItemManager.getInstance().count());
        assertEquals(3, AuctionManager.getInstance().count());
    }

    @Test
    @DisplayName("run() idempotent - gọi lần 2 không seed thêm")
    void run_idempotent() {
        DataSeeder.run();
        int users = UserManager.getInstance().count();

        DataSeeder.run(); // đã có user → skip
        assertEquals(users, UserManager.getInstance().count());
    }
}
