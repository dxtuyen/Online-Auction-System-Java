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
    private ResponseStatus status;   // SUCCESS / ERROR / PUSH
    private String message;  // thông báo dành cho user
    private Object data;     // kết quả (Map, List,... tùy action)

    public Response() {}

    // ---------- Factory methods — tạo response nhanh gọn ----------

    public static Response success(String action, String message, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = ResponseStatus.SUCCESS;
        r.message = message;
        r.data = data;
        return r;
    }

    public static Response error(String action, String message, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = ResponseStatus.ERROR;
        r.message = message;
        r.data = data;
        return r;
    }

    /** Overload tiện dụng — error không cần data. */
    public static Response error(String action, String message) {
        return error(action, message, null);
    }

    public static Response push(String action, String message, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = ResponseStatus.PUSH;
        r.message = message;
        r.data = data;
        return r;
    }

    /** Overload tiện dụng — push không cần message. */
    public static Response push(String action, Object data) {
        return push(action, null, data);
    }

    // Getters / Setters (Gson cần setter để parse JSON)
    public String getAction() { return action; }
    public ResponseStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Object getData() { return data; }

    public void setAction(String action) { this.action = action; }
    public void setStatus(ResponseStatus status) { this.status = status; }
    public void setMessage(String message) { this.message = message; }
    public void setData(Object data) { this.data = data; }

    // Shortcuts tiện dụng
    public boolean isSuccess() { return ResponseStatus.SUCCESS == status; }
    public boolean isError()   { return ResponseStatus.ERROR == status; }
    public boolean isPush()    { return ResponseStatus.PUSH == status; }
}
