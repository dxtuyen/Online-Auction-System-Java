package com.auction.server.dao;

import com.auction.model.entity.Item;
import com.auction.model.entity.ItemFactory;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.server.util.DBConnection;

import java.sql.*;
import java.util.*;

public class ItemDAOImpl implements ItemDAO {

    @Override
    public int addItem(Item item) {
        String sql = "INSERT INTO items (name, description, starting_price, category, seller_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, item.getName());
            pstmt.setString(2, item.getDescription());
            pstmt.setDouble(3, item.getStartingPrice());
            // Lấy tên class làm category (VD: "Electronic", "Art")
            pstmt.setString(4, item.getClass().getSimpleName().toUpperCase());
            // pstmt.setInt(5, item.getSellerId()); // Thay bằng phương thức lấy seller ID thực tế của bạn

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1); // Trả về ID tự động tăng
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public Item getItemById(int id) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapResultSetToItem(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean updateItem(Item item) {
        String sql = "UPDATE items SET name = ?, description = ?, starting_price = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, item.getName());
            pstmt.setString(2, item.getDescription());
            pstmt.setDouble(3, item.getStartingPrice());
            pstmt.setInt(4, item.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean deleteItem(int id) {
        String sql = "DELETE FROM items WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Item item = mapResultSetToItem(rs);
                if (item != null) items.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    @Override
    public List<Item> getItemsByCategory(String category) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE category = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, category.toUpperCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Item item = mapResultSetToItem(rs);
                    if (item != null) items.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    @Override
    public List<Item> getItemsBySellerId(int sellerId) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, sellerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Item item = mapResultSetToItem(rs);
                    if (item != null) items.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    @Override
    public List<Item> searchItemsByName(String keyword) {
        List<Item> items = new ArrayList<>();
        // Sử dụng LIKE để tìm kiếm gần đúng
        String sql = "SELECT * FROM items WHERE name LIKE ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + keyword + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Item item = mapResultSetToItem(rs);
                    if (item != null) items.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    @Override
    public boolean isItemInAuction(int itemId) {
        // Giả định bạn có bảng 'auctions' chứa cột 'item_id' và 'status'
        String sql = "SELECT 1 FROM auctions WHERE item_id = ? AND status != 'FINISHED'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next(); // Nếu có kết quả nghĩa là sản phẩm đang nằm trong 1 phiên đấu giá chưa kết thúc
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Helper: Map dữ liệu từ ResultSet sang đối tượng Item thông qua ItemFactory.
     * Áp dụng Factory Pattern theo đúng yêu cầu đề bài.
     */
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        // 1. Lấy thông tin cơ bản
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String desc = rs.getString("description");
        double price = rs.getDouble("starting_price");
        int sellerId = rs.getInt("seller_id");
        ItemCategory category = ItemCategory.valueOf(rs.getString("category"));

        // Giả định có thêm cột condition (trạng thái hàng cũ/mới)
        ItemCondition condition = ItemCondition.valueOf(rs.getString("item_condition"));

        // 2. Lấy danh sách ảnh (Giả định trường hợp A: lưu chuỗi cách nhau dấu phẩy)
        List<String> imageList = new ArrayList<>();
        String imgStr = rs.getString("image_urls");
        if (imgStr != null) imageList = Arrays.asList(imgStr.split(","));

        // 3. Lấy dữ liệu đặc thù (như đã hướng dẫn ở phần 1)
        Map<String, String> specAttrs = new HashMap<>();
        switch (category) {
            case VEHICLE:
                specAttrs.put("brand", rs.getString("brand"));
                specAttrs.put("model", rs.getString("model"));
                specAttrs.put("manufactureYear", String.valueOf(rs.getInt("manufacture_year")));
                specAttrs.put("mileage", String.valueOf(rs.getInt("mileage")));
                specAttrs.put("color", rs.getString("color"));
                specAttrs.put("fuelType", rs.getString("fuel_type"));
                specAttrs.put("transmission", rs.getString("transmission"));
                specAttrs.put("ownerCount", String.valueOf(rs.getInt("owner_count")));
                specAttrs.put("hasRegistration", String.valueOf(rs.getBoolean("has_registration")));
                break;

            case ELECTRONICS:
                specAttrs.put("brand", rs.getString("brand"));
                specAttrs.put("model", rs.getString("model"));
                specAttrs.put("warrantyMonths", String.valueOf(rs.getInt("warranty_months")));
                break;

            case ART:
                specAttrs.put("artist", rs.getString("artist"));
                specAttrs.put("year", String.valueOf(rs.getInt("art_year")));
                break;
        }

        // 4. Gọi Factory để "chốt đơn"
        return ItemFactory.createItemFromDB(
            id, category, name, desc, price,
            sellerId, imageList, condition, specAttrs
        );
    }
}