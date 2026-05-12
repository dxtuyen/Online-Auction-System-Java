package com.auction.persistence;

import com.auction.config.AppConfig;
import com.mysql.cj.jdbc.MysqlDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Singleton quản lý JDBC DataSource - mọi DAO lấy connection qua đây.
 *
 * <p>Dùng MysqlDataSource thay vì DriverManager để có thể dễ dàng nâng cấp
 * lên connection pool (HikariCP/c3p0) sau này mà không phải đổi DAO.</p>
 *
 * <p>Connection được mở/đóng theo từng operation (try-with-resources ở DAO),
 * không giữ connection lâu.</p>
 */
public final class Database {

    private static final class Holder {
        private static final Database INSTANCE = new Database();
    }

    public static Database getInstance() {
        return Holder.INSTANCE;
    }

    private final DataSource dataSource;

    private Database() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(AppConfig.require("DB_URL"));
        ds.setUser(AppConfig.require("DB_USER"));
        ds.setPassword(AppConfig.require("DB_PASSWORD"));
        this.dataSource = ds;
    }

    /** Mở connection mới - caller phải close (try-with-resources). */
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
}
