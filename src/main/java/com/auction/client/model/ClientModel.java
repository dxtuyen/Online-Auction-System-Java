package com.auction.client.model;

import com.auction.client.network.ServerConnection;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.util.JsonHelper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * ClientModel — Singleton, trung tâm trạng thái phía client.
 *
 * <ul>
 *   <li>Giữ connection tới server</li>
 *   <li>Lưu thông tin user đang login</li>
 *   <li>Route message từ server: response vào BlockingQueue, push sang handler</li>
 *   <li>Cho Controller gọi {@code sendRequest} + {@code waitForResponse} dễ dàng</li>
 * </ul>
 */
public class ClientModel {

    private static ClientModel instance;

    public static synchronized ClientModel getInstance() {
        if (instance == null) instance = new ClientModel();
        return instance;
    }

    private ServerConnection connection;

    // User state sau khi login
    private String userId;
    private String username;
    private String role;

    // Response queue — Controller gửi request rồi chờ response ở đây
    // Key = action name, Value = queue chứa response matching
    private final Map<String, BlockingQueue<Response>> responseQueues = new ConcurrentHashMap<>();

    // Push handlers — Controller đăng ký lắng nghe push notification
    // Key = push action, Value = list handler nhận data
    private final Map<String, List<Consumer<Map<String, Object>>>> pushHandlers = new ConcurrentHashMap<>();

    private ClientModel() {}

    // ============= CONNECTION =============

    public void connect(String host, int port) throws IOException {
        if (connection != null && connection.isConnected()) return;
        connection = new ServerConnection();
        connection.connect(host, port);
        connection.setListener(this::handleServerMessage);
    }

    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    public void disconnect() {
        if (connection != null) connection.disconnect();
        userId = null;
        username = null;
        role = null;
    }

    // ============= GỬI REQUEST =============

    public void sendRequest(String action, Map<String, Object> data) {
        Request req = new Request(action, data, userId);
        connection.send(JsonHelper.toJson(req));
    }

    /**
     * Blocking — gọi trên thread riêng, KHÔNG gọi trên JavaFX thread.
     * Trả về Response hoặc null nếu timeout.
     */
    public Response waitForResponse(String action, long timeoutMs) {
        BlockingQueue<Response> queue = new LinkedBlockingQueue<>();
        responseQueues.put(action, queue);
        try {
            return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            responseQueues.remove(action);
        }
    }

    // ============= NHẬN MESSAGE =============

    /** Callback từ ServerConnection — mỗi dòng JSON từ server sẽ gọi hàm này. */
    private void handleServerMessage(String json) {
        try {
            Response res = JsonHelper.parseResponse(json);
            String action = res.getAction();

            if (res.isPush()) {
                dispatchPush(action, res);
            } else {
                // SUCCESS hoặc ERROR → đưa vào queue để waitForResponse() unblock
                BlockingQueue<Response> queue = responseQueues.get(action);
                if (queue != null) queue.offer(res);
            }
        } catch (Exception e) {
            System.err.println("[ClientModel] Lỗi parse: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchPush(String action, Response res) {
        List<Consumer<Map<String, Object>>> handlers = pushHandlers.get(action);
        if (handlers != null) {
            Map<String, Object> data = res.getData() instanceof Map
                    ? (Map<String, Object>) res.getData()
                    : Map.of();
            for (Consumer<Map<String, Object>> h : handlers) {
                try { h.accept(data); }
                catch (Exception e) { System.err.println("[PushHandler] " + e.getMessage()); }
            }
        }
    }

    // ============= PUSH HANDLERS =============

    public void addPushHandler(String action, Consumer<Map<String, Object>> handler) {
        pushHandlers.computeIfAbsent(action, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Xóa tất cả handler của các push action bidding (gọi khi rời màn Bidding). */
    public void clearBiddingPushHandlers() {
        pushHandlers.remove("BID_UPDATE");
        pushHandlers.remove("AUCTION_STATUS");
        pushHandlers.remove("AUCTION_EXTENDED");
    }

    // ============= GETTERS/SETTERS =============

    public String getUserId()    { return userId; }
    public String getUsername()  { return username; }
    public String getRole()      { return role; }
    public void setUserId(String userId)     { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setRole(String role)         { this.role = role; }
}
