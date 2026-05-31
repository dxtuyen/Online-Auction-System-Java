package com.auction.testsupport;

import com.auction.persistence.Database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hỗ trợ test trên H2 in-memory (MySQL mode).
 *
 * <p>Schema được tạo bằng DDL tương thích H2 (subset của schema.sql production).
 * {@link #ensureSchema()} idempotent — chỉ chạy 1 lần cho cả JVM. {@link #clean()}
 * xóa sạch dữ liệu giữa các test theo thứ tự an toàn FK.</p>
 *
 * <p>Kết nối lấy qua {@link Database} — vốn đã được trỏ vào H2 thông qua biến
 * môi trường DB_URL/DB_USER/DB_PASSWORD cấu hình trong maven-surefire-plugin.</p>
 */
public final class TestDb {

    private static final AtomicBoolean SCHEMA_READY = new AtomicBoolean(false);

    private TestDb() { }

    private static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS users (
                id              CHAR(36)      NOT NULL PRIMARY KEY,
                created_at      TIMESTAMP(3)  NOT NULL,
                updated_at      TIMESTAMP(3)  NOT NULL,
                username        VARCHAR(50)   NOT NULL UNIQUE,
                hashed_password VARCHAR(255)  NOT NULL,
                password_salt   VARCHAR(255)  NOT NULL,
                email           VARCHAR(254)  NOT NULL UNIQUE,
                full_name       VARCHAR(100)  NOT NULL,
                user_status     VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
                role            VARCHAR(10)    NOT NULL DEFAULT 'NORMAL',
                balance         DECIMAL(19,4) NOT NULL DEFAULT 0,
                revenue         DECIMAL(19,4) NOT NULL DEFAULT 0,
                avatar_url      VARCHAR(500)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS items (
                id              CHAR(36)      NOT NULL PRIMARY KEY,
                created_at      TIMESTAMP(3)  NOT NULL,
                updated_at      TIMESTAMP(3)  NOT NULL,
                item_type       VARCHAR(20)   NOT NULL,
                name            VARCHAR(200)  NOT NULL,
                description     CLOB,
                seller_id       CHAR(36)      NOT NULL,
                starting_price  DECIMAL(19,4) NOT NULL,
                category        VARCHAR(50)   NOT NULL,
                item_condition  VARCHAR(50)   NOT NULL,
                brand           VARCHAR(100),
                model           VARCHAR(100),
                warranty_months INT,
                artist          VARCHAR(100),
                year_created    INT,
                medium          VARCHAR(100),
                make            VARCHAR(100),
                vehicle_model   VARCHAR(100),
                vehicle_year    INT,
                mileage_km      INT,
                extra_info      CLOB,
                CONSTRAINT fk_items_seller FOREIGN KEY (seller_id) REFERENCES users(id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS item_images (
                item_id   CHAR(36)     NOT NULL,
                image_url VARCHAR(500) NOT NULL,
                position  INT          NOT NULL DEFAULT 0,
                PRIMARY KEY (item_id, position),
                CONSTRAINT fk_images_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS auctions (
                id                CHAR(36)      NOT NULL PRIMARY KEY,
                created_at        TIMESTAMP(3)  NOT NULL,
                updated_at        TIMESTAMP(3)  NOT NULL,
                item_id           CHAR(36)      NOT NULL UNIQUE,
                seller_id         CHAR(36)      NOT NULL,
                start_time        TIMESTAMP(3)  NOT NULL,
                end_time          TIMESTAMP(3)  NOT NULL,
                starting_price    DECIMAL(19,4) NOT NULL,
                current_price     DECIMAL(19,4) NOT NULL,
                minimum_increment DECIMAL(19,4) NOT NULL,
                highest_bidder_id CHAR(36),
                status            VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                total_bids        INT           NOT NULL DEFAULT 0,
                CONSTRAINT fk_auction_item   FOREIGN KEY (item_id)           REFERENCES items(id),
                CONSTRAINT fk_auction_seller FOREIGN KEY (seller_id)         REFERENCES users(id),
                CONSTRAINT fk_auction_winner FOREIGN KEY (highest_bidder_id) REFERENCES users(id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS bid_transactions (
                id         CHAR(36)      NOT NULL PRIMARY KEY,
                created_at TIMESTAMP(3)  NOT NULL,
                updated_at TIMESTAMP(3)  NOT NULL,
                auction_id CHAR(36)      NOT NULL,
                bidder_id  CHAR(36)      NOT NULL,
                bid_amount DECIMAL(19,4) NOT NULL,
                status     VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                CONSTRAINT fk_bid_auction FOREIGN KEY (auction_id) REFERENCES auctions(id),
                CONSTRAINT fk_bid_bidder  FOREIGN KEY (bidder_id)  REFERENCES users(id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS auto_bids (
                bidder_id  CHAR(36)      NOT NULL,
                auction_id CHAR(36)      NOT NULL,
                max_bid    DECIMAL(19,4) NOT NULL,
                increment  DECIMAL(19,4) NOT NULL,
                created_at TIMESTAMP(3)  NOT NULL,
                active     TINYINT       NOT NULL DEFAULT 1,
                PRIMARY KEY (bidder_id, auction_id),
                CONSTRAINT fk_autobid_bidder  FOREIGN KEY (bidder_id)  REFERENCES users(id),
                CONSTRAINT fk_autobid_auction FOREIGN KEY (auction_id) REFERENCES auctions(id)
            )
            """
    };

    /** Tạo schema 1 lần duy nhất cho cả JVM (H2 in-memory giữ qua DB_CLOSE_DELAY=-1). */
    public static void ensureSchema() {
        if (!SCHEMA_READY.compareAndSet(false, true)) return;
        try (Connection c = Database.getInstance().getConnection();
             Statement st = c.createStatement()) {
            for (String ddl : DDL) {
                st.execute(ddl);
            }
        } catch (SQLException e) {
            SCHEMA_READY.set(false);
            throw new RuntimeException("Không tạo được schema H2 cho test: " + e.getMessage(), e);
        }
    }

    /** Xóa sạch dữ liệu mọi bảng theo thứ tự an toàn FK. */
    public static void clean() {
        ensureSchema();
        try (Connection c = Database.getInstance().getConnection();
             Statement st = c.createStatement()) {
            st.execute("DELETE FROM auto_bids");
            st.execute("DELETE FROM bid_transactions");
            st.execute("DELETE FROM auctions");
            st.execute("DELETE FROM item_images");
            st.execute("DELETE FROM items");
            st.execute("DELETE FROM users");
        } catch (SQLException e) {
            throw new RuntimeException("Không clean được DB test: " + e.getMessage(), e);
        }
    }
}
