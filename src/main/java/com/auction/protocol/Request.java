package com.auction.protocol;

import com.auction.protocol.ActionType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class Request {
    private ActionType actionType;
    private Map<String, Object> data;
    private UUID token;

    public Request(ActionType actionType, Map<String, Object> data, UUID token) {
        this.actionType = actionType;
        this.data = data;
        this.token = token;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public UUID getToken() {
        return token;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public void setToken(UUID token) {
        this.token = token;
    }

    public String getStringData(String key) {
        if (data == null) return null;
        Object value = data.get(key);
        if (value == null) return null;
        return value.toString();
    }

    public Double getDoubleData(String key) {
        if (data == null) return null;
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean getBooleanData(String key) {
        if (data == null) return false;
        Object value = data.get(key);
        if (value == null) return false;
        if (value instanceof Boolean) return (boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    public UUID getUUIDData(String key) {
        String value = getStringData(key);
        if(value == null || value.isBlank()) return  null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public BigDecimal getDecimalData(String key) {
        if (data == null) return null;
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        try {
            return new BigDecimal(value.toString());
        } catch (IllegalArgumentException e) {
            return  null;
        }
    }
}