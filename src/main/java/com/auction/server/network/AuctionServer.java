package com.auction.server.network;

import com.auction.domain.AuctionManager;
import com.auction.server.util.DatabaseUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionServer {
    public static final int DEFAULT_PORT = 9999;

    private final int port;
    private final Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService threadPool = Executors.newFixedThreadPool(50);

    public AuctionServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        initializeDatabase();  // Khởi tạo database
        seedDemoData();
        
        // Thêm shutdown hook để lưu dữ liệu khi server tắt
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[MÁY CHỦ] ⚠️ Server đang tắt, lưu dữ liệu...");
            AuctionManager.getInstance().persistAllSessions();
            threadPool.shutdown();
            System.out.println("[MÁY CHỦ] ✓ Dữ liệu đã được lưu");
        }));
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[MÁY CHỦ] Đang lắng nghe ở cổng " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[MÁY CHỦ] Client kết nối: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);
                threadPool.execute(handler);
            }
        }
    }

    public void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }

    private void seedDemoData() {
        AuctionManager manager = AuctionManager.getInstance();
        // Chỉ seed nếu chưa có phiên A1 (tránh tạo trùng)
        if (manager.getSession("A1") != null) {
            System.out.println("[MÁY CHỦ] Phiên mẫu A1 đã tồn tại, bỏ qua seed.");
            return;
        }
        try {
            // Insert item mẫu vào DB trước
            com.auction.server.dao.ItemDAO itemDAO = new com.auction.server.dao.ItemDAO();
            if (itemDAO.findById("ITEM1") == null) {
                com.auction.common.models.Item demoItem =
                    new com.auction.common.models.Electronics("ITEM1", "Laptop Demo", "Laptop mẫu cho demo", 500.0, "ELECTRONICS", "", "", "");
                itemDAO.saveItem(demoItem);
            }
            // Insert seller mẫu vào DB trước
            com.auction.server.dao.UserDAO userDAO = new com.auction.server.dao.UserDAO();
            if (userDAO.getUserByUsername("SELLER1") == null) {
                com.auction.common.models.User demoSeller =
                    new com.auction.common.models.Seller("SELLER1", "SELLER1", "seller123", "Seller Demo", "seller@demo.com");
                userDAO.saveUser(demoSeller);
            }
        } catch (Exception e) {
            System.err.println("[MÁY CHỦ] Lỗi seed dữ liệu mẫu: " + e.getMessage());
        }
        if (manager.createSession("A1", "ITEM1", "Laptop Demo", "SELLER1", 500.0, 60)) {
            manager.startSession("A1");
            System.out.println("[MÁY CHỦ] Đã tạo phiên mẫu A1.");
        }
    }

    private void initializeDatabase() {
    try {
        // 1. Kết nối thẳng vào MySQL server root (không chỉ định database nào cả)
        // Lưu ý: Cậu cần đảm bảo username/pass ở đây khớp với máy cậu
        String rootUrl = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC";
        Connection conn = java.sql.DriverManager.getConnection(rootUrl, "root", ""); 
        
        Statement stmt = conn.createStatement();
        
        // 2. Tạo database
        stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS auction_system");
        System.out.println("[MÁY CHỦ] Đã kiểm tra/tạo database 'auction_system'");
        stmt.close();
        conn.close(); // Đóng kết nối root sau khi tạo xong
        
        // 3. Bây giờ mới dùng DatabaseUtil để kết nối vào auction_system và tạo bảng
        Connection dbConn = DatabaseUtil.getInstance().getConnection(); // Lúc này DatabaseUtil sẽ kết nối vào db đã tồn tại
        Statement dbStmt = dbConn.createStatement();
        
        // Tạo bảng (Code tạo bảng của cậu giữ nguyên ở đây)
        dbStmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
            "id VARCHAR(50) PRIMARY KEY," +
            "username VARCHAR(50) NOT NULL UNIQUE," +
            "password VARCHAR(255) NOT NULL," +
            "full_name VARCHAR(100) NOT NULL," +
            "email VARCHAR(100) NOT NULL UNIQUE," +
            "role VARCHAR(20) NOT NULL," +
            "balance DOUBLE DEFAULT 0" +
        ")");
            
            // Tạo bảng items
        dbStmt.executeUpdate("CREATE TABLE IF NOT EXISTS items (" +
            "id VARCHAR(50) PRIMARY KEY," +
            "name VARCHAR(100) NOT NULL," +
            "description TEXT," +
            "init_price DOUBLE NOT NULL," +
            "category VARCHAR(50) NOT NULL" +
        ")");
        
        // Tạo bảng auction_sessions (không có FK để tránh lỗi khi seed/test)
        dbStmt.executeUpdate("CREATE TABLE IF NOT EXISTS auction_sessions (" +
            "auction_id VARCHAR(50) PRIMARY KEY," +
            "item_id VARCHAR(50) NOT NULL," +
            "seller_id VARCHAR(50) NOT NULL," +
            "start_time DATETIME NOT NULL," +
            "end_time DATETIME NOT NULL," +
            "status VARCHAR(20) NOT NULL," +
            "winner_id VARCHAR(50)," +
            "current_highest_bid DOUBLE DEFAULT 0" +
        ")");
        
        // Tạo bảng bid_transactions (không có FK để tránh lỗi)
        dbStmt.executeUpdate("CREATE TABLE IF NOT EXISTS bid_transactions (" +
            "id INT AUTO_INCREMENT PRIMARY KEY," +
            "auction_id VARCHAR(50) NOT NULL," +
            "bidder_id VARCHAR(50) NOT NULL," +
            "bid_amount DOUBLE NOT NULL," +
            "bid_time DATETIME NOT NULL" +
        ")");
            
// ... Các bảng items, auction_sessions, bid_transactions ...
        
        dbStmt.close();
        System.out.println("[MÁY CHỦ] ✓ Database đã khởi tạo hoàn tất");
        
    } catch (Exception e) {
        System.err.println("[MÁY CHỦ] ✗ Lỗi khởi tạo database: " + e.getMessage());
        e.printStackTrace();
    }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        try {
            new AuctionServer(port).start();
        } catch (IOException e) {
            System.err.println("[MÁY CHỦ] Lỗi khi khởi động: " + e.getMessage());
        }
    }
}