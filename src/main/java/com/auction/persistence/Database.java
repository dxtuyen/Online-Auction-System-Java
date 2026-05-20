package com.auction.persistence;

import com.auction.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Singleton quản lý JDBC DataSource - mọi DAO lấy connection qua đây.
 *
 * <p>Dùng <b>HikariCP</b> làm connection pool — mọi DAO {@code getConnection()}
 * lấy connection có sẵn trong pool thay vì mở TCP+handshake mỗi lần. Quan trọng
 * khi có nhiều bid đồng thời + proxy-bid loop (xem BidManager): mỗi placeBid
 * mở 4-5 connection, không pool sẽ chậm và dễ exhaust MySQL.</p>
 *
 * <p>Cấu hình qua {@link AppConfig} (.env hoặc biến môi trường):
 * <ul>
 *   <li>{@code DB_POOL_SIZE} — max connection trong pool (default 10)</li>
 *   <li>{@code DB_POOL_MIN_IDLE} — min idle (default 2)</li>
 *   <li>{@code DB_POOL_TIMEOUT_MS} — timeout chờ connection (default 30000)</li>
 * </ul>
 * </p>
 *
 * <p>Connection vẫn được mở/đóng theo từng operation qua try-with-resources ở DAO;
 * {@code close()} trên HikariCP-connection thực ra là <i>return-to-pool</i>, không
 * close TCP — nên không cần đổi gì ở DAO.</p>
 */
public final class Database {

    private static final class Holder {
        private static final Database INSTANCE = new Database();
    }

    public static Database getInstance() {
        return Holder.INSTANCE;
    }

    private final HikariDataSource dataSource;

    private Database() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.require("DB_URL"));
        cfg.setUsername(AppConfig.require("DB_USER"));
        cfg.setPassword(AppConfig.require("DB_PASSWORD"));
        cfg.setMaximumPoolSize(AppConfig.getInt("DB_POOL_SIZE", 10));
        cfg.setMinimumIdle(AppConfig.getInt("DB_POOL_MIN_IDLE", 2));
        cfg.setConnectionTimeout(AppConfig.getInt("DB_POOL_TIMEOUT_MS", 30000));
        cfg.setPoolName("auction-db-pool");
        // Tránh leak detection chạy nóng — bật ở 30s, cảnh báo nếu DAO quên close.
        cfg.setLeakDetectionThreshold(30_000);
        this.dataSource = new HikariDataSource(cfg);
    }

    /** Cho test/utility cần unwrap DataSource interface. */
    public DataSource getDataSource() {
        return dataSource;
    }

    /** Lấy connection từ pool - caller phải close (try-with-resources) để return pool. */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Sanity check - gọi lúc startup để fail-fast nếu DB không kết nối được. */
    public void verifyConnection() {
        try (Connection c = getConnection()) {
            if (!c.isValid(3)) {
                throw new IllegalStateException("DB connection không hợp lệ");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Không kết nối được DB. Kiểm tra docker compose up + .env: "
                            + e.getMessage(), e);
        }
    }

    /**
     * Đóng pool — gọi ở shutdown hook để giải phóng socket TCP gracefully thay vì
     * để Hikari bỏ đói khi JVM exit (tránh kết nối "treo" ở MySQL).
     */
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
