package com.auction.persistence;

import com.auction.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

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

        cfg.setLeakDetectionThreshold(30_000);
        this.dataSource = new HikariDataSource(cfg);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

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

    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
