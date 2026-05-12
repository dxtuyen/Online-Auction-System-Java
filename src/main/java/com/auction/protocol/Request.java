package com.auction.protocol;

import com.auction.util.MoneyHelper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request từ Client gửi lên Server.
 *
 * <p>Cấu trúc JSON chuẩn:
 * <pre>
 * {
 *   "action": "LOGIN",                    // tên hành động
 *   "data":   { "username":"alice", ... },// tham số tùy action
 *   "requestId":"550e8400-e29b-41d4-a716-446655440000", // correlation id
 *   "token":  "1"                         // id user đã đăng nhập, null nếu chưa
 * }
 * </pre>
 */
public class Request {

    /** Tên hành động: LOGIN, REGISTER, PLACE_BID, LIST_AUCTIONS,... */
    private String action;

    /** Các tham số đi kèm action, có thể rỗng. */
    private Map<String, Object> data;

    /** Correlation id để ghép đúng response với request tương ứng. */
    private String requestId;

    /** Id user đang đăng nhập, dạng String để tiện serialize JSON. */
    private String token;

    /** Constructor rỗng — Gson cần để parse JSON. */
    public Request() {}

    /** Constructor đầy đủ — dùng khi code Java muốn tạo Request rồi gửi đi. */
    public Request(String action, Map<String, Object> data, String requestId, String token) {
        this.action = action;
        this.data = data;
        this.requestId = requestId;
        this.token = token;
    }

    public String getAction() { return action; }
    public Map<String, Object> getData() { return data; }
    public String getRequestId() { return requestId; }
    public String getToken() { return token; }

    public void setAction(String action) { this.action = action; }
    public void setData(Map<String, Object> data) { this.data = data; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public void setToken(String token) { this.token = token; }

    // ---------- Helpers lấy value từ data an toàn ----------

    /** Lấy giá trị String từ data (trả null nếu không có). */
    public String getDataString(String key) {
        Object val = data != null ? data.get(key) : null;
        return val != null ? val.toString() : null;
    }

    /** Lấy double — Gson parse số JSON mặc định thành Double. */
    public double getDataDouble(String key) {
        Object val = data != null ? data.get(key) : null;
        if (val instanceof Number n) return n.doubleValue();
        return 0;
    }

    /** Lấy int — convert từ Number về int. */
    public int getDataInt(String key) {
        Object val = data != null ? data.get(key) : null;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    /** Lấy BigDecimal, trả về 0 nếu không có. */
    public BigDecimal getDataBigDecimal(String key) {
        Object val = data != null ? data.get(key) : null;
        return val == null ? BigDecimal.ZERO : MoneyHelper.parseRequestAmount(val, key);
    }

    // ---------- Helpers BẮT BUỘC: ném IllegalArgumentException nếu thiếu/sai ----------
    // RequestRouter đã catch RuntimeException và bọc thành Response.error(action, message),
    // nên client sẽ nhận về message thân thiện thay vì stack trace của NPE.

    /** Bắt buộc có giá trị String non-blank, ném lỗi friendly nếu thiếu. */
    public String requireString(String key) {
        String val = getDataString(key);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Thiếu trường '" + key + "'");
        }
        return val;
    }

    /** Parse UUID bắt buộc — chuẩn hóa lỗi "thiếu" và "sai format" thành cùng format message. */
    public UUID requireUUID(String key) {
        String val = requireString(key);
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Trường '" + key + "' không phải UUID hợp lệ: " + val);
        }
    }

    /** Parse enum bắt buộc — báo lỗi friendly nếu giá trị không thuộc enum. */
    public <E extends Enum<E>> E requireEnum(Class<E> enumClass, String key) {
        String val = requireString(key);
        try {
            return Enum.valueOf(enumClass, val);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Trường '" + key + "' không hợp lệ: " + val);
        }
    }

    /** Lấy double bắt buộc — phân biệt "thiếu" với "không phải số". */
    public double requireDouble(String key) {
        Object val = data != null ? data.get(key) : null;
        if (val == null) {
            throw new IllegalArgumentException("Thiếu trường '" + key + "'");
        }
        if (val instanceof Number n) return n.doubleValue();
        throw new IllegalArgumentException(
                "Trường '" + key + "' phải là số: " + val);
    }

    /** Lấy BigDecimal bắt buộc — giữ độ chính xác thay vì đi qua double. */
    public BigDecimal requireBigDecimal(String key) {
        Object val = data != null ? data.get(key) : null;
        return MoneyHelper.parseRequestAmount(val, key);
    }

    /** Lấy int bắt buộc — phân biệt "thiếu" với "không phải số". */
    public int requireInt(String key) {
        Object val = data != null ? data.get(key) : null;
        if (val == null) {
            throw new IllegalArgumentException("Thiếu trường '" + key + "'");
        }
        if (val instanceof Number n) return n.intValue();
        throw new IllegalArgumentException(
                "Trường '" + key + "' phải là số: " + val);
    }
}
