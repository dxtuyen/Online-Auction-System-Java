package com.auction.protocol;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request gửi từ Client lên Server.
 *
 * <p>Schema JSON:
 * <pre>
 * {
 *   "action": "PLACE_BID",
 *   "data":   { "auctionId": "...", "amount": 1000 },
 *   "token":  "userId-hoặc-session-token",   // optional
 *   "requestId": "uuid-do-client-sinh"        // optional, dùng để client ghép response
 * }
 * </pre>
 *
 * <p>Lưu ý:
 * <ul>
 *   <li>{@code action} lưu thành String để Gson không phải xử lý enum khi parse —
 *       server tự convert sang {@link ActionType} qua {@link ActionType#from(String)}
 *       trong {@code RequestRouter}.</li>
 *   <li>{@code token} lưu String thay vì UUID để client gửi linh hoạt (sau này có thể
 *       đổi sang JWT mà không phải đổi schema). Server CHỈ tin {@code Session},
 *       KHÔNG tin token client tự gửi.</li>
 *   <li>Các helper {@code getDataXxx} luôn null-safe — return null/giá trị mặc định
 *       khi key thiếu để controller không phải lặp NPE check.</li>
 * </ul>
 */
public class Request {

    private String action;
    private Map<String, Object> data;
    private String token;
    private String requestId;

    /** Gson cần constructor rỗng để deserialize. */
    public Request() {}

    public Request(String action, Map<String, Object> data, String token) {
        this.action = action;
        this.data = data;
        this.token = token;
    }

    // ============== Getters / Setters ==============

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    // ============== Helpers đọc data null-safe ==============

    public String getDataString(String key) {
        if (data == null || key == null) return null;
        Object val = data.get(key);
        if (val == null) return null;
        return val.toString();
    }

    public Integer getDataInteger(String key) {
        if (data == null || key == null) return null;
        Object val = data.get(key);
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Tiện cho controller — trả 0 khi thiếu/parse fail (caller tự validate sau đó). */
    public int getDataInt(String key) {
        Integer v = getDataInteger(key);
        return v == null ? 0 : v;
    }

    public Double getDataDouble(String key) {
        if (data == null || key == null) return null;
        Object val = data.get(key);
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean getDataBoolean(String key) {
        if (data == null || key == null) return false;
        Object v = data.get(key);
        if (v == null) return false;
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        try {
            return Boolean.parseBoolean(v.toString());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getDataUUID(String key) {
        String val = getDataString(key);
        if (val == null || val.isBlank()) return null;
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * BigDecimal cho số tiền — bắt buộc dùng để giữ precision.
     * Gson đã được config dùng BIG_DECIMAL policy nên Number ở đây thường đã là BigDecimal.
     */
    public BigDecimal getDataDecimal(String key) {
        String val = getDataString(key);
        if (val == null || val.isBlank()) return null;
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
