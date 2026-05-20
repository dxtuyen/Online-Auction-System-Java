package com.auction.server;

import com.auction.bootstrap.DataSeeder;
import com.auction.config.AppConfig;
import com.auction.persistence.Database;
import com.auction.service.AuctionManager;
import com.auction.service.BidManager;
import com.auction.service.ItemManager;
import com.auction.service.UserManager;
import com.auction.util.AppLogger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point của Server.
 *
 * <p>Mỗi client kết nối được handover cho một thread trong pool có giới hạn — tránh
 * trường hợp 1000 client = 1000 thread sống lay lắt làm cạn tài nguyên server.</p>
 *
 * <p>Cấu hình lấy từ {@link AppConfig}:
 * <ul>
 *   <li>{@code SERVER_PORT} (default 8888)</li>
 *   <li>{@code SERVER_MAX_THREADS} (default 100)</li>
 * </ul>
 */
public final class ServerMain {

    private static final Logger log = AppLogger.get(ServerMain.class);

    private ServerMain() { /* static-only */ }

    public static void main(String[] args) {
        int port = AppConfig.getInt("SERVER_PORT", 8888);
        int maxThreads = AppConfig.getInt("SERVER_MAX_THREADS", 100);

        // 1. Fail-fast nếu DB không kết nối được — không cho service start nửa vời.
        Database.getInstance().verifyConnection();

        // 2. Load cache theo thứ tự dependency: User → Item → Auction → Bid/AutoBid.
        UserManager.getInstance().loadAllFromDb();
        ItemManager.getInstance().loadAllFromDb();
        AuctionManager.getInstance().loadAllFromDb();
        BidManager.getInstance().loadAllFromDb();

        // 3. Seed (chỉ khi DB rỗng).
        DataSeeder.run();

        ExecutorService workers = Executors.newFixedThreadPool(maxThreads, namedFactory("client-"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(workers), "shutdown-hook"));

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info(() -> "Server lắng nghe trên port " + port + " (max " + maxThreads + " clients)");

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                log.info(() -> "Client kết nối: " + clientSocket.getRemoteSocketAddress());
                try {
                    workers.submit(new ClientHandler(clientSocket));
                } catch (RuntimeException e) {
                    // Pool reject (đã shutdown hoặc full) — đóng socket để không leak.
                    log.warning("Từ chối client " + clientSocket.getRemoteSocketAddress()
                            + ": " + e.getMessage());
                    try { clientSocket.close(); } catch (IOException ignored) { }
                }
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Server lỗi khởi động", e);
        }
    }

    private static void shutdown(ExecutorService workers) {
        log.info("Đang shutdown server...");
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        AuctionManager.getInstance().shutdown();
        // Đóng pool sau cùng — để các tick lifecycle / observer kịp flush DB trước.
        Database.getInstance().close();
        log.info("Server đã dừng.");
    }

    /** ThreadFactory đặt tên rõ ràng để dễ trace trong thread dump. */
    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + seq.incrementAndGet());
            t.setDaemon(false);
            return t;
        };
    }
}
