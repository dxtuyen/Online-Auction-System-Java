package com.auction.protocol;

public class Response {

    private String action;
    private ResponseStatus status;
    private String message;
    private Object data;
    private String requestId;

    public Response() {}

    public static Response success(String action, String message, Object data) {
        Response r = new Response();
        r.action = action;
        r.status = ResponseStatus.SUCCESS;
        r.message = message;
        r.data = data;
        return r;
    }

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

    public Response withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

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

    public boolean isSuccess() { return status == ResponseStatus.SUCCESS; }
    public boolean isError()   { return status == ResponseStatus.ERROR; }
    public boolean isPush()    { return status == ResponseStatus.PUSH; }
}
