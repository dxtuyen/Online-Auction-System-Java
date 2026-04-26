package com.auction.client.network;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Quản lý socket tới Server.
 *
 * <p>Mở thread riêng để lắng nghe tin nhắn từ server liên tục.
 * Mỗi dòng JSON nhận được sẽ đẩy cho {@link MessageListener}.</p>
 */
public class ServerConnection {

    public interface MessageListener {
        void onMessage(String json);
    }

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected;
    private MessageListener listener;
    private Thread listenerThread;

    /** Mở kết nối + tạo listener thread. */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        // UTF-8 bắt buộc để handle tiếng Việt
        reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8), true);
        connected = true;

        // Thread riêng để không block main thread khi đọc từ socket
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    if (listener != null) listener.onMessage(line);
                }
            } catch (IOException e) {
                if (connected) System.err.println("[Conn] Mất kết nối: " + e.getMessage());
            } finally {
                connected = false;
            }
        }, "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Gửi JSON lên server. synchronized tránh 2 thread ghi cùng lúc
     * gây lỗi interleaving.
     */
    public synchronized void send(String json) {
        if (writer != null && connected) writer.println(json);
    }

    public void setListener(MessageListener listener) { this.listener = listener; }
    public boolean isConnected() { return connected; }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) {}
    }
}
