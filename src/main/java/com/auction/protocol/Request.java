package com.auction.protocol;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class Request {

    private String action;
    private Map<String, Object> data;
    private String token;
    private String requestId;

    public Request() {}

    public Request(String action, Map<String, Object> data, String token) {
        this.action = action;
        this.data = data;
        this.token = token;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

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
