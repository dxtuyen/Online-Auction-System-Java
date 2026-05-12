package com.auction.server;

import com.auction.bootstrap.DataSeeder;
import com.auction.service.AuctionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entry point của Server.
 *
 * <p>SAU REFACTOR:
 * <ul>
 *   <li><b>Thread pool</b> thay cho thread-per-connection: dùng cached {@link ExecutorService}.
 *       Lý do: mỗi client = 1 OS thread không scale (100 user = 100 thread, tốn RAM/scheduling).
 *       Cached pool tái sử dụng thread idle, đủ cho BTL nhiều người dùng cùng lúc.</li>
 *   <li><b>Graceful shutdown</b>: {@code Runtime.addShutdownHook} đảm bảo khi server tắt
 *       (Ctrl+C, kill SIGTERM) sẽ đóng socket, shutdown thread pool, và shutdown
 *       {@link AuctionManager} scheduler. Tránh "thread daemon còn sống nhưng resource bị treo".</li>
 * </ul>
 *
 * <p>Server chính chỉ làm 1 việc: accept connection → submit cho pool. Việc đọc/xử lý
 * request nằm hoàn toàn trong {@link ClientHandler}.</p>
 */
public class ServerMain {

    public static final int PORT = 8888;

    private static final AtomicLong THREAD_SEQ = new AtomicLong();

    /**
     * Cached pool: tự co giãn theo tải, thread idle 60s sẽ bị recycle.
     * Phù hợp cho mô hình "nhiều connection, mỗi connection sống lâu nhưng phần lớn thời gian
     * block ở readLine()" — đặc trưng của TCP socket server.
     *
     * <p>Đặt tên thread rõ để dễ debug khi xem stack/jstack.</p>
     */
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "client-handler-" + THREAD_SEQ.incrementAndGet());
        t.setDaemon(false);
        return t;
    });

    public static void main(String[] args) {
        DataSeeder.run();

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            System.err.println("[Server] Không mở được port " + PORT + ": " + e.getMessage());
            return;
        }

        // Đăng ký shutdown hook TRƯỚC khi vào loop accept — để mọi đường thoát đều dọn dẹp được.
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> shutdown(serverSocket), "server-shutdown"));

        System.out.println("[Server] Đang lắng nghe trên port " + PORT);
        System.out.println("[Server] Đợi client kết nối... (Ctrl+C để thoát)");

        try {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Client kết nối: "
                        + clientSocket.getRemoteSocketAddress());
                // Submit thay cho new Thread — pool sẽ tự cấp thread.
                POOL.submit(new ClientHandler(clientSocket));
            }
        } catch (SocketException e) {
            // serverSocket bị đóng bởi shutdown hook → đây là exit path bình thường.
            System.out.println("[Server] Đã ngừng nhận kết nối mới.");
        } catch (IOException e) {
            System.err.println("[Server] Lỗi loop accept: " + e.getMessage());
        }
    }

    /**
     * Dọn dẹp khi server tắt:
     * 1. Đóng ServerSocket → loop accept() ở main thoát.
     * 2. Shutdown pool ClientHandler → đợi tối đa 5s.
     * 3. Shutdown AuctionManager scheduler → dừng lifecycle tick.
     */
    private static void shutdown(ServerSocket serverSocket) {
        System.out.println("[Server] Đang shutdown...");
        try { serverSocket.close(); } catch (IOException ignored) {}

        POOL.shutdown();
        try {
            if (!POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }

        AuctionManager.getInstance().shutdown();
        System.out.println("[Server] Đã shutdown sạch.");
    }
}
