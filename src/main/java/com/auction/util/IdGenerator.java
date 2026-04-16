package com.auction.util;

import java.util.concurrent.atomic.AtomicInteger;

public class IdGenerator {
    // AtomicInteger: thread-safe, tự tăng không bao giờ trùng
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static String generate(String prefix) {
        // "U" → "U0001", "I" → "I0002", "A" → "A0003"
        return prefix + String.format("%04d", counter.incrementAndGet());
    }
}