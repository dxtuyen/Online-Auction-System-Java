package com.auction.client.model;

import com.auction.client.network.ServerConnection;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.util.AppLogger;
import com.auction.util.JsonHelper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * ClientModel — Singleton, trung tâm trạng thái phía client.
 *
 * <ul>
 *   <li>Giữ connection tới server.</li>
 *   <li>Lưu thông tin user đang login.</li>
 *   <li>Route message từ server: response (SUCCESS/ERROR) đẩy vào hàng đợi theo
 *       {@code action} để controller poll; push notification gọi handler đã đăng ký.</li>
 * </ul>
 *
 * <p><b>Routing theo action thay vì requestId:</b> tầng controller hiện gửi-rồi-chờ
 * tuần tự cho mỗi action (xem các Controller {@code waitForResponse(action, ms)}).
 * Vì 1 màn hình hiếm khi gửi 2 request cùng action đồng thời, model dùng 1 queue cho
 * mỗi action. Mỗi {@code sendRequest} đảm bảo queue tồn tại; mỗi response đến sẽ
 * được offer vào queue đúng action; controller poll bằng {@code waitForResponse}.</p>
 *
 * <p>Thread-safety: dùng {@link ConcurrentHashMap} + {@link LinkedBlockingQueue} nên
 * nhiều màn hình/thread có thể gửi-nhận song song không conflict.</p>
 */
public class ClientModel {

    private static final Logger log = AppLogger.get(ClientModel.class);

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

    /** Queue chờ response cho mỗi action. */
    private final Map<String, BlockingQueue<Response>> pendingByAction = new ConcurrentHashMap<>();

    /** Push handler — Controller đăng ký lắng nghe push (BID_UPDATE, AUCTION_STATUS, ...). */
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
        pendingByAction.clear();
        pushHandlers.clear();
        userId = null;
        username = null;
        role = null;
    }

    // ============= GỬI REQUEST =============

    /**
     * Gửi request không block. Sau đó controller gọi {@link #waitForResponse(String, long)}
     * để lấy response tương ứng.
     */
    public void sendRequest(String action, Map<String, Object> data) {
        // Tạo queue trước khi gửi, tránh race: response có thể về sớm hơn lần
        // poll đầu tiên của controller.
        pendingByAction.computeIfAbsent(action, k -> new LinkedBlockingQueue<>());

        Request req = new Request(action, data, userId);
        connection.send(JsonHelper.toJson(req));
    }

    /**
     * Đợi response cho 1 action (SUCCESS hoặc ERROR). Block thread hiện tại
     * tối đa {@code timeoutMs} ms.
     *
     * <p><b>KHÔNG gọi trên JavaFX Application Thread</b> — sẽ làm đứng UI.
     * Controller đang chuyển sang thread riêng (Platform.runLater để update UI sau).</p>
     *
     * @return Response, hoặc null nếu timeout.
     */
    public Response waitForResponse(String action, long timeoutMs) {
        BlockingQueue<Response> q = pendingByAction.computeIfAbsent(
                action, k -> new LinkedBlockingQueue<>());
        try {
            return q.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ============= NHẬN MESSAGE =============

    /** Callback từ ServerConnection — mỗi dòng JSON từ server gọi hàm này. */
    private void handleServerMessage(String json) {
        try {
            Response res = JsonHelper.parseResponse(json);
            if (res == null || res.getAction() == null) return;

            if (res.isPush()) {
                dispatchPush(res.getAction(), res);
            } else {
                // SUCCESS hoặc ERROR — đẩy vào queue của action tương ứng.
                BlockingQueue<Response> q = pendingByAction.get(res.getAction());
                if (q != null) {
                    q.offer(res);
                }
                // Nếu không có queue thì response đến lạc — bỏ (controller đã unmount hoặc
                // timeout từ trước). Không log để tránh spam.
            }
        } catch (Exception e) {
            log.warning(() -> "Lỗi parse: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchPush(String action, Response res) {
        List<Consumer<Map<String, Object>>> handlers = pushHandlers.get(action);
        if (handlers == null || handlers.isEmpty()) return;
        Map<String, Object> data = res.getData() instanceof Map
                ? (Map<String, Object>) res.getData()
                : Map.of();
        for (Consumer<Map<String, Object>> h : handlers) {
            try { h.accept(data); }
            catch (Exception e) { log.warning(() -> "PushHandler: " + e.getMessage()); }
        }
    }

    // ============= PUSH HANDLERS =============

    public void addPushHandler(String action, Consumer<Map<String, Object>> handler) {
        pushHandlers.computeIfAbsent(action, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Xóa tất cả handler các push action bidding (gọi khi rời màn Bidding). */
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
