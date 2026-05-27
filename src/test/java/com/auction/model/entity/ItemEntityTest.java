package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Item entity hierarchy + Entity base")
class ItemEntityTest {

    private static final UUID SELLER = UUID.randomUUID();
    private static final BigDecimal PRICE = new BigDecimal("1000");

    private Electronics electronics() {
        return new Electronics("iPhone", "desc", SELLER, PRICE,
                new ArrayList<>(List.of("a.jpg")), ItemCondition.USED,
                "Apple", "15 Pro", 12);
    }

    // ========== Item domain operations (covers Item base) ==========

    @Test
    @DisplayName("rename cập nhật tên")
    void rename() {
        Electronics e = electronics();
        e.rename("New Name");
        assertEquals("New Name", e.getName());
    }

    @Test
    @DisplayName("rename tên rỗng - throw")
    void rename_blank_throws() {
        Electronics e = electronics();
        assertThrows(IllegalArgumentException.class, () -> e.rename("  "));
    }

    @Test
    @DisplayName("updatePrice cập nhật giá")
    void updatePrice() {
        Electronics e = electronics();
        e.updatePrice(new BigDecimal("2000"));
        assertEquals(new BigDecimal("2000"), e.getStartingPrice());
    }

    @Test
    @DisplayName("updatePrice âm - throw")
    void updatePrice_negative_throws() {
        Electronics e = electronics();
        assertThrows(IllegalArgumentException.class, () -> e.updatePrice(new BigDecimal("-1")));
    }

    @Test
    @DisplayName("updateDescription + updateCondition")
    void updateDescriptionAndCondition() {
        Electronics e = electronics();
        e.updateDescription("new desc");
        e.updateCondition(ItemCondition.NEW);
        assertEquals("new desc", e.getDescription());
        assertEquals(ItemCondition.NEW, e.getCondition());
    }

    @Test
    @DisplayName("addImage thêm ảnh mới, bỏ qua trùng")
    void addImage() {
        Electronics e = electronics();
        e.addImage("b.jpg");
        e.addImage("b.jpg"); // trùng - bỏ qua
        assertEquals(2, e.getImages().size());
    }

    @Test
    @DisplayName("addImage rỗng - throw")
    void addImage_blank_throws() {
        Electronics e = electronics();
        assertThrows(IllegalArgumentException.class, () -> e.addImage(" "));
    }

    @Test
    @DisplayName("removeImage xóa ảnh")
    void removeImage() {
        Electronics e = electronics();
        e.removeImage("a.jpg");
        assertTrue(e.getImages().isEmpty());
    }

    @Test
    @DisplayName("getImages trả về list bất biến")
    void getImages_unmodifiable() {
        Electronics e = electronics();
        assertThrows(UnsupportedOperationException.class, () -> e.getImages().add("x"));
    }

    @Test
    @DisplayName("printInfo chứa tên + thông tin đặc thù")
    void printInfo() {
        Electronics e = electronics();
        String info = e.printInfo();
        assertTrue(info.contains("iPhone"));
        assertTrue(info.contains("Apple"));
    }

    @Test
    @DisplayName("Item name null - throw")
    void constructor_nullName_throws() {
        assertThrows(NullPointerException.class, () ->
                new Electronics(null, "d", SELLER, PRICE, List.of(), ItemCondition.NEW, "b", "m", 0));
    }

    @Test
    @DisplayName("Item images null - throw")
    void constructor_nullImages_throws() {
        assertThrows(NullPointerException.class, () ->
                new Electronics("n", "d", SELLER, PRICE, null, ItemCondition.NEW, "b", "m", 0));
    }

    // ========== getSpecificInfo polymorphism ==========

    @Test
    @DisplayName("Electronics.getSpecificInfo")
    void electronicsSpecificInfo() {
        assertTrue(electronics().getSpecificInfo().contains("Apple"));
    }

    @Test
    @DisplayName("Art.getSpecificInfo (year null hiển thị '?')")
    void artSpecificInfo() {
        Art art = new Art("Painting", "d", SELLER, PRICE, List.of(), ItemCondition.USED,
                "Picasso", null, "Oil");
        String info = art.getSpecificInfo();
        assertTrue(info.contains("Picasso"));
        assertTrue(info.contains("?"));
    }

    @Test
    @DisplayName("Vehicle.getSpecificInfo")
    void vehicleSpecificInfo() {
        Vehicle v = new Vehicle("Car", "d", SELLER, PRICE, List.of(), ItemCondition.USED,
                "Toyota", "Camry", 2020, 50000);
        assertTrue(v.getSpecificInfo().contains("Toyota"));
    }

    @Test
    @DisplayName("Vehicle year < 1900 - throw")
    void vehicle_invalidYear_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Vehicle("Car", "d", SELLER, PRICE, List.of(), ItemCondition.USED,
                        "Toyota", "Camry", 1800, 0));
    }

    @Test
    @DisplayName("OtherItem.getSpecificInfo khi extraInfo null - default text")
    void otherItemSpecificInfo_null() {
        OtherItem o = new OtherItem("Coin", "d", SELLER, PRICE, List.of(),
                ItemCategory.COLLECTIBLE, ItemCondition.USED, null);
        assertTrue(o.getSpecificInfo().contains("không có"));
    }

    @Test
    @DisplayName("OtherItem.updateExtraInfo")
    void otherItemUpdateExtraInfo() {
        OtherItem o = new OtherItem("Coin", "d", SELLER, PRICE, List.of(),
                ItemCategory.OTHER, ItemCondition.USED, "old");
        o.updateExtraInfo("Size M");
        assertEquals("Size M", o.getExtraInfo());
        assertEquals("Size M", o.getSpecificInfo());
    }

    @Test
    @DisplayName("OtherItem với category đã có subclass riêng - throw")
    void otherItem_dedicatedCategory_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new OtherItem("X", "d", SELLER, PRICE, List.of(),
                        ItemCategory.ELECTRONICS, ItemCondition.NEW, null));
    }

    @Test
    @DisplayName("Electronics warrantyMonths âm - throw")
    void electronics_negativeWarranty_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Electronics("n", "d", SELLER, PRICE, List.of(), ItemCondition.NEW, "b", "m", -1));
    }

    // ========== Entity base: equals / hashCode / toString / timestamps ==========

    @Test
    @DisplayName("Entity equals dựa trên id")
    void entityEquals_sameId() {
        Electronics e = electronics();
        // cùng object → equals
        assertEquals(e, e);
        // khác object cùng loại, id khác → not equals
        assertNotEquals(e, electronics());
    }

    @Test
    @DisplayName("Entity not equals với null và khác class")
    void entityEquals_nullAndOtherClass() {
        Electronics e = electronics();
        assertNotEquals(e, null);
        assertNotEquals(e, "a string");
    }

    @Test
    @DisplayName("Entity hashCode ổn định theo id")
    void entityHashCode() {
        Electronics e = electronics();
        assertEquals(e.hashCode(), e.hashCode());
    }

    @Test
    @DisplayName("Entity có id, createdAt, updatedAt")
    void entityTimestamps() {
        Electronics e = electronics();
        assertNotNull(e.getId());
        assertNotNull(e.getCreatedAt());
        assertNotNull(e.getUpdatedAt());
    }

    @Test
    @DisplayName("toString chứa tên class và id")
    void entityToString() {
        Electronics e = electronics();
        assertTrue(e.toString().contains("Electronics"));
    }
}
