package com.auction.util;

import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.google.gson.Gson;

/**
 * Utility convert Java object &lt;-&gt; JSON string bằng Gson.
 *
 * <p>Gson instance là thread-safe nên có thể dùng chung toàn app
 * thay vì tạo mới mỗi lần.</p>
 */
public class JsonHelper {

    // Dùng chung — không tạo mới mỗi lần gọi để tiết kiệm
    private static final Gson gson = new Gson();

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
}
