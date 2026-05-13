package com.auction.protocol;

/**
 * Response từ Server gửi về Client.
 *
 * <p>3 loại response phân biệt qua {@code status}:
 * <ul>
 *   <li>{@code SUCCESS} — request xử lý thành công</li>
 *   <li>{@code ERROR}   — request thất bại, {@code message} chứa lý do</li>
 *   <li>{@code PUSH}    — server chủ động push (không phải reply cho request nào cụ thể)</li>
 * </ul>
 *
 * <p>{@code requestId} được server echo lại từ {@link Request#getRequestId()} cho các
 * response SUCCESS/ERROR — client dùng key này để ghép response với request đã gửi
 * (vì nhiều request cùng action có thể chạy song song trên 1 connection).
 * Response PUSH không có {@code requestId} vì không phải reply của ai.</p>
 */
public class Response {

    private String action;             // echo lại action để client biết response của request nào
    private ResponseStatus status;     // SUCCESS / ERROR / PUSH
    private String message;            // thông báo cho user
    private Object data;               // payload (Map/List/...)
    private String requestId;          // ghép với Request.requestId — null nếu PUSH

    public Response() {}

    // ============== Factory: SUCCESS ==============

    public static Response success(String action, String message, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = ResponseStatus.SUCCESS;
        r.message = message;
        r.data = data;
        return r;
    }

    // ============== Factory: ERROR ==============

    /** Lỗi không kèm payload (trường hợp thường gặp nhất). */
    public static Response error(String action, String message) {
        return error(action, message, null);
    }

    public static Response error(String action, String message, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = ResponseStatus.ERROR;
        r.message = message;
        r.data = data;
        return r;
    }

    // ============== Factory: PUSH ==============

    /** Push không có message kèm theo — chỉ data. */
    public static Response push(String action, Object data) {
        return push(action, null, data);
    }

    public static Response push(String action, String message, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = ResponseStatus.PUSH;
        r.message = message;
        r.data = data;
        return r;
    }

    // ============== Builder-style helper ==============

    /**
     * Gắn requestId rồi trả về chính mình — chainable trong 1 dòng:
     * <pre>send(Response.error("X","msg").withRequestId(rid));</pre>
     */
    public Response withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    // ============== Getters / Setters ==============

    public String getAction()         { return action; }
    public ResponseStatus getStatus() { return status; }
    public String getMessage()        { return message; }
    public Object getData()           { return data; }
    public String getRequestId()      { return requestId; }

    public void setAction(String action)         { this.action = action; }
    public void setStatus(ResponseStatus status) { this.status = status; }
    public void setMessage(String message)       { this.message = message; }
    public void setData(Object data)             { this.data = data; }
    public void setRequestId(String requestId)   { this.requestId = requestId; }

    // ============== Shortcuts tiện dụng ==============

    public boolean isSuccess() { return status == ResponseStatus.SUCCESS; }
    public boolean isError()   { return status == ResponseStatus.ERROR; }
    public boolean isPush()    { return status == ResponseStatus.PUSH; }
}
