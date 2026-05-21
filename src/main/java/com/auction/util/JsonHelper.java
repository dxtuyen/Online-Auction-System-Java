package com.auction.util;

import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility convert Java object &lt;-&gt; JSON string bằng Gson.
 *
 * <p>Gson instance là thread-safe nên dùng chung toàn app.</p>
 *
 * <p><b>Vì sao phải custom Gson?</b>
 * <ul>
 *   <li>{@code BigDecimal}: mặc định khi deserialize vào {@code Map<String,Object>},
 *       Gson dùng {@code Double} → mất precision (0.1 + 0.2 ≠ 0.3). Ta ép
 *       {@code ToNumberPolicy.BIG_DECIMAL} để giữ chính xác giá tiền.</li>
 *   <li>{@code LocalDateTime}: Gson KHÔNG hỗ trợ sẵn → tự viết adapter
 *       round-trip qua chuỗi ISO-8601.</li>
 *   <li>{@code UUID}: Gson 2.10 đã hỗ trợ sẵn — không cần adapter.</li>
 * </ul>
 */
public class JsonHelper {

    private static final Gson gson = new GsonBuilder()
            // Giữ precision khi parse số vào Map<String,Object>
            .setObjectToNumberStrategy(ToNumberPolicy.BIG_DECIMAL)
            // BigDecimal explicit (tránh khoa học hoá kiểu 1.0E+2)
            .registerTypeAdapter(BigDecimal.class, new BigDecimalAdapter())
            // LocalDateTime ↔ ISO-8601 string
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    /** Chuyển object bất kỳ thành 1 dòng JSON (không xuống dòng). */
    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    /** JSON → Request object. */
    public static Request parseRequest(String json) {
        return gson.fromJson(json, Request.class);
    }

    /** JSON → Response object. */
    public static Response parseResponse(String json) {
        return gson.fromJson(json, Response.class);
    }

    /** JSON → bất kỳ class nào có constructor rỗng. */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }

    // ============== TYPE ADAPTERS ==============

    /**
     * BigDecimal: serialize ra dạng số nguyên dạng plain (không khoa học hoá),
     * deserialize giữ nguyên precision.
     */
    private static final class BigDecimalAdapter extends TypeAdapter<BigDecimal> {
        @Override
        public void write(JsonWriter out, BigDecimal value) throws IOException {
            if (value == null) { out.nullValue(); return; }
            out.value(value);
        }
        @Override
        public BigDecimal read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return new BigDecimal(in.nextString());
        }
    }

    /** LocalDateTime ↔ chuỗi ISO-8601 ("2026-05-08T14:30:00"). */
    private static final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) { out.nullValue(); return; }
            out.value(value.format(FMT));
        }
        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return LocalDateTime.parse(in.nextString(), FMT);
        }
    }
}
