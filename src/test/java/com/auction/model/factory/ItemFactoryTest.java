package com.auction.model.factory;

import com.auction.model.entity.Art;
import com.auction.model.entity.Electronics;
import com.auction.model.entity.Item;
import com.auction.model.entity.OtherItem;
import com.auction.model.entity.Vehicle;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemFactory")
class ItemFactoryTest {

    private static final UUID SELLER_ID = UUID.randomUUID();
    private static final BigDecimal PRICE = new BigDecimal("5000000");
    private static final List<String> IMAGES = List.of("img1.jpg");

    // ========== ELECTRONICS ==========

    @Test
    @DisplayName("Tạo Electronics - trả về đúng kiểu")
    void create_electronics_returnsElectronicsInstance() {
        Item item = ItemFactory.create(
                ItemCategory.ELECTRONICS, "iPhone 15", "Like new", SELLER_ID, PRICE, IMAGES,
                ItemCondition.USED,
                Map.of("brand", "Apple", "model", "iPhone 15", "warrantyMonths", 6));

        assertInstanceOf(Electronics.class, item);
        Electronics e = (Electronics) item;
        assertEquals("Apple", e.getBrand());
        assertEquals("iPhone 15", e.getModel());
        assertEquals(6, e.getWarrantyMonths());
        assertEquals(ItemCategory.ELECTRONICS, e.getCategory());
    }

    @Test
    @DisplayName("Electronics không có warrantyMonths - mặc định 0")
    void create_electronics_missingWarranty_defaultsToZero() {
        Item item = ItemFactory.create(
                ItemCategory.ELECTRONICS, "Laptop", "New", SELLER_ID, PRICE, IMAGES,
                ItemCondition.NEW,
                Map.of("brand", "Dell", "model", "XPS 15"));

        assertEquals(0, ((Electronics) item).getWarrantyMonths());
    }

    @Test
    @DisplayName("Electronics thiếu brand - throw")
    void create_electronics_missingBrand_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemFactory.create(
                        ItemCategory.ELECTRONICS, "Phone", "Desc", SELLER_ID, PRICE, IMAGES,
                        ItemCondition.USED,
                        Map.of("model", "Model X")));
    }

    @Test
    @DisplayName("Electronics thiếu model - throw")
    void create_electronics_missingModel_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemFactory.create(
                        ItemCategory.ELECTRONICS, "Phone", "Desc", SELLER_ID, PRICE, IMAGES,
                        ItemCondition.USED,
                        Map.of("brand", "Samsung")));
    }

    // ========== ART ==========

    @Test
    @DisplayName("Tạo Art - trả về đúng kiểu")
    void create_art_returnsArtInstance() {
        Item item = ItemFactory.create(
                ItemCategory.ART, "Mona Lisa Copy", "Oil painting", SELLER_ID, PRICE, IMAGES,
                ItemCondition.USED,
                Map.of("artist", "Da Vinci", "yearCreated", 1503, "medium", "Oil on canvas"));

        assertInstanceOf(Art.class, item);
        Art a = (Art) item;
        assertEquals("Da Vinci", a.getArtist());
        assertEquals(1503, a.getYearCreated());
        assertEquals("Oil on canvas", a.getMedium());
        assertEquals(ItemCategory.ART, a.getCategory());
    }

    @Test
    @DisplayName("Art thiếu artist - throw")
    void create_art_missingArtist_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemFactory.create(
                        ItemCategory.ART, "Painting", "Desc", SELLER_ID, PRICE, IMAGES,
                        ItemCondition.USED,
                        Map.of("medium", "Oil")));
    }

    @Test
    @DisplayName("Art không có yearCreated - mặc định null")
    void create_art_missingYear_returnsNull() {
        Item item = ItemFactory.create(
                ItemCategory.ART, "Sculpture", "Bronze", SELLER_ID, PRICE, IMAGES,
                ItemCondition.USED,
                Map.of("artist", "Unknown", "medium", "Bronze"));

        assertNull(((Art) item).getYearCreated());
    }

    // ========== VEHICLE ==========

    @Test
    @DisplayName("Tạo Vehicle - trả về đúng kiểu")
    void create_vehicle_returnsVehicleInstance() {
        Item item = ItemFactory.create(
                ItemCategory.VEHICLE, "Toyota Camry 2020", "Good condition",
                SELLER_ID, PRICE, IMAGES, ItemCondition.USED,
                Map.of("make", "Toyota", "model", "Camry", "year", 2020, "mileageKm", 50000));

        assertInstanceOf(Vehicle.class, item);
        Vehicle v = (Vehicle) item;
        assertEquals("Toyota", v.getMake());
        assertEquals("Camry", v.getModel());
        assertEquals(2020, v.getYear());
        assertEquals(50000, v.getMileageKm());
    }

    @Test
    @DisplayName("Vehicle thiếu make - throw")
    void create_vehicle_missingMake_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemFactory.create(
                        ItemCategory.VEHICLE, "Car", "Desc", SELLER_ID, PRICE, IMAGES,
                        ItemCondition.USED,
                        Map.of("model", "Civic", "year", 2019, "mileageKm", 30000)));
    }

    // ========== OTHER CATEGORIES (OtherItem) ==========

    @Test
    @DisplayName("Tạo OTHER - trả về OtherItem")
    void create_other_returnsOtherItemInstance() {
        Item item = ItemFactory.create(
                ItemCategory.OTHER, "Rare Coin", "1888 edition", SELLER_ID, PRICE, IMAGES,
                ItemCondition.USED,
                Map.of("extraInfo", "Silver plated"));

        assertInstanceOf(OtherItem.class, item);
        assertEquals(ItemCategory.OTHER, item.getCategory());
    }

    @Test
    @DisplayName("Tạo FASHION - trả về OtherItem")
    void create_fashion_returnsOtherItem() {
        Item item = ItemFactory.create(
                ItemCategory.FASHION, "Vintage Jacket", "1970s", SELLER_ID, PRICE, IMAGES,
                ItemCondition.USED, Map.of());
        assertInstanceOf(OtherItem.class, item);
        assertEquals(ItemCategory.FASHION, item.getCategory());
    }

    @Test
    @DisplayName("Tạo COLLECTIBLE - trả về OtherItem")
    void create_collectible_returnsOtherItem() {
        Item item = ItemFactory.create(
                ItemCategory.COLLECTIBLE, "Trading Card", "Rare holo", SELLER_ID, PRICE, IMAGES,
                ItemCondition.NEW, Map.of());
        assertInstanceOf(OtherItem.class, item);
    }

    // ========== EDGE CASES ==========

    @Test
    @DisplayName("category null - throw")
    void create_nullCategory_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemFactory.create(null, "Name", "Desc", SELLER_ID, PRICE, IMAGES,
                        ItemCondition.NEW, Map.of()));
    }

    @Test
    @DisplayName("specificAttrs null - không throw (dùng Map rỗng)")
    void create_nullSpecificAttrs_usesEmptyMap() {
        // OTHER không có attrs bắt buộc nên null attrs vẫn ok
        assertDoesNotThrow(() ->
                ItemFactory.create(ItemCategory.OTHER, "Item", "Desc", SELLER_ID, PRICE, IMAGES,
                        ItemCondition.NEW, null));
    }

    @Test
    @DisplayName("Item được tạo có sellerId đúng")
    void create_item_hasCorrectSellerId() {
        Item item = ItemFactory.create(
                ItemCategory.OTHER, "Item", "Desc", SELLER_ID, PRICE, IMAGES,
                ItemCondition.NEW, Map.of());
        assertEquals(SELLER_ID, item.getSellerId());
    }

    @Test
    @DisplayName("Item được tạo có ID và timestamp")
    void create_item_hasIdAndTimestamp() {
        Item item = ItemFactory.create(
                ItemCategory.OTHER, "Item", "Desc", SELLER_ID, PRICE, IMAGES,
                ItemCondition.NEW, Map.of());
        assertNotNull(item.getId());
        assertNotNull(item.getCreatedAt());
    }
}
