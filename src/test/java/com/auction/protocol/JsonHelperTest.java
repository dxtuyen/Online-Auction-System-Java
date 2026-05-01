package com.auction.protocol;

import com.auction.util.JsonHelper;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests cho JsonHelper — đảm bảo lớp protocol convert Java &lt;-&gt; JSON chính xác.
 *
 * <p>Quan trọng: nếu Request/Response không serialize/deserialize đúng,
 * mọi giao tiếp Client-Server đều hỏng.</p>
 */
class JsonHelperTest {

    // ==================== REQUEST ====================

    @Test
    @DisplayName("Request → JSON: chứa đủ các field action, data, token")
    void toJson_request_correctFormat() {
        Request req = new Request("LOGIN",
                Map.of("username", "alice", "password", "123"), null);

        String json = JsonHelper.toJson(req);

        assertNotNull(json);
        assertTrue(json.contains("\"action\":\"LOGIN\""));
        assertTrue(json.contains("\"username\":\"alice\""));
        assertTrue(json.contains("\"password\":\"123\""));
    }

    @Test
    @DisplayName("JSON → Request: parse lại lấy được data đúng")
    void parseRequest_validJson_correctObject() {
        String json = "{\"action\":\"PLACE_BID\","
                    + "\"data\":{\"auctionId\":1,\"amount\":32000000},"
                    + "\"token\":\"5\"}";

        Request req = JsonHelper.parseRequest(json);

        assertEquals("PLACE_BID", req.getAction());
        assertEquals("5", req.getToken());
        assertEquals(1, req.getDataInt("auctionId"));
        assertEquals(32_000_000, req.getDataDouble("amount"));
    }

    @Test
    @DisplayName("Helper getDataString trả null nếu key không tồn tại")
    void getDataString_missingKey_returnsNull() {
        Request req = new Request("ACTION", Map.of("a", "1"), null);
        assertNull(req.getDataString("notExist"));
    }

    // ==================== RESPONSE ====================

    @Test
    @DisplayName("Response.success() → JSON có status SUCCESS")
    void success_toJson_containsStatus() {
        Response res = Response.success("LOGIN", "OK",
                Map.of("userId", 1, "role", "BIDDER"));

        assertTrue(res.isSuccess());
        assertFalse(res.isError());
        assertFalse(res.isPush());

        String json = JsonHelper.toJson(res);
        assertTrue(json.contains("\"status\":\"SUCCESS\""));
        assertTrue(json.contains("\"userId\":1"));
    }

    @Test
    @DisplayName("Response.error() → JSON có status ERROR + message")
    void error_toJson_containsStatusAndMessage() {
        Response res = Response.error("LOGIN", "Sai mật khẩu!");

        assertTrue(res.isError());
        assertFalse(res.isSuccess());

        String json = JsonHelper.toJson(res);
        assertTrue(json.contains("\"status\":\"ERROR\""));
        assertTrue(json.contains("Sai mật khẩu"));
    }

    @Test
    @DisplayName("Response.push() → JSON có status PUSH (server chủ động gửi)")
    void push_toJson_containsPushStatus() {
        Response res = Response.push("BID_UPDATE",
                Map.of("amount", 40_000_000));

        assertTrue(res.isPush());
        String json = JsonHelper.toJson(res);
        assertTrue(json.contains("\"status\":\"PUSH\""));
    }

    // ==================== ROUND-TRIP ====================

    @Test
    @DisplayName("Round-trip: Java → JSON → Java vẫn giữ nguyên data")
    void roundTrip_requestPreservesData() {
        Request original = new Request("CREATE_AUCTION",
                Map.of("itemId", 1, "durationMinutes", 60), "3");

        String json = JsonHelper.toJson(original);
        Request parsed = JsonHelper.parseRequest(json);

        assertEquals(original.getAction(), parsed.getAction());
        assertEquals(original.getToken(), parsed.getToken());
        assertEquals(original.getDataInt("itemId"), parsed.getDataInt("itemId"));
        assertEquals(original.getDataInt("durationMinutes"),
                parsed.getDataInt("durationMinutes"));
    }

    @Test
    @DisplayName("Response round-trip giữ status, message và data")
    void roundTrip_responsePreservesData() {
        Response original = Response.success("PLACE_BID", "Đặt giá thành công",
                Map.of("bidId", 5, "amount", 40_000_000));

        String json = JsonHelper.toJson(original);
        Response parsed = JsonHelper.parseResponse(json);

        assertEquals(original.getAction(), parsed.getAction());
        assertEquals(original.getStatus(), parsed.getStatus());
        assertEquals(original.getMessage(), parsed.getMessage());
        assertNotNull(parsed.getData());
    }
}
