package com.auction.util;

import java.util.logging.Logger;

/**
 * Cấu hình logger chung cho toàn app, dựa trên java.util.logging (không thêm dependency).
 *
 * <p>Một dòng log có dạng: {@code 2026-05-14 10:00:00 [INFO ] ClassName - message}.
 * Set system property trong static block để mọi {@link java.util.logging.SimpleFormatter}
 * format theo dạng này.</p>
 *
 * <p>Dùng: {@code private static final Logger log = AppLogger.get(MyClass.class);}</p>
 */
public final class AppLogger {

    static {
        // %1=time, %4=level, %3=loggerName, %5=msg, %6=throwable
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT [%4$-7s] %3$s - %5$s%6$s%n");
    }

    private AppLogger() { /* static-only */ }

    public static Logger get(Class<?> cls) {
        return Logger.getLogger(cls.getSimpleName());
    }
}
