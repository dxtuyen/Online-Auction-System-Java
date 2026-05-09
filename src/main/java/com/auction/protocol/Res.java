package com.auction.protocol;

public class Res {
    private String echo;
    private Object data;
    private ResponseStatus status;
    private String message;

    public Res() {
    }

    public static Res success(String echo, Object data, String message) {
        Res successRes = new Res();
        successRes.echo = echo;
        successRes.data = data;
        successRes.status = ResponseStatus.SUCCESS;
        successRes.message = message;
        return successRes;
    }

    public static Res error(String echo, Object data, String message) {
        Res errorRes = new Res();
        errorRes.echo = echo;
        errorRes.data = data;
        errorRes.status = ResponseStatus.ERROR;
        errorRes.message = message;
        return errorRes;
    }

    public static Res push(String echo, Object data, String message) {
        Res pushRes = new Res();
        pushRes.echo = echo;
        pushRes.data = data;
        pushRes.status = ResponseStatus.PUSH;
        pushRes.message = message;
        return pushRes;
    }

    public String getEcho() {
        return echo;
    }

    public Object getData() {
        return data;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public void setEcho(String echo) {
        this.echo = echo;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public void setSuccessStatus(ResponseStatus status) {
        this.status = status;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
