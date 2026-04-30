package com.auction.server.dao;

import com.auction.model.entity.Auction;
import com.auction.model.enums.AuctionStatus;
import com.auction.server.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAOImpl implements AuctionDAO {

    @Override
    public boolean insert(Auction auction) {
        String sql = "INSERT INTO auctions (item_id, seller_id, start_time, end_time, starting_price, current_price, " +
                "highest_bidder_id, minimum_increment, status, total_bids) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, auction.getItemId());
            ps.setInt(2, auction.getSellerId());
            ps.setTimestamp(3, Timestamp.valueOf(auction.getStartTime()));
            ps.setTimestamp(4, Timestamp.valueOf(auction.getEndTime()));
            ps.setDouble(5, auction.getStartingPrice());
            ps.setDouble(6, auction.getCurrentPrice());

            // highestBidderId có thể null nên cần xử lý kỹ
            if (auction.getHighestBidderId() != null) {
                ps.setInt(7, auction.getHighestBidderId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }

            ps.setDouble(8, auction.getMinimumIncrement());
            ps.setString(9, auction.getStatus().name());
            ps.setInt(10, auction.getTotalBids());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Auction findById(int id) {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAuction(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Auction> findAll() {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                auctions.add(mapResultSetToAuction(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return auctions;
    }

    @Override
    public boolean update(Auction auction) {
        String sql = "UPDATE auctions SET current_price = ?, highest_bidder_id = ?, status = ?, total_bids = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1, auction.getCurrentPrice());
            if (auction.getHighestBidderId() != null) {
                ps.setInt(2, auction.getHighestBidderId());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, auction.getStatus().name());
            ps.setInt(4, auction.getTotalBids());
            ps.setInt(5, auction.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Auction> findByStatus(AuctionStatus status) {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    auctions.add(mapResultSetToAuction(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return auctions;
    }

    // Hàm Helper Mapping - Đúng phong cách của nhóm bạn
    private Auction mapResultSetToAuction(ResultSet rs) throws SQLException {
        Auction a = new Auction();

        // Giả sử Entity class có setId
        a.setId(rs.getInt("id"));

        a.setItemId(rs.getInt("item_id"));
        a.setSellerId(rs.getInt("seller_id"));

        // Chuyển từ Timestamp sang LocalDateTime
        a.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
        a.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());

        a.setStartingPrice(rs.getDouble("starting_price"));
        a.setCurrentPrice(rs.getDouble("current_price"));

        // Xử lý Integer có thể Null cho highestBidderId
        int bidderId = rs.getInt("highest_bidder_id");
        if (!rs.wasNull()) {
            a.setHighestBidderId(bidderId);
        }

        a.setMinimumIncrement(rs.getDouble("minimum_increment"));

        // Mapping Enum Status
        a.setStatus(AuctionStatus.valueOf(rs.getString("status")));

        a.setTotalBids(rs.getInt("total_bids"));

        return a;
    }
}
