package com.auction.util;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL      = "jdbc:mysql://localhost:3306/auction_db";
    private static final String USER     = "root";
    private static final String PASSWORD = "123456";

    private static Connection connection = null;

    static {
        Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
        }
        return connection;
    }
}
