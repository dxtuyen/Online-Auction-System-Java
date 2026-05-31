package com.auction.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Request protocol")
class RequestTest {

    private Request withData(Map<String, Object> data) {
        Request r = new Request("ACTION", data, "token-1");
        r.setRequestId("rid-1");
        return r;
    }

    @Test
    @DisplayName("Getters/setters cơ bản")
    void gettersSetters() {
        Request r = new Request();
        r.setAction("LOGIN");
        r.setToken("tk");
        r.setRequestId("rid");
        r.setData(Map.of("k", "v"));
        assertEquals("LOGIN", r.getAction());
        assertEquals("tk", r.getToken());
        assertEquals("rid", r.getRequestId());
        assertEquals("v", r.getData().get("k"));
    }

    @Test
    @DisplayName("getDataString trả về giá trị / null khi thiếu")
    void getDataString() {
        Request r = withData(Map.of("name", "Alice"));
        assertEquals("Alice", r.getDataString("name"));
        assertNull(r.getDataString("missing"));
    }

    @Test
    @DisplayName("getDataString khi data null - trả về null")
    void getDataString_nullData() {
        Request r = new Request();
        assertNull(r.getDataString("x"));
    }

    @Test
    @DisplayName("getDataInteger parse số và string")
    void getDataInteger() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", 42);
        data.put("b", "100");
        data.put("c", "abc");
        Request r = withData(data);
        assertEquals(42, r.getDataInteger("a"));
        assertEquals(100, r.getDataInteger("b"));
        assertNull(r.getDataInteger("c"));
        assertNull(r.getDataInteger("missing"));
    }

    @Test
    @DisplayName("getDataInt trả về 0 khi thiếu")
    void getDataInt_default() {
        Request r = withData(Map.of("a", 5));
        assertEquals(5, r.getDataInt("a"));
        assertEquals(0, r.getDataInt("missing"));
    }

    @Test
    @DisplayName("getDataDouble parse số và string")
    void getDataDouble() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", 3.14);
        data.put("b", "2.5");
        data.put("c", "xx");
        Request r = withData(data);
        assertEquals(3.14, r.getDataDouble("a"));
        assertEquals(2.5, r.getDataDouble("b"));
        assertNull(r.getDataDouble("c"));
    }

    @Test
    @DisplayName("getDataBoolean parse boolean và string")
    void getDataBoolean() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", true);
        data.put("b", "true");
        data.put("c", "false");
        Request r = withData(data);
        assertTrue(r.getDataBoolean("a"));
        assertTrue(r.getDataBoolean("b"));
        assertFalse(r.getDataBoolean("c"));
        assertFalse(r.getDataBoolean("missing"));
    }

    @Test
    @DisplayName("getDataUUID parse UUID hợp lệ / null khi sai")
    void getDataUUID() {
        UUID id = UUID.randomUUID();
        Map<String, Object> data = new HashMap<>();
        data.put("id", id.toString());
        data.put("bad", "not-a-uuid");
        Request r = withData(data);
        assertEquals(id, r.getDataUUID("id"));
        assertNull(r.getDataUUID("bad"));
        assertNull(r.getDataUUID("missing"));
    }

    @Test
    @DisplayName("getDataDecimal parse BigDecimal / null khi sai")
    void getDataDecimal() {
        Map<String, Object> data = new HashMap<>();
        data.put("amount", "1500.50");
        data.put("bad", "abc");
        Request r = withData(data);
        assertEquals(new BigDecimal("1500.50"), r.getDataDecimal("amount"));
        assertNull(r.getDataDecimal("bad"));
        assertNull(r.getDataDecimal("missing"));
    }
}
