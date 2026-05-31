package com.auction.service;

import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.model.enums.Role;
import com.auction.testsupport.Reset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemManager service (H2)")
class ItemManagerTest {

    private final ItemManager manager = ItemManager.getInstance();
    private User seller;

    @BeforeEach
    void setUp() {
        Reset.all();
        seller = UserManager.getInstance().register(
                "seller1", "password123", "seller1@example.com", "Seller", Role.NORMAL);
    }

    private Item createPhone() {
        return manager.createItem(ItemCategory.ELECTRONICS, "iPhone", "desc",
                seller.getId(), new BigDecimal("1000"), List.of("a.jpg"),
                ItemCondition.NEW, Map.of("brand", "Apple", "model", "15"));
    }

    @Test
    @DisplayName("createItem persist + nằm trong cache")
    void createItem() {
        Item item = createPhone();
        assertEquals(1, manager.count());
        assertTrue(manager.findById(item.getId()).isPresent());
    }

    @Test
    @DisplayName("createItem seller không tồn tại - throw")
    void createItem_unknownSeller_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.createItem(ItemCategory.OTHER, "X", "d", UUID.randomUUID(),
                        new BigDecimal("100"), List.of(), ItemCondition.NEW, Map.of()));
    }

    @Test
    @DisplayName("createItem seller bị ban - throw (không có quyền sell)")
    void createItem_bannedSeller_throws() {
        seller.ban();
        UserManager.getInstance().save(seller);
        assertThrows(IllegalArgumentException.class, this::createPhone);
    }

    @Test
    @DisplayName("save sau khi mutate item")
    void save() {
        Item item = createPhone();
        item.rename("Renamed");
        manager.save(item);
        // reload từ DB để chắc chắn đã persist
        manager.loadAllFromDb();
        assertEquals("Renamed", manager.findById(item.getId()).orElseThrow().getName());
    }

    @Test
    @DisplayName("save item chưa register - throw")
    void save_unregistered_throws() {
        Item ghost = new com.auction.model.entity.OtherItem("X", "d", seller.getId(),
                new BigDecimal("10"), List.of(), ItemCategory.OTHER, ItemCondition.NEW, null);
        assertThrows(IllegalArgumentException.class, () -> manager.save(ghost));
    }

    @Test
    @DisplayName("findBySellerId + findByCategory")
    void queries() {
        createPhone();
        manager.createItem(ItemCategory.ART, "Tranh", "d", seller.getId(),
                new BigDecimal("500"), List.of(), ItemCondition.USED,
                Map.of("artist", "X", "medium", "Oil"));

        assertEquals(2, manager.findBySellerId(seller.getId()).size());
        assertEquals(1, manager.findByCategory(ItemCategory.ELECTRONICS).size());
        assertEquals(1, manager.findByCategory(ItemCategory.ART).size());
        assertEquals(2, manager.findAll().size());
    }

    @Test
    @DisplayName("findById không tồn tại - empty")
    void findById_missing() {
        assertTrue(manager.findById(UUID.randomUUID()).isEmpty());
    }
}
