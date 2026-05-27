package com.auction.util;

import com.auction.protocol.Request;
import com.auction.protocol.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonHelper")
class JsonHelperTest {

    @Test
    @DisplayName("toJson + parseRequest round-trip")
    void requestRoundTrip() {
        Request r = new Request("LOGIN", Map.of("username", "alice"), "tok");
        r.setRequestId("rid-1");

        String json = JsonHelper.toJson(r);
        Request parsed = JsonHelper.parseRequest(json);

        assertEquals("LOGIN", parsed.getAction());
        assertEquals("tok", parsed.getToken());
        assertEquals("rid-1", parsed.getRequestId());
        assertEquals("alice", parsed.getDataString("username"));
    }

    @Test
    @DisplayName("parseRequest giữ precision BigDecimal cho số tiền")
    void bigDecimalPrecision() {
        String json = "{\"action\":\"PLACE_BID\",\"data\":{\"amount\":1500000}}";
        Request parsed = JsonHelper.parseRequest(json);
        assertEquals(new BigDecimal("1500000"), parsed.getDataDecimal("amount"));
    }

    @Test
    @DisplayName("parseResponse round-trip")
    void responseRoundTrip() {
        Response r = Response.success("LOGIN", "ok", Map.of("k", "v"));
        String json = JsonHelper.toJson(r);
        Response parsed = JsonHelper.parseResponse(json);
        assertTrue(parsed.isSuccess());
        assertEquals("LOGIN", parsed.getAction());
    }

    @Test
    @DisplayName("fromJson generic")
    void fromJsonGeneric() {
        String json = "{\"action\":\"X\",\"token\":\"t\"}";
        Request r = JsonHelper.fromJson(json, Request.class);
        assertEquals("X", r.getAction());
    }

    @Test
    @DisplayName("LocalDateTime serialize ISO-8601 round-trip")
    void localDateTimeAdapter() {
        Holder h = new Holder();
        h.time = LocalDateTime.of(2026, 5, 8, 14, 30, 0);
        h.amount = new BigDecimal("99.50");

        String json = JsonHelper.toJson(h);
        assertTrue(json.contains("2026-05-08T14:30:00"));

        Holder parsed = JsonHelper.fromJson(json, Holder.class);
        assertEquals(h.time, parsed.time);
        assertEquals(0, new BigDecimal("99.50").compareTo(parsed.amount));
    }

    @Test
    @DisplayName("BigDecimal null serialize được")
    void bigDecimalNull() {
        Holder h = new Holder();
        String json = JsonHelper.toJson(h);
        Holder parsed = JsonHelper.fromJson(json, Holder.class);
        assertNull(parsed.amount);
        assertNull(parsed.time);
    }

    static final class Holder {
        LocalDateTime time;
        BigDecimal amount;
    }
}
