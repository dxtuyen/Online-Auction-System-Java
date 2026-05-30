package com.auction.util;

import java.util.logging.Logger;

public final class AppLogger {

    static {

        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT [%4$-7s] %3$s - %5$s%6$s%n");
    }

    private AppLogger() {  }

    public static Logger get(Class<?> cls) {
        return Logger.getLogger(cls.getSimpleName());
    }
}
