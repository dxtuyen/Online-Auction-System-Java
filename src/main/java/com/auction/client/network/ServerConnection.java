package com.auction.client.network;

import com.auction.util.AppLogger;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class ServerConnection {

    private static final Logger log = AppLogger.get(ServerConnection.class);

    public interface MessageListener {
        void onMessage(String json);
    }

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected;
    private MessageListener listener;
    private Thread listenerThread;

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);

        reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8), true);
        connected = true;

        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    if (listener != null) listener.onMessage(line);
                }
            } catch (IOException e) {
                if (connected) log.warning(() -> "Mất kết nối: " + e.getMessage());
            } finally {
                connected = false;
            }
        }, "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public synchronized void send(String json) {
        if (writer != null && connected) writer.println(json);
    }

    public void setListener(MessageListener listener) { this.listener = listener; }
    public boolean isConnected() { return connected; }

    public void disconnect() {
        connected = false;

        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        try { if (writer != null) writer.close(); } catch (RuntimeException ignored) {}
        if (listenerThread != null) listenerThread.interrupt();
    }
}
