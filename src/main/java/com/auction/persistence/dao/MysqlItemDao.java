package com.auction.persistence.dao;

import com.auction.model.entity.Art;
import com.auction.model.entity.Electronics;
import com.auction.model.entity.Item;
import com.auction.model.entity.OtherItem;
import com.auction.model.entity.Vehicle;
import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;
import com.auction.persistence.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MysqlItemDao implements ItemDao {

    private static final String COLS =
            "id, created_at, updated_at, item_type, name, description, seller_id, " +
            "starting_price, category, item_condition, " +
            "brand, model, warranty_months, " +
            "artist, year_created, medium, " +
            "make, vehicle_model, vehicle_year, mileage_km, " +
            "extra_info";

    private static final String INSERT_ITEM_SQL =
            "INSERT INTO items (" + COLS + ") VALUES " +
            "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE_ITEM_SQL =
            "UPDATE items SET updated_at=?, name=?, description=?, " +
            "starting_price=?, item_condition=?, extra_info=? WHERE id=?";

    private static final String SELECT_ITEM_SQL =
            "SELECT " + COLS + " FROM items";

    private static final String INSERT_IMAGE_SQL =
            "INSERT INTO item_images (item_id, image_url, position) VALUES (?,?,?)";

    private static final String DELETE_IMAGES_SQL =
            "DELETE FROM item_images WHERE item_id=?";

    private static final String SELECT_IMAGES_SQL =
            "SELECT image_url FROM item_images WHERE item_id=? ORDER BY position";

    private final Database db;

    public MysqlItemDao() {
        this.db = Database.getInstance();
    }

    @Override
    public void insert(Item item) {
        Objects.requireNonNull(item);
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                insertItemRow(c, item);
                insertImages(c, item.getId(), item.getImages());
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Insert item thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Item item) {
        Objects.requireNonNull(item);
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(UPDATE_ITEM_SQL)) {
                    ps.setTimestamp(1, Timestamp.valueOf(item.getUpdatedAt()));
                    ps.setString(2, item.getName());
                    ps.setString(3, item.getDescription());
                    ps.setBigDecimal(4, item.getStartingPrice());
                    ps.setString(5, item.getCondition().name());
                    ps.setString(6, item instanceof OtherItem o ? o.getExtraInfo() : null);
                    ps.setString(7, item.getId().toString());
                    int n = ps.executeUpdate();
                    if (n == 0) {
                        throw new PersistenceException(
                                "Item không tồn tại trong DB: " + item.getId());
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(DELETE_IMAGES_SQL)) {
                    ps.setString(1, item.getId().toString());
                    ps.executeUpdate();
                }
                insertImages(c, item.getId(), item.getImages());
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Update item thất bại: " + e.getMessage(), e);
        }
    }

    private void insertItemRow(Connection c, Item item) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERT_ITEM_SQL)) {
            ps.setString(1, item.getId().toString());
            ps.setTimestamp(2, Timestamp.valueOf(item.getCreatedAt()));
            ps.setTimestamp(3, Timestamp.valueOf(item.getUpdatedAt()));
            ps.setString(4, itemTypeOf(item));
            ps.setString(5, item.getName());
            ps.setString(6, item.getDescription());
            ps.setString(7, item.getSellerId().toString());
            ps.setBigDecimal(8, item.getStartingPrice());
            ps.setString(9, item.getCategory().name());
            ps.setString(10, item.getCondition().name());

            setElectronics(ps, 11, item);
            setArt(ps, 14, item);
            setVehicle(ps, 17, item);
            ps.setString(21, item instanceof OtherItem o ? o.getExtraInfo() : null);

            ps.executeUpdate();
        }
    }

    private void insertImages(Connection c, UUID itemId, List<String> images) throws SQLException {
        if (images == null || images.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement(INSERT_IMAGE_SQL)) {
            int pos = 0;
            for (String url : images) {
                ps.setString(1, itemId.toString());
                ps.setString(2, url);
                ps.setInt(3, pos++);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public Optional<Item> findById(UUID id) {
        Objects.requireNonNull(id);
        Item item;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ITEM_SQL + " WHERE id=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                item = mapRow(rs, loadImages(c, id));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Find item thất bại: " + e.getMessage(), e);
        }
        return Optional.of(item);
    }

    @Override
    public List<Item> findAll() {

        Map<UUID, List<String>> imagesByItem = loadAllImages();
        List<Item> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ITEM_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                List<String> imgs = imagesByItem.getOrDefault(id, List.of());
                result.add(mapRow(rs, imgs));
            }
        } catch (SQLException e) {
            throw new PersistenceException("findAll item thất bại: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public long count() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM items");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new PersistenceException("count item thất bại: " + e.getMessage(), e);
        }
    }

    private List<String> loadImages(Connection c, UUID itemId) throws SQLException {
        List<String> images = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_IMAGES_SQL)) {
            ps.setString(1, itemId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) images.add(rs.getString(1));
            }
        }
        return images;
    }

    private Map<UUID, List<String>> loadAllImages() {
        Map<UUID, List<String>> map = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT item_id, image_url FROM item_images ORDER BY item_id, position");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID itemId = UUID.fromString(rs.getString("item_id"));
                map.computeIfAbsent(itemId, k -> new ArrayList<>()).add(rs.getString("image_url"));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Load images thất bại: " + e.getMessage(), e);
        }
        return map;
    }

    private static Item mapRow(ResultSet rs, List<String> images) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        LocalDateTime updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
        String name = rs.getString("name");
        String description = rs.getString("description");
        UUID sellerId = UUID.fromString(rs.getString("seller_id"));
        BigDecimal startingPrice = rs.getBigDecimal("starting_price");
        ItemCategory category = ItemCategory.valueOf(rs.getString("category"));
        ItemCondition condition = ItemCondition.valueOf(rs.getString("item_condition"));
        String type = rs.getString("item_type");

        return switch (type) {
            case "ELECTRONICS" -> new Electronics(
                    id, createdAt, updatedAt,
                    name, description, sellerId, startingPrice, images, condition,
                    rs.getString("brand"),
                    rs.getString("model"),
                    rs.getInt("warranty_months")
            );
            case "ART" -> new Art(
                    id, createdAt, updatedAt,
                    name, description, sellerId, startingPrice, images, condition,
                    rs.getString("artist"),
                    (Integer) rs.getObject("year_created"),
                    rs.getString("medium")
            );
            case "VEHICLE" -> new Vehicle(
                    id, createdAt, updatedAt,
                    name, description, sellerId, startingPrice, images, condition,
                    rs.getString("make"),
                    rs.getString("vehicle_model"),
                    rs.getInt("vehicle_year"),
                    rs.getInt("mileage_km")
            );
            case "OTHER" -> new OtherItem(
                    id, createdAt, updatedAt,
                    name, description, sellerId, startingPrice, images,
                    category, condition,
                    rs.getString("extra_info")
            );
            default -> throw new PersistenceException("Unknown item_type: " + type);
        };
    }

    private static String itemTypeOf(Item item) {
        if (item instanceof Electronics) return "ELECTRONICS";
        if (item instanceof Art) return "ART";
        if (item instanceof Vehicle) return "VEHICLE";
        if (item instanceof OtherItem) return "OTHER";
        throw new PersistenceException("Unknown item subtype: " + item.getClass().getName());
    }

    private static void setElectronics(PreparedStatement ps, int startIdx, Item item)
            throws SQLException {
        if (item instanceof Electronics e) {
            ps.setString(startIdx, e.getBrand());
            ps.setString(startIdx + 1, e.getModel());
            ps.setInt(startIdx + 2, e.getWarrantyMonths());
        } else {
            ps.setNull(startIdx, java.sql.Types.VARCHAR);
            ps.setNull(startIdx + 1, java.sql.Types.VARCHAR);
            ps.setNull(startIdx + 2, java.sql.Types.INTEGER);
        }
    }

    private static void setArt(PreparedStatement ps, int startIdx, Item item)
            throws SQLException {
        if (item instanceof Art a) {
            ps.setString(startIdx, a.getArtist());
            if (a.getYearCreated() == null) {
                ps.setNull(startIdx + 1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(startIdx + 1, a.getYearCreated());
            }
            ps.setString(startIdx + 2, a.getMedium());
        } else {
            ps.setNull(startIdx, java.sql.Types.VARCHAR);
            ps.setNull(startIdx + 1, java.sql.Types.INTEGER);
            ps.setNull(startIdx + 2, java.sql.Types.VARCHAR);
        }
    }

    private static void setVehicle(PreparedStatement ps, int startIdx, Item item)
            throws SQLException {
        if (item instanceof Vehicle v) {
            ps.setString(startIdx, v.getMake());
            ps.setString(startIdx + 1, v.getModel());
            ps.setInt(startIdx + 2, v.getYear());
            ps.setInt(startIdx + 3, v.getMileageKm());
        } else {
            ps.setNull(startIdx, java.sql.Types.VARCHAR);
            ps.setNull(startIdx + 1, java.sql.Types.VARCHAR);
            ps.setNull(startIdx + 2, java.sql.Types.INTEGER);
            ps.setNull(startIdx + 3, java.sql.Types.INTEGER);
        }
    }
}
