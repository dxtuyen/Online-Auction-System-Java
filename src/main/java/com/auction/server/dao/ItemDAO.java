package com.auction.server.dao;

import com.auction.model.entity.Item;
import java.util.List;

public interface ItemDAO {
    // --- Nhóm các hàm CRUD cơ bản ---

    /**
     * Thêm một sản phẩm mới vào hệ thống.
     * @return ID của sản phẩm vừa tạo (để dùng cho phiên đấu giá tiếp theo).
     */
    int addItem(Item item);

    /**
     * Lấy thông tin chi tiết một sản phẩm theo ID.
     */
    Item getItemById(int id);

    /**
     * Cập nhật thông tin sản phẩm (Tên, mô tả, giá khởi điểm...).
     */
    boolean updateItem(Item item);

    /**
     * Xóa sản phẩm khỏi hệ thống.
     */
    boolean deleteItem(int id);

    // --- Nhóm các hàm nghiệp vụ và tìm kiếm ---

    /**
     * Lấy toàn bộ danh sách sản phẩm hiện có.
     */
    List<Item> getAllItems();

    /**
     * Lấy danh sách sản phẩm theo từng loại (Electronics, Art, Vehicle).
     * Phục vụ chức năng lọc sản phẩm trên GUI.
     */
    List<Item> getItemsByCategory(String category);

    /**
     * Lấy danh sách sản phẩm do một người bán (Seller) đăng.
     * Phục vụ màn hình "Quản lý sản phẩm" của Seller.
     */
    List<Item> getItemsBySellerId(int sellerId);

    /**
     * Tìm kiếm sản phẩm theo tên hoặc từ khóa.
     */
    List<Item> searchItemsByName(String keyword);

    /**
     * Kiểm tra sản phẩm có đang trong một phiên đấu giá nào không trước khi xóa/sửa.
     */
    boolean isItemInAuction(int itemId);
}