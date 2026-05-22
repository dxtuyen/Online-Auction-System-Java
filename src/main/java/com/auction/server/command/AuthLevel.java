package com.auction.server.command;

/**
 * Mức xác thực yêu cầu cho mỗi action.
 *
 * <p>Đặt ngay tại nơi đăng ký command (xem {@link com.auction.server.RequestRouter})
 * để mỗi action khai báo rõ "ai được phép gọi" — không phải đọc tay từng controller.</p>
 *
 * <p>Cách dùng: router kiểm tra level TRƯỚC khi forward request cho handler.
 * Nếu thiếu auth → trả ngay {@code Response.error("...","Chưa đăng nhập")} mà
 * không cần controller tự check lặp đi lặp lại.</p>
 */
public enum AuthLevel {
    /** Không cần đăng nhập (LOGIN, REGISTER, LIST_AUCTIONS công khai...). */
    PUBLIC,
    /** Phải đăng nhập (PLACE_BID, CREATE_AUCTION, GET_PROFILE...). */
    USER,
    /** Phải đăng nhập và có role ADMIN (BAN_USER, LIST_USERS...). */
    ADMIN
}
