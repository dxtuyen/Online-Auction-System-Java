package com.auction.persistence.dao;

import com.auction.model.entity.*;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.model.entity.User;
import com.auction.testsupport.Fixtures;
import com.auction.testsupport.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MysqlItemDao (H2)")
class MysqlItemDaoTest {

    private final ItemDao dao = new MysqlItemDao();
    private UUID sellerId;

    @BeforeEach
    void setUp() {
        TestDb.clean();
        sellerId = Fixtures.persistUser().getId();
    }

    @Test
    @DisplayName("insert Electronics + findById giữ subtype và images")
    void insertElectronics() {
        Electronics e = new Electronics("Laptop", "desc", sellerId, new BigDecimal("5000"),
                List.of("a.jpg", "b.jpg"), ItemCondition.NEW, "Dell", "XPS", 24);
        dao.insert(e);

        Item found = dao.findById(e.getId()).orElseThrow();
        assertInstanceOf(Electronics.class, found);
        assertEquals("Dell", ((Electronics) found).getBrand());
        assertEquals(24, ((Electronics) found).getWarrantyMonths());
        assertEquals(2, found.getImages().size());
    }

    @Test
    @DisplayName("insert Art với yearCreated null")
    void insertArt_nullYear() {
        Art art = new Art("Painting", "desc", sellerId, new BigDecimal("9000"),
                List.of(), ItemCondition.USED, "Picasso", null, "Oil");
        dao.insert(art);

        Art found = (Art) dao.findById(art.getId()).orElseThrow();
        assertEquals("Picasso", found.getArtist());
        assertNull(found.getYearCreated());
    }

    @Test
    @DisplayName("insert Vehicle")
    void insertVehicle() {
        Vehicle v = new Vehicle("Car", "desc", sellerId, new BigDecimal("200000"),
                List.of(), ItemCondition.USED, "Toyota", "Camry", 2020, 50000);
        dao.insert(v);

        Vehicle found = (Vehicle) dao.findById(v.getId()).orElseThrow();
        assertEquals("Toyota", found.getMake());
        assertEquals(2020, found.getYear());
        assertEquals(50000, found.getMileageKm());
    }

    @Test
    @DisplayName("insert OtherItem")
    void insertOther() {
        OtherItem o = new OtherItem("Coin", "desc", sellerId, new BigDecimal("500"),
                List.of(), ItemCategory.COLLECTIBLE, ItemCondition.USED, "Rare");
        dao.insert(o);

        OtherItem found = (OtherItem) dao.findById(o.getId()).orElseThrow();
        assertEquals("Rare", found.getExtraInfo());
    }

    @Test
    @DisplayName("update sửa tên, giá, images")
    void update() {
        Item item = Fixtures.persistItem(sellerId);
        item.rename("New Name");
        item.updatePrice(new BigDecimal("9999"));
        item.addImage("c.jpg");
        dao.update(item);

        Item reloaded = dao.findById(item.getId()).orElseThrow();
        assertEquals("New Name", reloaded.getName());
        assertEquals(0, new BigDecimal("9999").compareTo(reloaded.getStartingPrice()));
        assertEquals(3, reloaded.getImages().size());
    }

    @Test
    @DisplayName("findAll + count")
    void findAllAndCount() {
        Fixtures.persistItem(sellerId);
        Fixtures.persistItem(sellerId);
        assertEquals(2, dao.findAll().size());
        assertEquals(2, dao.count());
    }

    @Test
    @DisplayName("findById không tồn tại - empty")
    void findById_missing() {
        assertTrue(dao.findById(UUID.randomUUID()).isEmpty());
    }
}
