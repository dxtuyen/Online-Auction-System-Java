package com.auction.model.exception;

/**
 * Lỗi xác thực đăng nhập.
 *
 * QUAN TRỌNG: KHÔNG phân biệt "user không tồn tại" và "sai password" trong
 * message → tránh username enumeration. Attacker thử login hàng loạt sẽ
 * không biết được username nào tồn tại.
 *
 * Cũng dùng cho: tài khoản bị khóa (BANNED), hết hạn token, v.v.
 */
public class AuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(message);
    }
}
