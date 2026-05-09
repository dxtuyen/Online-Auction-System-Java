package com.auction.protocol;

/**
 * Loại Response — phân biệt:
 * <ul>
 *   <li>{@code SUCCESS} — request xử lý thành công</li>
 *   <li>{@code ERROR}   — request thất bại</li>
 *   <li>{@code PUSH}    — server chủ động push, không phải reply cho 1 request</li>
 * </ul>
 */
public enum ResponseStatus {
    SUCCESS,
    ERROR,
    PUSH;

    /** Parse tên status từ JSON. Trả {@code null} nếu chuỗi không hợp lệ. */
    public static ResponseStatus from(String name) {
        if (name == null) return null;
        try {
            return ResponseStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
