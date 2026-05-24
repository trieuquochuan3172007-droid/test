package com.auction.server.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseUtil {
    private static DatabaseUtil instance;
    private Connection connection;
    private String url;
    private String username;
    private String password;
    private static final int MAX_RETRIES = 3;

    private DatabaseUtil() {
        try {
            Properties props = new Properties();
            InputStream in = getClass().getClassLoader().getResourceAsStream("config/application.properties");
            if (in != null) {
                props.load(in);
                this.url = props.getProperty("db.url");
                this.username = props.getProperty("db.username");
                this.password = props.getProperty("db.password");
            } else {
                throw new RuntimeException("Cannot find config/application.properties");
            }
            
            // Load driver explicitly
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize DatabaseUtil", e);
        }
    }

    public static synchronized DatabaseUtil getInstance() {
        if (instance == null) {
            instance = new DatabaseUtil();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        // Kiểm tra connection có còn sống không
        if (connection == null || connection.isClosed()) {
            // Tạo connection mới với retry logic
            for (int i = 0; i < MAX_RETRIES; i++) {
                try {
                    connection = DriverManager.getConnection(url, username, password);
                    System.out.println("[DATABASE] ✓ Kết nối thành công");
                    return connection;
                } catch (SQLException e) {
                    System.err.println("[DATABASE] ⚠ Lần thử " + (i + 1) + "/" + MAX_RETRIES + " - " + e.getMessage());
                    if (i < MAX_RETRIES - 1) {
                        try {
                            Thread.sleep(1000);  // Đợi 1 giây rồi thử lại
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        throw e;
                    }
                }
            }
        }
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DATABASE] Đóng kết nối");
            }
        } catch (SQLException e) {
            System.err.println("[DATABASE] Lỗi đóng kết nối: " + e.getMessage());
        }
    }
}
