package com.auction.server.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    // Thông số kết nối (Thay đổi theo DB của bạn)
    private static final String URL = "jdbc:mysql://localhost:3306/auction_db";
    private static final String USER = "root";
    private static final String PASSWORD = "your_password";

    private static Connection connection = null;

    // Hàm lấy kết nối (getConnection) mà bạn đang gọi ở UserDAOImpl
    public static Connection getConnection() throws SQLException {
        // Kiểm tra nếu connection chưa tồn tại hoặc đã đóng thì tạo mới
        if (connection == null || connection.isClosed()) {
            try {
                // Đăng ký Driver (Tùy thuộc bạn dùng MySQL, PostgreSQL hay SQLite)
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
            } catch (ClassNotFoundException e) {
                System.err.println("Không tìm thấy Driver JDBC!");
                e.printStackTrace();
            }
        }
        return connection;
    }
}
