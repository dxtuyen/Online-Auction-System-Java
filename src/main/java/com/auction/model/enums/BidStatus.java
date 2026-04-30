package com.auction.model.enums;

public enum BidStatus {
    PENDING,   // vừa tạo, chưa xác thực
    VALID,     // hợp lệ
    OUTBID,    // đã bị giá cao hơn vượt qua
    REJECTED,  // bị từ chối (vi phạm rule)
    CANCELLED  // bị hủy bởi user/system
}
