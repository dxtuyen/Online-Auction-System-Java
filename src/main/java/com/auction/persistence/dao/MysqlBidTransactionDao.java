package com.auction.persistence.dao;

import com.auction.model.entity.BidTransaction;
import com.auction.model.enums.BidStatus;
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

public final class MysqlBidTransactionDao implements BidTransactionDao {

    private static final String COLS =
            "id, created_at, updated_at, auction_id, bidder_id, bid_amount, status";

    private static final String INSERT_SQL =
            "INSERT INTO bid_transactions (" + COLS + ") VALUES (?,?,?,?,?,?,?)";

    private static final String SELECT_ALL_SQL =
            "SELECT " + COLS + " FROM bid_transactions ORDER BY created_at";

    private static final String SELECT_BY_AUCTION_SQL =
            "SELECT " + COLS + " FROM bid_transactions WHERE auction_id=? ORDER BY created_at";

    private final Database db;

    public MysqlBidTransactionDao() {
        this.db = Database.getInstance();
    }

    @Override
    public void insert(BidTransaction bid) {
        Objects.requireNonNull(bid);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            ps.setString(1, bid.getId().toString());
            ps.setTimestamp(2, Timestamp.valueOf(bid.getCreatedAt()));
            ps.setTimestamp(3, Timestamp.valueOf(bid.getUpdatedAt()));
            ps.setString(4, bid.getAuctionId().toString());
            ps.setString(5, bid.getBidderId().toString());
            ps.setBigDecimal(6, bid.getBidAmount());
            ps.setString(7, bid.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Insert bid thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BidTransaction> findAll() {
        List<BidTransaction> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new PersistenceException("findAll bid thất bại: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<BidTransaction> findByAuctionId(UUID auctionId) {
        Objects.requireNonNull(auctionId);
        List<BidTransaction> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_BY_AUCTION_SQL)) {
            ps.setString(1, auctionId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new PersistenceException("findByAuctionId bid thất bại: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public long count() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM bid_transactions");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new PersistenceException("count bid thất bại: " + e.getMessage(), e);
        }
    }

    private static BidTransaction map(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        UUID auctionId = UUID.fromString(rs.getString("auction_id"));
        UUID bidderId = UUID.fromString(rs.getString("bidder_id"));
        BigDecimal amount = rs.getBigDecimal("bid_amount");
        BidStatus status = BidStatus.valueOf(rs.getString("status"));

        return new BidTransaction(id,
                createdAt.toLocalDateTime(),
                updatedAt.toLocalDateTime(),
                auctionId, bidderId, amount, status);
    }
}
