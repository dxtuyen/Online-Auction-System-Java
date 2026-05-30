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

public class ClientModel {

    private static final Logger log = AppLogger.get(ClientModel.class);

    private static ClientModel instance;

    public static synchronized ClientModel getInstance() {
        if (instance == null) instance = new ClientModel();
        return instance;
    }

    private ServerConnection connection;

    private String userId;
    private String username;
    private String role;

    private final Map<String, BlockingQueue<Response>> pendingByAction = new ConcurrentHashMap<>();

    private final Map<String, List<Consumer<Map<String, Object>>>> pushHandlers = new ConcurrentHashMap<>();

    private ClientModel() {}

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

    public void sendRequest(String action, Map<String, Object> data) {

        pendingByAction.computeIfAbsent(action, k -> new LinkedBlockingQueue<>());

        Request req = new Request(action, data, userId);
        connection.send(JsonHelper.toJson(req));
    }

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

    private void handleServerMessage(String json) {
        try {
            Response res = JsonHelper.parseResponse(json);
            if (res == null || res.getAction() == null) return;

            if (res.isPush()) {
                dispatchPush(res.getAction(), res);
            } else {

                BlockingQueue<Response> q = pendingByAction.get(res.getAction());
                if (q != null) {
                    q.offer(res);
                }

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

    public void addPushHandler(String action, Consumer<Map<String, Object>> handler) {
        pushHandlers.computeIfAbsent(action, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void clearBiddingPushHandlers() {
        pushHandlers.remove("BID_UPDATE");
        pushHandlers.remove("AUCTION_STATUS");
        pushHandlers.remove("AUCTION_EXTENDED");
    }

    public String getUserId()    { return userId; }
    public String getUsername()  { return username; }
    public String getRole()      { return role; }
    public void setUserId(String userId)     { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setRole(String role)         { this.role = role; }
}
