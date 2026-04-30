package com.auction.server.dao;

import com.auction.model.entity.Auction;
import com.auction.model.enums.AuctionStatus;

import java.util.List;

public interface AuctionDAO {
    // Thêm phiên đấu giá mới
    boolean insert(Auction auction);

    // Tìm kiếm theo ID
    Auction findById(int id);

    // Lấy danh sách tất cả các phiên
    List<Auction> findAll();

    // Cập nhật thông tin đấu giá (khi có người trả giá mới hoặc đổi trạng thái)
    boolean update(Auction auction);

    // Tìm các phiên theo trạng thái (ví dụ: RUNNING)
    List<Auction> findByStatus(AuctionStatus status);
}