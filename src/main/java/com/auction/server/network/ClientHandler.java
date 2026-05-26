package com.auction.server.network;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.common.models.Bidder;
import com.auction.common.models.Item;
import com.auction.common.models.Seller;
import com.auction.common.models.User;
import com.auction.common.pattern.ItemFactory;
import com.auction.domain.AuctionManager;
import com.auction.domain.AuctionSession;
import com.auction.domain.AuctionStatus;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;

import java.io.*;
import java.net.Socket;

/**
 * Xử lý giao tiếp với một client kết nối vào server.
 *
 * <p>Mỗi lệnh từ client được phân tách bằng ký tự '|'.
 * Ví dụ: {@code PLACE_BID|auctionId|bidderId|amount}</p>
 *
 * <p>Danh sách lệnh hỗ trợ:
 * <ul>
 *   <li>LOGIN|username|password</li>
 *   <li>REGISTER|username|password|email|role</li>
 *   <li>LIST — danh sách phiên (id:price:status)</li>
 *   <li>LIST_DETAIL — danh sách phiên đầy đủ (1 lần gọi thay vì N+1)</li>
 *   <li>GET_SESSION|auctionId</li>
 *   <li>CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|durationMinutes</li>
 *   <li>CREATE_ITEM|itemId|itemName|description|initPrice|category</li>
 *   <li>PLACE_BID|auctionId|bidderId|amount</li>
 *   <li>QUIT</li>
 * </ul>
 */
public class ClientHandler implements Runnable {

    private final Socket       socket;
    private final AuctionServer server;
    private PrintWriter        out;

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
    }

    /** Gửi tin nhắn chủ động tới client (dùng cho broadcast). */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            this.out = writer;
            out.println("CHAO_MUNG|AuctionServer v2.0");

            String line;
            while ((line = in.readLine()) != null) {
                String response = handleRequest(line.trim());
                out.println(response);
                if ("TAM_BIET".equals(response)) break;
            }

        } catch (IOException e) {
            System.out.println("[SERVER] Mất kết nối client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // -------------------------------------------------------------------------
    // Router chính
    // -------------------------------------------------------------------------
    private String handleRequest(String raw) {
        if (raw == null || raw.isBlank()) return "LOI|Yeu cau trong";

        String[] parts   = raw.split("\\|");
        String   command = parts[0].toUpperCase().trim();

        AuctionManager manager = AuctionManager.getInstance();

        return switch (command) {
            case "LOGIN"          -> processLogin(parts);
            case "REGISTER"       -> processRegister(parts);
            case "LIST"           -> buildListResponse(manager);
            case "LIST_DETAIL"    -> buildListDetailResponse(manager);
            case "GET_SESSION"    -> buildSessionResponse(parts, manager);
            case "CREATE_AUCTION" -> processCreateAuction(parts, manager);
            case "CREATE_ITEM"    -> processCreateItem(parts);
            case "PLACE_BID"      -> processBid(parts, manager);
            case "CLOSE_SESSION" -> processCloseSession(parts, manager);
            case "DEPOSIT"       -> processDeposit(parts);
            case "QUIT"          -> "TAM_BIET";
            default               -> "LOI|Lenh khong hop le: " + command;
        };
    }

    // -------------------------------------------------------------------------
    // Xử lý từng lệnh
    // -------------------------------------------------------------------------

    /**
     * LOGIN|username|password
     * → LOGIN_SUCCESS|role|id|fullName|email  hoặc  LOGIN_FAILED|...
     */
    private String processLogin(String[] parts) {
        if (parts.length != 3) return "LOI|Dinh dang: LOGIN|username|password";
        try {
            UserDAO userDAO = new UserDAO();
            User user = userDAO.getUserByUsername(parts[1]);
            if (user == null) return "LOGIN_FAILED|Sai tai khoan hoac mat khau";

            // Xác thực BCrypt — tương thích ngược: nếu hash bắt đầu bằng '$2' thì là BCrypt
            boolean valid = isPasswordValid(parts[2], user.getPassword());
            if (!valid) return "LOGIN_FAILED|Sai tai khoan hoac mat khau";

            return "LOGIN_SUCCESS|" + user.getRole() + "|" + user.getId()
                    + "|" + user.getFullName() + "|" + user.getEmail();

        } catch (Exception e) {
            return "LOI|Loi he thong: " + e.getMessage();
        }
    }

    /**
     * REGISTER|username|password|email|role
     * → REGISTER_SUCCESS  hoặc  REGISTER_FAILED|...
     */
    private String processRegister(String[] parts) {
        if (parts.length != 5) return "LOI|Dinh dang: REGISTER|username|password|email|role";
        String username = parts[1];
        String rawPassword = parts[2];
        String email  = parts[3];
        String role   = parts[4].toUpperCase();

        try {
            UserDAO userDAO = new UserDAO();
            if (userDAO.getUserByUsername(username) != null) {
                return "REGISTER_FAILED|Ten dang nhap da ton tai";
            }

            // Mã hóa mật khẩu bằng BCrypt (cost factor 12)
            String hashedPassword = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());

            String id = String.valueOf(System.currentTimeMillis());
            User newUser = switch (role) {
                case "SELLER" -> new Seller(id, username, hashedPassword, username, email);
                default       -> new Bidder(id, username, hashedPassword, username, email, 0.0);
            };

            userDAO.saveUser(newUser);
            return "REGISTER_SUCCESS";

        } catch (Exception e) {
            return "LOI|Loi DB: " + e.getMessage();
        }
    }

    /**
     * PLACE_BID|auctionId|bidderId|amount
     * → CHAP_NHAN|...  hoặc  TU_CHOI|...
     */
    private String processBid(String[] parts, AuctionManager manager) {
        if (parts.length != 4) return "LOI|Dinh dang: PLACE_BID|auctionId|bidderId|amount";

        double amount;
        try {
            amount = Double.parseDouble(parts[3]);
        } catch (NumberFormatException e) {
            return "LOI|So tien dat gia phai la so";
        }

        boolean success = manager.placeBid(parts[1], parts[2], amount);
        AuctionSession session = manager.getSession(parts[1]);
        if (session == null) return "LOI|Khong tim thay phien dau gia: " + parts[1];

        String statusInfo = buildSessionStatusStr(session);

        if (success) {
            // Broadcast cho tất cả clients đang xem phiên này
            String broadcast = "CAP_NHAT|id=" + session.getAuctionID()
                    + "|gia_hien_tai=" + session.getCurrentHighestBid()
                    + "|nguoi_dan_dau=" + nullSafe(session.getWinnerID())
                    + "|trang_thai=" + session.getStatus()
                    + "|end_time=" + nullSafe(session.getEndTime());
            server.broadcast(broadcast);
            return "CHAP_NHAN|" + statusInfo;
        }
        return "TU_CHOI|" + statusInfo;
    }

    /**
     * LIST — danh sách tóm tắt (id:price:status).
     * Dùng cho hiển thị nhanh.
     */
    private String buildListResponse(AuctionManager manager) {
        StringBuilder sb = new StringBuilder("DANH_SACH");
        for (AuctionSession s : manager.getAllSessions()) {
            sb.append("|").append(s.getAuctionID())
              .append(":").append(s.getCurrentHighestBid())
              .append(":").append(s.getStatus());
        }
        return sb.length() == "DANH_SACH".length() ? "DANH_SACH|trong" : sb.toString();
    }

    /**
     * LIST_DETAIL — danh sách đầy đủ trong <strong>một lần gọi duy nhất</strong>.
     * Giải quyết N+1 problem trong MainAuctionController.
     *
     * <p>Format: {@code DANH_SACH_CHI_TIET|id:itemName:price:status:endTime|...}</p>
     */
    private String buildListDetailResponse(AuctionManager manager) {
        StringBuilder sb = new StringBuilder("DANH_SACH_CHI_TIET");
        for (AuctionSession s : manager.getAllSessions()) {
            sb.append("|")
              .append(s.getAuctionID()).append(":")
              .append(s.getDisplayItem()).append(":")
              .append(s.getCurrentHighestBid()).append(":")
              .append(s.getStatus()).append(":")
              .append(nullSafe(s.getEndTime()));
        }
        return sb.length() == "DANH_SACH_CHI_TIET".length()
                ? "DANH_SACH_CHI_TIET|trong" : sb.toString();
    }

    /**
     * GET_SESSION|auctionId
     * → PHIEN|id=...|vat_pham=...|gia_hien_tai=...|nguoi_dan_dau=...|trang_thai=...|end_time=...
     */
    private String buildSessionResponse(String[] parts, AuctionManager manager) {
        if (parts.length != 2) return "LOI|Dinh dang: GET_SESSION|auctionId";
        AuctionSession session = manager.getSession(parts[1]);
        if (session == null) return "LOI|Khong tim thay phien: " + parts[1];

        return "PHIEN|id=" + session.getAuctionID()
                + "|vat_pham=" + session.getDisplayItem()
                + "|" + buildSessionStatusStr(session)
                + "|end_time=" + nullSafe(session.getEndTime());
    }

    /**
     * CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|durationMinutes
     */
    private String processCreateAuction(String[] parts, AuctionManager manager) {
        if (parts.length != 7) {
            return "LOI|Dinh dang: CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|durationMinutes";
        }
        double startPrice;
        int    duration;
        try {
            startPrice = Double.parseDouble(parts[5]);
            duration   = Integer.parseInt(parts[6]);
        } catch (NumberFormatException e) {
            return "LOI|Gia va thoi gian phai la so";
        }

        // Đảm bảo item tồn tại trong DB (tránh FK violation)
        try {
            ItemDAO itemDAO = new ItemDAO();
            if (itemDAO.findById(parts[2]) == null) {
                Item newItem = ItemFactory.createElectronics(
                        parts[2], parts[3], "", startPrice, "", "", "", "");
                itemDAO.saveItem(newItem);
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Lỗi tạo item: " + e.getMessage());
        }

        boolean created = manager.createSession(
                parts[1], parts[2], parts[3], parts[4], startPrice, duration);
        if (!created) return "LOI|Ma phien da ton tai: " + parts[1];

        manager.startSession(parts[1]);
        return "CREATE_AUCTION_SUCCESS|" + parts[1];
    }

    /**
     * CREATE_ITEM|itemId|itemName|description|initPrice|category
     */
    private String processCreateItem(String[] parts) {
        if (parts.length != 6) return "LOI|Dinh dang: CREATE_ITEM|itemId|itemName|description|initPrice|category";
        try {
            double price = Double.parseDouble(parts[4]);
            Item item = ItemFactory.create(parts[1], parts[2], parts[3], price, parts[5]);
            new ItemDAO().saveItem(item);
            return "CREATE_ITEM_SUCCESS|" + parts[1];
        } catch (Exception e) {
            return "LOI|Loi tao vat pham: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Xây chuỗi trạng thái phiên (dùng chung cho nhiều response). */
    private String buildSessionStatusStr(AuctionSession session) {
        return "gia_hien_tai=" + session.getCurrentHighestBid()
                + "|nguoi_dan_dau=" + nullSafe(session.getWinnerID())
                + "|trang_thai=" + session.getStatus();
    }

    /** Chuyển giá trị null thành chuỗi rỗng (tránh "null" trong response). */
    private String nullSafe(Object obj) {
        return (obj != null) ? obj.toString() : "";
    }

    /**
     * Kiểm tra mật khẩu — hỗ trợ cả BCrypt (mới) và plaintext (cũ, backward compat).
     * Sau khi toàn bộ user đã đăng ký lại, có thể xóa nhánh plaintext.
     */
    private boolean isPasswordValid(String rawPassword, String storedPassword) {
        if (storedPassword != null && storedPassword.startsWith("$2")) {
            // BCrypt hash
            return BCrypt.verifyer()
                         .verify(rawPassword.toCharArray(), storedPassword)
                         .verified;
        }
        // Fallback plaintext (người dùng cũ seed/demo)
        return rawPassword.equals(storedPassword);
    }


    /**
     * CLOSE_SESSION|auctionId — Admin đóng phiên thủ công.
     */
    private String processCloseSession(String[] parts, AuctionManager manager) {
        if (parts.length != 2) return "LOI|Dinh dang: CLOSE_SESSION|auctionId";
        boolean ok = manager.closeSession(parts[1]);
        if (!ok) return "LOI|Khong the dong phien (khong ton tai hoac da dong): " + parts[1];

        // Broadcast kết thúc tới tất cả client
        server.broadcast("CAP_NHAT|id=" + parts[1]
                + "|trang_thai=FINISHED|gia_hien_tai=0|nguoi_dan_dau=");
        return "CLOSE_SESSION_SUCCESS|" + parts[1];
    }

    /**
     * DEPOSIT|bidderId|amount — Nạp tiền vào ví Bidder.
     */
    private String processDeposit(String[] parts) {
        if (parts.length != 3) return "LOI|Dinh dang: DEPOSIT|bidderId|amount";
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            return "LOI|So tien phai la so";
        }
        if (amount <= 0) return "LOI|So tien phai lon hon 0";
        try {
            UserDAO userDAO = new UserDAO();
            User user = userDAO.findById(parts[1]);
            if (!(user instanceof Bidder bidder)) return "LOI|Khong tim thay Bidder: " + parts[1];
            bidder.getWallet().deposit(amount);
            userDAO.saveUser(bidder);
            return "DEPOSIT_SUCCESS|" + bidder.getWallet().getBalance();
        } catch (Exception e) {
            return "LOI|Loi nap tien: " + e.getMessage();
        }
    }

    /** Giải phóng tài nguyên khi client ngắt kết nối. */
    private void cleanup() {
        server.removeClient(this);
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) { }
    }
}
