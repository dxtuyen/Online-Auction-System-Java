package com.auction.client.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServerConnection socket I/O")
class ServerConnectionTest {

    @Test
    @DisplayName("send ghi đúng một dòng UTF-8 sang server")
    void send_writesLineToServer() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
            Thread acceptThread = new Thread(() -> {
                try (Socket socket = server.accept();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(
                             socket.getInputStream(), StandardCharsets.UTF_8))) {
                    received.offer(reader.readLine());
                } catch (Exception e) {
                    received.offer("ERROR:" + e.getMessage());
                }
            }, "test-server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            ServerConnection connection = new ServerConnection();
            try {
                connection.connect("127.0.0.1", server.getLocalPort());
                connection.send("{\"message\":\"Xin chào\"}");

                assertEquals("{\"message\":\"Xin chào\"}", received.poll(2, TimeUnit.SECONDS));
            } finally {
                connection.disconnect();
            }
        }
    }

    @Test
    @DisplayName("listener nhận dòng server push và disconnect hạ connected")
    void listener_receivesMessagesAndDisconnects() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch serverAccepted = new CountDownLatch(1);
            AtomicReference<Socket> serverSide = new AtomicReference<>();
            Thread acceptThread = new Thread(() -> {
                try {
                    Socket socket = server.accept();
                    serverSide.set(socket);
                    serverAccepted.countDown();
                } catch (Exception ignored) {
                    serverAccepted.countDown();
                }
            }, "test-server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            ServerConnection connection = new ServerConnection();
            LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
            try {
                connection.connect("127.0.0.1", server.getLocalPort());
                connection.setListener(messages::offer);
                assertTrue(serverAccepted.await(2, TimeUnit.SECONDS));
                assertTrue(connection.isConnected());

                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        serverSide.get().getOutputStream(), StandardCharsets.UTF_8), true)) {
                    writer.println("{\"action\":\"BID_UPDATE\"}");
                    assertEquals("{\"action\":\"BID_UPDATE\"}", messages.poll(2, TimeUnit.SECONDS));
                }
            } finally {
                connection.disconnect();
                Socket accepted = serverSide.get();
                if (accepted != null) accepted.close();
            }

            assertFalse(connection.isConnected());
        }
    }
}
