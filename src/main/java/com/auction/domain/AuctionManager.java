package com.auction.domain;

import com.auction.server.dao.AuctionDAO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors; // tạo threads chạy ngắn
import java.util.concurrent.ScheduledExecutorService; // lập lịch
import java.util.concurrent.TimeUnit; // đơn vị thời gian

public class AuctionManager {
    // volatile: đảm bảo giá trị của biến luôn được cập nhật chính xác giữa các luồng khác nhau.
    private static volatile AuctionManager instance;
    private final Map<String, AuctionSession> sessions;
    private final AuctionDAO auctionDAO = new AuctionDAO();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private AuctionManager() {
        this.sessions = new ConcurrentHashMap<>(); // Khởi tạo bộ lưu trữ an toàn đa luồng
        loadSessionsFromDatabase(); // Load data từ database khi khởi động
        startAutoCloseTask(); // Kích hoạt ngay bộ quét thời gian tự động
    }

    public static AuctionManager getInstance() {
        if (instance == null) { //nếu đối tượng được tạo rồi, lấy dùng luôn, thay vì đợi các bước sau phức tạp
            synchronized (AuctionManager.class) { // sychronized đẻ giữ cho chỉ được phép 1 luồng truy cập
                if (instance == null) { // kiểm tra lần 2, không có thì phá vỡ nguyên tắc duy nhất của singleton
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    // ScheduledExecutorService: cứ cách 1 giây thực hiện khối code 1 lần
    // ConcurrentHashMap: Bộ nhớ lưu trữ phiên đấu giá, an toàn khi nhiều người truy cập cùng lúc
    // Hàm trên để kiểm tra và đóng các phiên đấu giá đã hết giờ, không có cái này thì cá phiên đấu giá sẽ mãi hoạt động trừ khi kết thúc thủ công

    private void startAutoCloseTask() {
        scheduler.scheduleAtFixedRate(() -> { // cách 1 giây thực hiện khối code 1 lần
            LocalDateTime now = LocalDateTime.now();
            for (AuctionSession session : sessions.values()) {  // kiểm tra tất cả các phiên đấu giá đang lưu trong hệ thống
                if ((session.getStatus() == AuctionStatus.RUNNING || session.getStatus() == AuctionStatus.EXTENDED)
                        && session.getEndTime() != null) {

        // quét các phiên đang còn hoạt động để kết thúc phiên
                    try {
                        LocalDateTime endTime = session.getEndTime();
                        if (now.isAfter(endTime)) {  // kiểm tra xem kết thúc chưa thì chuyển trạng thái rồi in ra thông báo
                            session.setStatus(AuctionStatus.FINISHED);
                            saveSessionToDatabase(session); // Lưu vào database
                            System.out.println("==> [HỆ THỐNG]: Phiên " + session.getAuctionID() + " đã kết thúc tự động!");
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }


    public boolean createSession(String auctionID, String itemID, String sellerID, double startPrice) { // tạo session mới
        return createSession(auctionID, itemID, itemID, sellerID, startPrice, 60);
    }

    public boolean createSession(String auctionID, String itemID, String itemName, String sellerID, double startPrice, int durationMinutes) {
        if (sessions.containsKey(auctionID)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        long safeDuration = durationMinutes > 0 ? durationMinutes : 60;
        AuctionSession newSession = new AuctionSession(auctionID, itemID, itemName, sellerID, startPrice,
                now, now.plusMinutes(safeDuration));
        sessions.put(auctionID, newSession);
        saveSessionToDatabase(newSession); // Lưu vào database
        return true;
    }

    public void addSession(AuctionSession session) {
        if (session != null && session.getAuctionID() != null) {
            sessions.put(session.getAuctionID(), session);
        }
    }

    public boolean startSession(String auctionID) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null || session.getStatus() != AuctionStatus.OPEN) {
            return false;
        }

        session.setStatus(AuctionStatus.RUNNING);
        saveSessionToDatabase(session); // Lưu vào database
        return true;
    }
    // tìm phiên theo Id, nếu thấy và đang ở trạng thái OPEN, chuyển sang RUNNING

    public boolean placeBid(String auctionID, String bidderID, double bidAmount) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null) {
            return false;
        }

        boolean result = session.processBid(bidderID, bidAmount);
        if (result) {
            saveSessionToDatabase(session); // Lưu vào database khi đặt giá thành công
        }
        return result;
    }

    // tìm phiên đấu giá tương ứng rồi ủy quyền cho AuctionSession xử lý
    public boolean closeSession(String auctionID) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null) {
            return false;
        }

        AuctionStatus status = session.getStatus();
        if (status != AuctionStatus.RUNNING && status != AuctionStatus.EXTENDED) {
            return false;
        }

        session.setStatus(AuctionStatus.FINISHED);
        saveSessionToDatabase(session); // Lưu vào database
        return true;
    }

    public List<AuctionSession> getAllSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
    }

    public AuctionSession getSession(String auctionID) {
        return sessions.get(auctionID);
    }

    // Lưu session vào database
    private void saveSessionToDatabase(AuctionSession session) {
        try {
            auctionDAO.saveSession(session);
        } catch (Exception e) {
            System.err.println("[CẢNH BÁO] Lỗi lưu phiên vào database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Load tất cả sessions từ database khi khởi động
    private void loadSessionsFromDatabase() {
        try {
            List<AuctionSession> loadedSessions = auctionDAO.getAllSessions();
            for (AuctionSession session : loadedSessions) {
                // Chỉ load các phiên chưa kết thúc
                if (session.getStatus() != AuctionStatus.FINISHED) {
                    sessions.put(session.getAuctionID(), session);
                    System.out.println("[HỆ THỐNG] Đã tải phiên từ database: " + session.getAuctionID());
                }
            }
        } catch (Exception e) {
            System.err.println("[CẢNH BÁO] Lỗi tải phiên từ database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Lưu tất cả sessions vào database (dùng khi exit hoặc crash)
    public void persistAllSessions() {
        try {
            for (AuctionSession session : sessions.values()) {
                saveSessionToDatabase(session);
            }
            System.out.println("[HỆ THỐNG] ✓ Đã lưu " + sessions.size() + " phiên vào database");
        } catch (Exception e) {
            System.err.println("[CẢNH BÁO] Lỗi lưu tất cả phiên: " + e.getMessage());
            e.printStackTrace();
        }
    }
}