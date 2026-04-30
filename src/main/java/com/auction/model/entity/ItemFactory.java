package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.util.List;
import java.util.Map;

/**
 * Factory trung tâm để tạo ra đúng subclass của {@link Item} theo {@link ItemCategory}.
 *
 * <p>Class này gom logic "biến dữ liệu đầu vào thành object domain" vào một chỗ duy nhất,
 * thay vì để controller/service phải tự {@code new Vehicle()}, {@code new Art()}, ...
 * Nhờ đó code phía trên chỉ cần biết category, còn chi tiết mỗi loại item cần field nào
 * sẽ do factory quyết định.</p>
 *
 * <p>Flow tạo item hiện tại gồm 3 bước:</p>
 * <ol>
 *   <li>Chuẩn hóa {@code specificAttributes} về một {@link Map} an toàn, không null.</li>
 *   <li>Tạo đúng object con theo category và map các field riêng của category đó.</li>
 *   <li>Gán các field chung của mọi item như tên, mô tả, giá khởi điểm, seller, ảnh...</li>
 * </ol>
 *
 * <p>Factory cũng cố tình dùng fallback mềm cho dữ liệu đầu vào:
 * field chuỗi thiếu thì dùng {@code "Unknown"},
 * field số parse lỗi thì rơi về giá trị mặc định {@code 0}.
 * Cách này giúp server không bị văng lỗi chỉ vì client gửi thiếu hoặc sai format một vài attribute phụ.</p>
 */
public class ItemFactory {

    private ItemFactory() {
    }

    /**
     * Tạo mới một item theo category.
     *
     * <p>Đây là entry point chính khi hệ thống nhận yêu cầu tạo item từ UI/API.
     * Method này chưa gán {@code id}; id thường sẽ được service cấp riêng sau đó.</p>
     *
     * <p>{@code specificAttributes} là phần dữ liệu biến thiên theo từng loại item.
     * Ví dụ:</p>
     * <ul>
     *   <li>{@code VEHICLE}: brand, model, manufactureYear, mileage...</li>
     *   <li>{@code ELECTRONICS}: brand, model, warrantyMonths</li>
     *   <li>{@code ART}: artist, year</li>
     *   <li>{@code OTHER}: giữ nguyên toàn bộ map ở extraDetails</li>
     * </ul>
     */
    public static Item createItem(ItemCategory category, String name,
                                  String description, double startingPrice,
                                  int sellerId, List<String> images, ItemCondition condition,
                                  Map<String, String> specificAttributes) {
        // Factory không làm việc với null map để các helper bên dưới không phải check null lặp lại.
        Map<String, String> attrs = specificAttributes != null ? specificAttributes : Map.of();
        Item item = createCategoryItem(category, attrs);
        // Sau khi tạo đúng subclass, mới áp các field chung mà item nào cũng có.
        applyCommonFields(item, name, description, startingPrice, sellerId, images, condition, category);
        return item;
    }

    /**
     * Dùng khi dữ liệu item đã tồn tại sẵn ở nơi khác (ví dụ DB/file) và cần dựng lại object.
     *
     * <p>Khác với {@link #createItem(ItemCategory, String, String, double, int, List, ItemCondition, Map)},
     * method này giữ nguyên {@code id} có sẵn thay vì chờ service cấp mới.</p>
     */
    public static Item createItemFromDB(int id, ItemCategory category, String name,
                                         String description, double startingPrice,
                                         int sellerId, List<String> images, ItemCondition condition,
                                         Map<String, String> specificAttributes) {
        Item item = createItem(category, name, description, startingPrice,
                sellerId, images, condition, specificAttributes);
        item.setId(id);
        return item;
    }

    /**
     * Parse chuỗi sang số nguyên một cách "chịu lỗi".
     *
     * <p>Nếu value null, rỗng, hoặc sai format thì trả về defaultValue thay vì ném exception.
     * Điều này đặc biệt hữu ích với các attribute phụ nhập từ form/client JSON.</p>
     */
    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Chọn đúng subclass của {@link Item} theo category.
     *
     * <p>Method này chỉ lo phần "loại item nào thì new class nào".
     * Việc gán field chung của item được tách riêng để tránh lặp code trong từng nhánh.</p>
     */
    private static Item createCategoryItem(ItemCategory category, Map<String, String> attrs) {
        return switch (category) {
            case VEHICLE -> createVehicle(attrs);
            case ELECTRONICS -> createElectronic(attrs);
            case ART -> createArt(attrs);
            default -> createOtherItem(attrs);
        };
    }

    /**
     * Dựng item loại {@link Vehicle} từ map attribute đầu vào.
     *
     * <p>Các field dạng số như năm sản xuất, số km, số đời chủ đều được parse an toàn.
     * Các field text thiếu dữ liệu sẽ dùng "Unknown".</p>
     */
    private static Vehicle createVehicle(Map<String, String> attrs) {
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand(getOrUnknown(attrs, "brand"));
        vehicle.setModel(getOrUnknown(attrs, "model"));
        vehicle.setManufactureYear(parseIntSafe(attrs.get("manufactureYear"), 0));
        vehicle.setMileage(parseIntSafe(attrs.get("mileage"), 0));
        vehicle.setColor(getOrUnknown(attrs, "color"));
        vehicle.setFuelType(getOrUnknown(attrs, "fuelType"));
        vehicle.setTransmission(getOrUnknown(attrs, "transmission"));
        vehicle.setOwnerCount(parseIntSafe(attrs.get("ownerCount"), 0));
        vehicle.setHasRegistration(Boolean.parseBoolean(attrs.getOrDefault("hasRegistration", "false")));
        return vehicle;
    }

    /**
     * Dựng item loại {@link Electronic}.
     */
    private static Electronic createElectronic(Map<String, String> attrs) {
        Electronic electronic = new Electronic();
        electronic.setBrand(getOrUnknown(attrs, "brand"));
        electronic.setModel(getOrUnknown(attrs, "model"));
        electronic.setWarrantyMonths(parseIntSafe(attrs.get("warrantyMonths"), 0));
        return electronic;
    }

    /**
     * Dựng item loại {@link Art}.
     */
    private static Art createArt(Map<String, String> attrs) {
        Art art = new Art();
        art.setArtist(getOrUnknown(attrs, "artist"));
        art.setYear(parseIntSafe(attrs.get("year"), 0));
        return art;
    }

    /**
     * Dựng item loại "khác".
     *
     * <p>Với các category chưa có model riêng hoặc không cần tách class chuyên biệt,
     * hệ thống giữ nguyên toàn bộ attribute map để vẫn không làm mất dữ liệu người dùng nhập.</p>
     */
    private static OtherItem createOtherItem(Map<String, String> attrs) {
        OtherItem otherItem = new OtherItem();
        otherItem.setExtraDetails(attrs);
        return otherItem;
    }

    /**
     * Áp các field chung cho mọi subclass của {@link Item}.
     *
     * <p>Việc tách riêng bước này giúp code tạo từng category gọn hơn:
     * helper category chỉ lo field đặc thù, còn phần dữ liệu base được xử lý tập trung tại đây.</p>
     */
    private static void applyCommonFields(Item item, String name, String description, double startingPrice,
                                          int sellerId, List<String> images, ItemCondition condition,
                                          ItemCategory category) {
        item.setName(name);
        item.setDescription(description);
        item.setStartingPrice(startingPrice);
        item.setSellerId(sellerId);
        item.setImages(images);
        item.setCondition(condition);
        item.setCategory(category);
    }

    /**
     * Lấy giá trị text từ attrs, thiếu thì trả về "Unknown".
     *
     * <p>Đây là fallback nhẹ để object domain vẫn được dựng hoàn chỉnh
     * ngay cả khi client không gửi đủ metadata phụ.</p>
     */
    private static String getOrUnknown(Map<String, String> attrs, String key) {
        return attrs.getOrDefault(key, "Unknown");
    }
}
