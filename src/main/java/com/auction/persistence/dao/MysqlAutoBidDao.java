package com.auction.persistence.dao;

import com.auction.model.entity.AutoBid;
import com.auction.persistence.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC implementation cho AutoBidDao.
 */
public final class MysqlAutoBidDao implements AutoBidDao {

    private static final String COLS =
            "bidder_id, auction_id, max_bid, increment, created_at, active";

    private static final String INSERT_SQL =
            "INSERT INTO auto_bids (" + COLS + ") VALUES (?,?,?,?,?,?)";

    private static final String DELETE_SQL =
            "DELETE FROM auto_bids WHERE bidder_id=? AND auction_id=?";

    private static final String UPDATE_ACTIVE_SQL =
            "UPDATE auto_bids SET active=? WHERE bidder_id=? AND auction_id=?";

    private static final String SELECT_ALL_SQL =
            "SELECT " + COLS + " FROM auto_bids";

    private final Database db;

    public MysqlAutoBidDao() {
        this.db = Database.getInstance();
    }

    @Override
    public void insert(AutoBid ab) {
        Objects.requireNonNull(ab);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            ps.setString(1, ab.getBidderId().toString());
            ps.setString(2, ab.getAuctionId().toString());
            ps.setBigDecimal(3, ab.getMaxBid());
            ps.setBigDecimal(4, ab.getIncrement());
            ps.setTimestamp(5, Timestamp.valueOf(ab.getCreatedAt()));
            ps.setBoolean(6, ab.isActive());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Insert auto-bid thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByBidderAndAuction(UUID bidderId, UUID auctionId) {
        Objects.requireNonNull(bidderId);
        Objects.requireNonNull(auctionId);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(DELETE_SQL)) {
            ps.setString(1, bidderId.toString());
            ps.setString(2, auctionId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Delete auto-bid thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateActive(UUID bidderId, UUID auctionId, boolean active) {
        Objects.requireNonNull(bidderId);
        Objects.requireNonNull(auctionId);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(UPDATE_ACTIVE_SQL)) {
            ps.setBoolean(1, active);
            ps.setString(2, bidderId.toString());
            ps.setString(3, auctionId.toString());
            ps.executeUpdate();
            // Không throw nếu rowCount=0: row có thể đã bị xóa, deactivate là idempotent
        } catch (SQLException e) {
            throw new PersistenceException("Update auto-bid active thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AutoBid> findAll() {
        List<AutoBid> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new PersistenceException("findAll auto-bid thất bại: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public long count() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM auto_bids");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new PersistenceException("count auto-bid thất bại: " + e.getMessage(), e);
        }
    }

    private static AutoBid map(ResultSet rs) throws SQLException {
        UUID bidderId = UUID.fromString(rs.getString("bidder_id"));
        UUID auctionId = UUID.fromString(rs.getString("auction_id"));
        BigDecimal maxBid = rs.getBigDecimal("max_bid");
        BigDecimal increment = rs.getBigDecimal("increment");
        Timestamp createdAt = rs.getTimestamp("created_at");
        boolean active = rs.getBoolean("active");

        return new AutoBid(bidderId, auctionId, maxBid, increment,
                createdAt.toLocalDateTime(), active);
    }
}
