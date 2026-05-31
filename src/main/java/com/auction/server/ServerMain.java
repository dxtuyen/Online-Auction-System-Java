package com.auction.server;

import com.auction.bootstrap.DataSeeder;
import com.auction.config.AppConfig;
import com.auction.persistence.Database;
import com.auction.server.observer.AuctionEventManager;
import com.auction.service.AuctionManager;
import com.auction.service.BidManager;
import com.auction.service.ImageStorageService;
import com.auction.service.ItemManager;
import com.auction.service.UserManager;
import com.auction.util.AppLogger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServerMain {

    private static final Logger log = AppLogger.get(ServerMain.class);

    private ServerMain() {}

    public static void main(String[] args) {
        int port = AppConfig.getInt("SERVER_PORT", 8888);
        int maxThreads = AppConfig.getInt("SERVER_MAX_THREADS", 100);

        Database.getInstance().verifyConnection();
        runMigrations();

        ImageStorageService.getInstance().init();

        UserManager.getInstance().loadAllFromDb();
        ItemManager.getInstance().loadAllFromDb();
        AuctionManager.getInstance().loadAllFromDb();
        BidManager.getInstance().loadAllFromDb();

        BidManager.getInstance().getParticipantsSnapshot().forEach(
                (auctionId, bidders) -> AuctionEventManager.getInstance()
                        .registerParticipants(auctionId, bidders));

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
                    log.warning("Từ chối client " + clientSocket.getRemoteSocketAddress()
                            + ": " + e.getMessage());
                    try {
                        clientSocket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Server lỗi khởi động", e);
        }
    }

    private static void runMigrations() {
        try (Connection c = Database.getInstance().getConnection();
             Statement st = c.createStatement()) {
            addColumnIfMissing(c, st, "users", "avatar_url", "VARCHAR(500) NULL");
        } catch (SQLException e) {
            throw new IllegalStateException("Migration thất bại: " + e.getMessage(), e);
        }
    }

    private static void addColumnIfMissing(Connection c, Statement st,
                                           String table,
                                           String column,
                                           String columnDef) throws SQLException {
        String check = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() "
                + "AND table_name = '" + table + "' AND column_name = '" + column + "'";

        try (var rs = st.executeQuery(check)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDef);
        log.info(() -> "Migration: thêm cột " + table + "." + column);
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
        Database.getInstance().close();

        log.info("Server đã dừng.");
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger();

        return r -> {
            Thread t = new Thread(r, prefix + seq.incrementAndGet());
            t.setDaemon(false);
            return t;
        };
    }
}