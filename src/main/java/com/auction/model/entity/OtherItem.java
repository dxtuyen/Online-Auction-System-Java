package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Item "tổng hợp" - dùng cho các category KHÔNG có subclass riêng
 * như FASHION, COLLECTIBLE, OTHER.
 *
 * Tại sao cần class này?
 *  - Item là abstract → không thể new Item(...) trực tiếp
 *  - Không phải mọi category đều xứng đáng có subclass riêng
 *    (vd: FASHION có thể chỉ cần "size" - không đủ phức tạp để tách class)
 *  - Đây là pattern Null Object / Default Implementation:
 *    "khi không có gì đặc biệt, dùng cái mặc định"
 *
 * Khác biệt với Electronics/Art/Vehicle:
 *  - Category được TRUYỀN VÀO (không cố định) - vì class này phục vụ nhiều category
 *  - Có 1 field tự do `extraInfo` để lưu thông tin đặc thù dạng text
 *
 * Khi nào nên TÁCH ra subclass riêng?
 *  - Khi category có >= 2 thuộc tính cấu trúc (vd: brand + model + warranty)
 *  - Khi cần validate đặc thù (vd: year >= 1900 cho Vehicle)
 *  - Khi cần logic riêng (vd: tính phí giao hàng theo cân nặng)
 */
public class OtherItem extends Item {

    private static final long serialVersionUID = 1L;

    /**
     * Thông tin đặc thù dạng text tự do.
     * Vd với FASHION: "Size: M | Brand: Zara"
     * Vd với COLLECTIBLE: "Năm phát hành: 1998 | Tình trạng hộp: Nguyên seal"
     * Có thể null nếu seller không cung cấp.
     */
    private String extraInfo;

    // ============== CONSTRUCTORS ==============

    /** Tạo item mới */
    public OtherItem(String name, String description, UUID sellerId,
                     BigDecimal startingPrice, List<String> images,
                     ItemCategory category, ItemCondition condition,
                     String extraInfo) {
        super(name, description, sellerId, startingPrice, images,
                validateCategory(category), condition);
        this.extraInfo = extraInfo;   // optional - cho phép null
    }

    /** Restore từ DB */
    public OtherItem(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                     String name, String description, UUID sellerId,
                     BigDecimal startingPrice, List<String> images,
                     ItemCategory category, ItemCondition condition,
                     String extraInfo) {
        super(id, createdAt, updatedAt, name, description, sellerId,
                startingPrice, images, validateCategory(category), condition);
        this.extraInfo = extraInfo;
    }

    // ============== GETTERS / SETTERS ==============

    public String getExtraInfo() {
        return extraInfo;
    }

    public void updateExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
        markUpdated();
    }

    // ============== POLYMORPHISM ==============

    @Override
    public String getSpecificInfo() {
        if (extraInfo == null || extraInfo.isBlank()) {
            return "(không có thông tin chi tiết)";
        }
        return extraInfo;
    }

    // ============== VALIDATION ==============

    /**
     * OtherItem CHỈ phục vụ các category KHÔNG có subclass riêng.
     * Ngăn dev lỡ tay tạo OtherItem cho ELECTRONICS/ART/VEHICLE
     * (đã có subclass dedicated rồi, dùng OtherItem là sai).
     */
    private static ItemCategory validateCategory(ItemCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        if (category == ItemCategory.ELECTRONICS
                || category == ItemCategory.ART
                || category == ItemCategory.VEHICLE) {
            throw new IllegalArgumentException(
                    "Category '" + category + "' đã có subclass riêng. " +
                            "Hãy dùng " + getDedicatedClassName(category) + " thay vì OtherItem.");
        }
        return category;
    }

    private static String getDedicatedClassName(ItemCategory category) {
        return switch (category) {
            case ELECTRONICS -> "Electronics";
            case ART -> "Art";
            case VEHICLE -> "Vehicle";
            default -> "OtherItem";
        };
    }
}