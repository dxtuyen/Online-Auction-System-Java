package com.auction.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Đọc config từ .env (dev) hoặc system env (production / Docker).
 *
 * <p>Ưu tiên: System.getenv() ⟶ .env file ở root project.
 * File .env KHÔNG được commit; team dùng .env.example làm template.</p>
 */
public final class AppConfig {

    private static final Map<String, String> ENV_FILE = loadEnvFile();

    private AppConfig() { /* static-only */ }

    /** Trả về giá trị config theo key, throw nếu thiếu. */
    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu config '" + key + "'. Kiểm tra .env hoặc biến môi trường.");
        }
        return value;
    }

    /** Trả về giá trị hoặc null - dùng cho config optional. */
    public static String get(String key) {
        Objects.requireNonNull(key);
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        return ENV_FILE.get(key);
    }

    /** Trả về giá trị hoặc {@code defaultValue} nếu thiếu/rỗng. */
    public static String get(String key, String defaultValue) {
        String v = get(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    /** Đọc int từ config; fallback về {@code defaultValue} nếu thiếu hoặc parse fail. */
    public static int getInt(String key, int defaultValue) {
        String v = get(key);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> map = new HashMap<>();
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) return map;

        try (BufferedReader r = Files.newBufferedReader(envPath)) {
            String line;
            while ((line = r.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                map.put(key, value);
            }
        } catch (IOException e) {
            // Không log secret — chỉ thông báo file đọc fail.
            System.err.println("[AppConfig] Không đọc được .env: " + e.getMessage());
        }
        return map;
    }
}
