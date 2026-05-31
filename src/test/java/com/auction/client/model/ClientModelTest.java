package com.auction.client.model;

import com.auction.client.network.ServerConnection;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.util.JsonHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientModel message routing")
class ClientModelTest {

    private ClientModel model;
    private FakeServerConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        model = ClientModel.getInstance();
        model.disconnect();
        connection = new FakeServerConnection();
        setField("connection", connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        model.disconnect();
        setField("connection", null);
    }

    @Test
    @DisplayName("sendRequest tạo JSON request kèm userId hiện tại")
    void sendRequest_writesRequestWithCurrentUser() {
        model.setUserId("user-1");

        model.sendRequest("PING", Map.of("value", "42"));

        assertEquals(1, connection.sent.size());
        Request sent = JsonHelper.parseRequest(connection.sent.get(0));
        assertEquals("PING", sent.getAction());
        assertEquals("user-1", sent.getToken());
        assertEquals("42", sent.getDataString("value"));
    }

    @Test
    @DisplayName("response SUCCESS/ERROR được route vào queue theo action")
    void handleServerMessage_routesResponseByAction() throws Exception {
        model.sendRequest("LOGIN", Map.of("username", "alice"));

        deliver(Response.success("LOGIN", "ok", Map.of("userId", "u1")));

        Response response = model.waitForResponse("LOGIN", 50);
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("ok", response.getMessage());
    }

    @Test
    @DisplayName("response không có queue chờ thì bị bỏ qua")
    void handleServerMessage_ignoresOrphanResponse() throws Exception {
        deliver(Response.success("NO_WAITING_CONTROLLER", "late", null));

        assertNull(model.waitForResponse("NO_WAITING_CONTROLLER", 20));
    }

    @Test
    @DisplayName("PUSH gọi handler đã đăng ký và clearBiddingPushHandlers gỡ đúng nhóm")
    void pushHandlers_dispatchAndClear() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Map<String, Object>> lastData = new AtomicReference<>();
        model.addPushHandler("BID_UPDATE", data -> {
            calls.incrementAndGet();
            lastData.set(data);
        });

        deliver(Response.push("BID_UPDATE", Map.of("amount", 1000)));

        assertEquals(1, calls.get());
        assertEquals("1000", String.valueOf(lastData.get().get("amount")));

        model.clearBiddingPushHandlers();
        deliver(Response.push("BID_UPDATE", Map.of("amount", 1100)));

        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("logoutAndDisconnect gửi LOGOUT nếu còn kết nối rồi cleanup local")
    void logoutAndDisconnect_sendsLogoutAndClearsState() {
        model.setUserId("user-1");
        model.setUsername("alice");
        model.setRole("NORMAL");
        connection.onSend = json -> {
            Request request = JsonHelper.parseRequest(json);
            if ("LOGOUT".equals(request.getAction())) {
                try {
                    deliver(Response.success("LOGOUT", "bye", null));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        model.logoutAndDisconnect();

        assertFalse(model.isConnected());
        assertNull(model.getUserId());
        assertNull(model.getUsername());
        assertNull(model.getRole());
        assertEquals(1, connection.disconnectCount);
        assertEquals("LOGOUT", JsonHelper.parseRequest(connection.sent.get(0)).getAction());
    }

    private void deliver(Response response) throws Exception {
        Method method = ClientModel.class.getDeclaredMethod("handleServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(model, JsonHelper.toJson(response));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = ClientModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(model, value);
    }

    private static final class FakeServerConnection extends ServerConnection {
        private final CopyOnWriteArrayList<String> sent = new CopyOnWriteArrayList<>();
        private volatile boolean connected = true;
        private int disconnectCount;
        private Consumer<String> onSend = json -> { };

        @Override
        public synchronized void send(String json) {
            sent.add(json);
            onSend.accept(json);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void disconnect() {
            connected = false;
            disconnectCount++;
        }
    }
}
