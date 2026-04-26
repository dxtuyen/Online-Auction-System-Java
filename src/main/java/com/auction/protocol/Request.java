package com.auction.protocol;

import java.util.Map;

/**
 * Request từ Client gửi lên Server.
 *
 * <p>Cấu trúc JSON chuẩn:
 * <pre>
 * {
 *   "action": "LOGIN",                    // tên hành động
 *   "data":   { "username":"alice", ... },// tham số tùy action
 *   "token":  "1"                         // id user đã đăng nhập, null nếu chưa
 * }
 * </pre>
 */
public class Request {

    /** Tên hành động: LOGIN, REGISTER, PLACE_BID, LIST_AUCTIONS,... */
    private String action;

    /** Các tham số đi kèm action, có thể rỗng. */
    private Map<String, Object> data;

    /** Id user đang đăng nhập, dạng String để tiện serialize JSON. */
    private String token;

    /** Constructor rỗng — Gson cần để parse JSON. */
    public Request() {}

    /** Constructor đầy đủ — dùng khi code Java muốn tạo Request rồi gửi đi. */
    public Request(String action, Map<String, Object> data, String token) {
        this.action = action;
        this.data = data;
        this.token = token;
    }

    public String getAction() { return action; }
    public Map<String, Object> getData() { return data; }
    public String getToken() { return token; }

    public void setAction(String action) { this.action = action; }
    public void setData(Map<String, Object> data) { this.data = data; }
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
}
