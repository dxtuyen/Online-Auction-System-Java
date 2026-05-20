package com.auction.protocol;

public enum ResponseStatus {
    SUCCESS(),
    ERROR(),
    PUSH();

    public static ResponseStatus from(String name) {
        if (name == null) return null;
        try {
            return ResponseStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
