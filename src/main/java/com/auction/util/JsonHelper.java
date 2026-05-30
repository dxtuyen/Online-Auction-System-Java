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

public class JsonHelper {

    private static final Gson gson = new GsonBuilder()

            .setObjectToNumberStrategy(ToNumberPolicy.BIG_DECIMAL)

            .registerTypeAdapter(BigDecimal.class, new BigDecimalAdapter())

            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    public static Request parseRequest(String json) {
        return gson.fromJson(json, Request.class);
    }

    public static Response parseResponse(String json) {
        return gson.fromJson(json, Response.class);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }

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
