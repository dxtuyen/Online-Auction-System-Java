package com.auction.protocol;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request từ Client gửi lên Server.
 *
 * <p>Wire format: 1 dòng JSON, gồm 3 field:
 * <ul>
 *   <li>{@code action} — tên action (chuỗi); xem {@link ActionType} cho danh sách hợp lệ</li>
 *   <li>{@code data}   — payload tự do, key-value</li>
 *   <li>{@code token}  — định danh phiên đăng nhập (thường là userId UUID dạng String)</li>
 * </ul>
 *
 * <p>Field giữ kiểu String để Gson serialize/deserialize đơn giản. Caller cần
 * type-safety thì gọi {@link #getActionType()} hoặc {@link #getTokenUUID()}.</p>
 */
public class Request {

    private String action;
    private Map<String, Object> data;
    private String token;

    public Request() {} // Gson cần constructor rỗng

    public Request(String action, Map<String, Object> data, String token) {
        this.action = action;
        this.data = data;
        this.token = token;
    }

    // ============== CORE ACCESSORS ==============

    public String getAction() { return action; }
    public Map<String, Object> getData() { return data; }
    public String getToken() { return token; }

    public void setAction(String action) { this.action = action; }
    public void setData(Map<String, Object> data) { this.data = data; }
    public void setToken(String token) { this.token = token; }

    // ============== TYPE-SAFE PARSE ==============

    /** Parse {@code action} sang enum. Trả {@code null} nếu chuỗi không hợp lệ. */
    public ActionType getActionType() {
        return ActionType.from(action);
    }

    /** Parse {@code token} sang UUID. Trả {@code null} nếu rỗng/không hợp lệ. */
    public UUID getTokenUUID() {
        if (token == null || token.isBlank()) return null;
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ============== DATA HELPERS ==============
    // Convention: getDataXxx — đọc field bên trong map data theo kiểu Xxx

    public String getDataString(String key) {
        if (data == null) return null;
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }

    /** Trả 0 nếu thiếu — caller tự xử lý default cho phù hợp business. */
    public int getDataInt(String key) {
        if (data == null) return 0;
        Object v = data.get(key);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Trả 0.0 nếu thiếu — caller tự xử lý default cho phù hợp business. */
    public double getDataDouble(String key) {
        if (data == null) return 0.0;
        Object v = data.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public boolean getDataBoolean(String key) {
        if (data == null) return false;
        Object v = data.get(key);
        if (v == null) return false;
        if (v instanceof Boolean) return (boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    public UUID getDataUUID(String key) {
        String v = getDataString(key);
        if (v == null || v.isBlank()) return null;
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Đọc số tiền: ép qua String để KHÔNG mất precision (tránh Double round). */
    public BigDecimal getDataDecimal(String key) {
        String v = getDataString(key);
        if (v == null || v.isBlank()) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
