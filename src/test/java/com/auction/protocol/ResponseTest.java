package com.auction.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Response + protocol enums")
class ResponseTest {

    @Test
    @DisplayName("success() tạo response SUCCESS")
    void success() {
        Response r = Response.success("LOGIN", "ok", Map.of("k", "v"));
        assertTrue(r.isSuccess());
        assertFalse(r.isError());
        assertFalse(r.isPush());
        assertEquals("LOGIN", r.getAction());
        assertEquals("ok", r.getMessage());
        assertEquals(ResponseStatus.SUCCESS, r.getStatus());
        assertNotNull(r.getData());
    }

    @Test
    @DisplayName("error() không kèm data")
    void error_noData() {
        Response r = Response.error("X", "lỗi");
        assertTrue(r.isError());
        assertEquals("lỗi", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("error() kèm data")
    void error_withData() {
        Response r = Response.error("X", "lỗi", Map.of("a", 1));
        assertTrue(r.isError());
        assertNotNull(r.getData());
    }

    @Test
    @DisplayName("push() tạo response PUSH không message")
    void push_noMessage() {
        Response r = Response.push("BID_UPDATE", Map.of("a", 1));
        assertTrue(r.isPush());
        assertNull(r.getMessage());
        assertEquals(ResponseStatus.PUSH, r.getStatus());
    }

    @Test
    @DisplayName("push() có message")
    void push_withMessage() {
        Response r = Response.push("BID_UPDATE", "msg", null);
        assertTrue(r.isPush());
        assertEquals("msg", r.getMessage());
    }

    @Test
    @DisplayName("withRequestId chainable")
    void withRequestId() {
        Response r = Response.error("X", "e").withRequestId("rid-9");
        assertEquals("rid-9", r.getRequestId());
    }

    @Test
    @DisplayName("Setters hoạt động")
    void setters() {
        Response r = new Response();
        r.setAction("A");
        r.setStatus(ResponseStatus.SUCCESS);
        r.setMessage("m");
        r.setData("d");
        r.setRequestId("rid");
        assertEquals("A", r.getAction());
        assertEquals(ResponseStatus.SUCCESS, r.getStatus());
        assertEquals("m", r.getMessage());
        assertEquals("d", r.getData());
        assertEquals("rid", r.getRequestId());
    }

    // ===== ActionType =====

    @Test
    @DisplayName("ActionType.from hợp lệ và không hợp lệ")
    void actionType_from() {
        assertEquals(ActionType.LOGIN, ActionType.from("LOGIN"));
        assertEquals(ActionType.PLACE_BID, ActionType.from("PLACE_BID"));
        assertNull(ActionType.from("NOPE"));
        assertNull(ActionType.from(null));
    }

    @Test
    @DisplayName("ActionType.values đầy đủ")
    void actionType_values() {
        assertTrue(ActionType.values().length >= 18);
    }

    // ===== ResponseStatus =====

    @Test
    @DisplayName("ResponseStatus.from hợp lệ và không hợp lệ")
    void responseStatus_from() {
        assertEquals(ResponseStatus.SUCCESS, ResponseStatus.from("SUCCESS"));
        assertEquals(ResponseStatus.PUSH, ResponseStatus.from("PUSH"));
        assertNull(ResponseStatus.from("NOPE"));
        assertNull(ResponseStatus.from(null));
    }
}
