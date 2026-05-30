package com.auction.persistence.dao;

import com.auction.model.entity.Auction;
import com.auction.model.enums.AuctionStatus;
import com.auction.persistence.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MysqlAuctionDao implements AuctionDao {

    private static final String COLS =
            "id, created_at, updated_at, item_id, seller_id, " +
            "start_time, end_time, starting_price, current_price, minimum_increment, " +
            "highest_bidder_id, status, total_bids";

    private static final String INSERT_SQL =
            "INSERT INTO auctions (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE_SQL =
            "UPDATE auctions SET updated_at=?, end_time=?, current_price=?, " +
            "highest_bidder_id=?, status=?, total_bids=? WHERE id=?";

    private static final String SELECT_SQL =
            "SELECT " + COLS + " FROM auctions";

    private final Database db;

    public MysqlAuctionDao() {
        this.db = Database.getInstance();
    }

    @Override
    public void insert(Auction auction) {
        Objects.requireNonNull(auction);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            ps.setString(1, auction.getId().toString());
            ps.setTimestamp(2, Timestamp.valueOf(auction.getCreatedAt()));
            ps.setTimestamp(3, Timestamp.valueOf(auction.getUpdatedAt()));
            ps.setString(4, auction.getItemId().toString());
            ps.setString(5, auction.getSellerId().toString());
            ps.setTimestamp(6, Timestamp.valueOf(auction.getStartTime()));
            ps.setTimestamp(7, Timestamp.valueOf(auction.getEndTime()));
            ps.setBigDecimal(8, auction.getStartingPrice());
            ps.setBigDecimal(9, auction.getCurrentPrice());
            ps.setBigDecimal(10, auction.getMinimumIncrement());
            setNullableUuid(ps, 11, auction.getHighestBidderId());
            ps.setString(12, auction.getStatus().name());
            ps.setInt(13, auction.getTotalBids());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Insert auction thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Auction auction) {
        Objects.requireNonNull(auction);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(UPDATE_SQL)) {
            ps.setTimestamp(1, Timestamp.valueOf(auction.getUpdatedAt()));
            ps.setTimestamp(2, Timestamp.valueOf(auction.getEndTime()));
            ps.setBigDecimal(3, auction.getCurrentPrice());
            setNullableUuid(ps, 4, auction.getHighestBidderId());
            ps.setString(5, auction.getStatus().name());
            ps.setInt(6, auction.getTotalBids());
            ps.setString(7, auction.getId().toString());
            int n = ps.executeUpdate();
            if (n == 0) {
                throw new PersistenceException(
                        "Auction không tồn tại trong DB: " + auction.getId());
            }
        } catch (SQLException e) {
            throw new PersistenceException("Update auction thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Auction> findById(UUID id) {
        Objects.requireNonNull(id);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_SQL + " WHERE id=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Find auction thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Auction> findAll() {
        List<Auction> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new PersistenceException("findAll auction thất bại: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public long count() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM auctions");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new PersistenceException("count auction thất bại: " + e.getMessage(), e);
        }
    }

    private static Auction mapRow(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        LocalDateTime updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
        UUID itemId = UUID.fromString(rs.getString("item_id"));
        UUID sellerId = UUID.fromString(rs.getString("seller_id"));
        LocalDateTime startTime = rs.getTimestamp("start_time").toLocalDateTime();
        LocalDateTime endTime = rs.getTimestamp("end_time").toLocalDateTime();
        BigDecimal startingPrice = rs.getBigDecimal("starting_price");
        BigDecimal currentPrice = rs.getBigDecimal("current_price");
        BigDecimal minimumIncrement = rs.getBigDecimal("minimum_increment");

        String highestStr = rs.getString("highest_bidder_id");
        UUID highestBidderId = highestStr == null ? null : UUID.fromString(highestStr);

        AuctionStatus status = AuctionStatus.valueOf(rs.getString("status"));
        int totalBids = rs.getInt("total_bids");

        return new Auction(id, createdAt, updatedAt, itemId, sellerId,
                startTime, endTime, startingPrice, currentPrice, minimumIncrement,
                highestBidderId, status, totalBids);
    }

    private static void setNullableUuid(PreparedStatement ps, int idx, UUID id) throws SQLException {
        if (id == null) ps.setNull(idx, java.sql.Types.CHAR);
        else ps.setString(idx, id.toString());
    }
}
