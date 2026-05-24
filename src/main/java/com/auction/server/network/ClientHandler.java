package com.auction.server.network;

import com.auction.domain.AuctionManager;
import com.auction.domain.AuctionSession;
import com.auction.server.dao.ItemDAO;
import com.auction.common.models.Item;
import com.auction.common.models.Electronics;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private PrintWriter out;

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
    }

    // Phương thức để server gọi khi cần gửi tin nhắn chủ động cho client
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.out = writer;
            out.println("CHAO_MUNG|AuctionServer");

            String line;
            while ((line = in.readLine()) != null) {
                String response = handleRequest(line.trim());
                out.println(response);

                if ("TAM_BIET".equals(response)) break;
            }
        } catch (IOException e) {
            System.out.println("[MÁY CHỦ] Lỗi kết nối client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private String handleRequest(String raw) {
        if (raw == null || raw.isBlank()) {
            return "LOI|Yeu cau rong";
        }

        String[] parts = raw.split("\\|");
        if (parts.length == 0 || parts[0].isBlank()) {
            return "LOI|Khong co lenh";
        }

        String command = parts[0].toUpperCase();
        AuctionManager manager = AuctionManager.getInstance();

        switch (command) {
            case "LOGIN":
                return processLogin(parts);
            case "REGISTER":
                return processRegister(parts);
            case "PLACE_BID":
                return processBid(parts, manager);
            case "LIST":
                return buildListResponse(manager);
            case "GET_SESSION":
                return buildSessionResponse(parts, manager);
            case "CREATE_AUCTION":
                return processCreateAuction(parts, manager);
            case "CREATE_ITEM":
                return processCreateItem(parts);
            case "QUIT":
                return "TAM_BIET";
            default:
                return "LOI|Lenh khong hop le";
        }
    }

    private String processLogin(String[] parts) {
        if (parts.length != 3) {
            return "LOI|Dinh dang: LOGIN|username|password";
        }
        String username = parts[1];
        String password = parts[2];

        try {
            com.auction.server.dao.UserDAO userDAO = new com.auction.server.dao.UserDAO();
            com.auction.common.models.User user = userDAO.getUserByUsername(username);
            if (user != null && user.getPassword().equals(password)) {
                return "LOGIN_SUCCESS|" + user.getRole() + "|" + user.getId() + "|" + user.getFullName() + "|" + user.getEmail();
            } else {
                return "LOGIN_FAILED|Sai tai khoan hoac mat khau";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "LOI|Loi he thong: " + e.getMessage();
        }
    }

    private String processRegister(String[] parts) {
        if (parts.length != 5) return "LOI|Dinh dang: REGISTER|username|password|email|role";
        String username = parts[1];
        String password = parts[2];
        String email = parts[3];
        String role = parts[4];
        
        try {
            com.auction.server.dao.UserDAO userDAO = new com.auction.server.dao.UserDAO();
            if (userDAO.getUserByUsername(username) != null) {
                return "REGISTER_FAILED|Tên đăng nhập đã tồn tại!";
            }
            long id = System.currentTimeMillis();
            com.auction.common.models.User newUser;
            if ("SELLER".equals(role)) {
                newUser = new com.auction.common.models.Seller(String.valueOf(id), username, password, username, email);
            } else {
                newUser = new com.auction.common.models.Bidder(String.valueOf(id), username, password, username, email, 0.0);
            }
            userDAO.saveUser(newUser);
            return "REGISTER_SUCCESS";
        } catch (Exception e) {
            e.printStackTrace();
            return "LOI|Lỗi DB: " + e.getMessage();
        }
    }

    private String processBid(String[] parts, AuctionManager manager) {
        if (parts.length != 4) {
            return "LOI|Dinh dang: PLACE_BID|auctionId|bidderId|amount";
        }

        String auctionId = parts[1];
        String bidderId = parts[2];
        double amount;
        try {
            amount = Double.parseDouble(parts[3]);
        } catch (NumberFormatException e) {
            return "LOI|So tien dat gia phai la so";
        }

        boolean success = manager.placeBid(auctionId, bidderId, amount);
        AuctionSession session = manager.getSession(auctionId);
        if (session == null) {
            return "LOI|Khong tim thay phien dau gia";
        }

        if (success) {
            String updateMsg = "CAP_NHAT|id=" + auctionId
                    + "|gia_hien_tai=" + session.getCurrentHighestBid()
                    + "|nguoi_dan_dau=" + session.getWinnerID()
                    + "|trang_thai=" + session.getStatus();
            server.broadcast(updateMsg);
            return "CHAP_NHAN|gia_hien_tai=" + session.getCurrentHighestBid()
                    + "|nguoi_dan_dau=" + session.getWinnerID()
                    + "|trang_thai=" + session.getStatus();
        }

        return "TU_CHOI|gia_hien_tai=" + session.getCurrentHighestBid()
                + "|nguoi_dan_dau=" + session.getWinnerID()
                + "|trang_thai=" + session.getStatus();
    }

    /**
     * Xử lý tạo phiên đấu giá mới.
     * Định dạng: CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|durationMinutes
     */
    private String processCreateAuction(String[] parts, AuctionManager manager) {
        if (parts.length != 7) {
            return "LOI|Dinh dang: CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|durationMinutes";
        }
        String auctionId = parts[1];
        String itemId    = parts[2];
        String itemName  = parts[3];
        String sellerId  = parts[4];
        double startPrice;
        int    durationMinutes;
        try {
            startPrice       = Double.parseDouble(parts[5]);
            durationMinutes  = Integer.parseInt(parts[6]);
        } catch (NumberFormatException e) {
            return "LOI|Gia va thoi gian phai la so";
        }

        // Đảm bảo item tồn tại trong DB trước khi tạo phiên (tránh FK violation)
        try {
            ItemDAO itemDAO = new ItemDAO();
            // Chỉ insert nếu chưa có
            Item existing = itemDAO.findById(itemId);
            if (existing == null) {
                Item newItem = new Electronics(itemId, itemName, "", startPrice, "", "", "", "");
                itemDAO.saveItem(newItem);
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Lỗi lưu item: " + e.getMessage());
            // Không dừng, thử tạo phiên tiếp
        }

        boolean created = manager.createSession(auctionId, itemId, itemName, sellerId, startPrice, durationMinutes);
        if (!created) {
            return "LOI|Ma phien da ton tai: " + auctionId;
        }
        manager.startSession(auctionId);
        return "CREATE_AUCTION_SUCCESS|" + auctionId;
    }

    /**
     * Xử lý tạo vật phẩm đấu giá.
     * Định dạng: CREATE_ITEM|itemId|itemName|description|initPrice|category
     */
    private String processCreateItem(String[] parts) {
        if (parts.length != 6) {
            return "LOI|Dinh dang: CREATE_ITEM|itemId|itemName|description|initPrice|category";
        }
        try {
            String itemId   = parts[1];
            String name     = parts[2];
            String desc     = parts[3];
            double price    = Double.parseDouble(parts[4]);
            String category = parts[5];
            ItemDAO itemDAO = new ItemDAO();
            Item item = new Electronics(itemId, name, desc, price, category, "", "", "");
            itemDAO.saveItem(item);
            return "CREATE_ITEM_SUCCESS|" + itemId;
        } catch (Exception e) {
            return "LOI|Loi tao vat pham: " + e.getMessage();
        }
    }

    private String buildListResponse(AuctionManager manager) {
        StringBuilder sb = new StringBuilder("DANH_SACH");
        for (AuctionSession s : manager.getAllSessions()) {
            sb.append("|")
                    .append(s.getAuctionID())
                    .append(":")
                    .append(s.getCurrentHighestBid())
                    .append(":")
                    .append(s.getStatus());
        }
        if ("DANH_SACH".contentEquals(sb)) {
            return "DANH_SACH|trong";
        }
        return sb.toString();
    }

    private String buildSessionResponse(String[] parts, AuctionManager manager) {
        if (parts.length != 2) {
            return "LOI|Dinh dang: GET_SESSION|auctionId";
        }
        AuctionSession session = manager.getSession(parts[1]);
        if (session == null) {
            return "LOI|Khong tim thay phien dau gia";
        }
        
        String itemName = session.getItemID();
        try {
            ItemDAO itemDAO = new ItemDAO();
            Item item = itemDAO.findById(session.getItemID());
            if (item != null) {
                itemName = item.getName();
            }
        } catch (Exception e) {
            // Nếu lỗi DB, vẫn dùng ID
        }

        return "PHIEN|id=" + session.getAuctionID()
                + "|vat_pham=" + itemName
                + "|gia_hien_tai=" + session.getCurrentHighestBid()
                + "|nguoi_dan_dau=" + (session.getWinnerID() == null ? "" : session.getWinnerID())
                + "|trang_thai=" + session.getStatus()
                + "|end_time=" + session.getEndTime();
    }

    private void cleanup() {
        server.removeClient(this);
        // Lưu tất cả sessions khi client disconnect để tránh mất dữ liệu
        AuctionManager.getInstance().persistAllSessions();
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}