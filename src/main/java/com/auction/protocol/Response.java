package com.auction.protocol;

/**
 * Response từ Server gửi về Client.
 *
 * <p>Có 3 loại response phân biệt qua trường {@code status}:
 * <ul>
 *   <li>{@code SUCCESS} — request xử lý thành công</li>
 *   <li>{@code ERROR}   — request thất bại, message chứa lý do</li>
 *   <li>{@code PUSH}    — server chủ động push tới client (VD: bid mới)</li>
 * </ul>
 */
public class Response {

    private String action;   // echo lại action từ Request để client biết response cho gì
    private String status;   // SUCCESS / ERROR / PUSH
    private String message;  // thông báo dành cho user
    private Object data;     // kết quả (Map, List,... tùy action)

    public Response() {}

    // ---------- Factory methods — tạo response nhanh gọn ----------

    public static Response success(String action, String message, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = "SUCCESS";
        r.message = message;
        r.data = data;
        return r;
    }

    public static Response error(String action, String message) {
        Response r = new Response();
        r.action = action;
        r.status = "ERROR";
        r.message = message;
        return r;
    }

    public static Response push(String action, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = "PUSH";
        r.data = data;
        return r;
    }

    // Getters / Setters (Gson cần setter để parse JSON)
    public String getAction() { return action; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Object getData() { return data; }

    public void setAction(String action) { this.action = action; }
    public void setStatus(String status) { this.status = status; }
    public void setMessage(String message) { this.message = message; }
    public void setData(Object data) { this.data = data; }

    // Shortcuts tiện dụng
    public boolean isSuccess() { return "SUCCESS".equals(status); }
    public boolean isError()   { return "ERROR".equals(status); }
    public boolean isPush()    { return "PUSH".equals(status); }
}
