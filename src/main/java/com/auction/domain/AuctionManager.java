package com.auction.domain;

import com.auction.common.dto.BidResult;
import com.auction.common.models.Bidder;
import com.auction.common.models.User;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.UserDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton quản lý toàn bộ phiên đấu giá.
 *
 * <p>Là tầng trung gian giữa {@link AuctionSession} (nghiệp vụ thuần túy)
 * và tầng DAO (database). Mọi thao tác DB và xử lý Wallet đều nằm ở đây.</p>
 *
 * <p>Dùng {@link ConcurrentHashMap} để lưu session an toàn đa luồng.
 * Dùng {@link ScheduledExecutorService} để tự động đóng phiên hết giờ.</p>
 */
public class AuctionManager {

    // -------------------------------------------------------------------------
    // Singleton (Double-Checked Locking)
    // -------------------------------------------------------------------------
    private static volatile AuctionManager instance;

    private final Map<String, AuctionSession> sessions    = new ConcurrentHashMap<>();
    private final AuctionDAO                  auctionDAO  = new AuctionDAO();
    private final ScheduledExecutorService    scheduler   =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "auction-auto-close");
                t.setDaemon(true);
                return t;
            });

    private AuctionManager() {
        loadSessionsFromDatabase();
        startAutoCloseTask();
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Tạo / Quản lý phiên
    // -------------------------------------------------------------------------

    /**
     * Tạo phiên đấu giá mới (mặc định 60 phút).
     */
    public boolean createSession(String auctionID, String itemID,
                                  String sellerID, double startPrice) {
        return createSession(auctionID, itemID, itemID, sellerID, startPrice, 60);
    }

    /**
     * Tạo phiên đấu giá mới với thời lượng tuỳ chỉnh.
     *
     * @return true nếu tạo thành công; false nếu ID đã tồn tại hoặc tham số sai
     */
    public boolean createSession(String auctionID, String itemID, String itemName,
                                  String sellerID, double startPrice, int durationMinutes) {
        if (auctionID == null || auctionID.isBlank()) return false;
        if (sessions.containsKey(auctionID)) return false;

        int safeDuration = (durationMinutes > 0) ? durationMinutes : 60;
        LocalDateTime now = LocalDateTime.now();

        AuctionSession session = new AuctionSession(
                auctionID, itemID, itemName, sellerID,
                startPrice, now, now.plusMinutes(safeDuration));

        sessions.put(auctionID, session);
        saveSessionSilently(session);
        return true;
    }

    public void addSession(AuctionSession session) {
        if (session != null && session.getAuctionID() != null) {
            sessions.put(session.getAuctionID(), session);
        }
    }

    /**
     * Chuyển phiên từ OPEN → RUNNING.
     */
    public boolean startSession(String auctionID) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null || session.getStatus() != AuctionStatus.OPEN) return false;

        session.setStatus(AuctionStatus.RUNNING);
        saveSessionSilently(session);
        return true;
    }

    /**
     * Đóng phiên thủ công (RUNNING/EXTENDED → FINISHED).
     */
    public boolean closeSession(String auctionID) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null) return false;

        AuctionStatus st = session.getStatus();
        if (st != AuctionStatus.RUNNING && st != AuctionStatus.EXTENDED) return false;

        session.setStatus(AuctionStatus.FINISHED);
        saveSessionSilently(session);
        return true;
    }

    // -------------------------------------------------------------------------
    // Đặt giá — xử lý Wallet tại đây (không trong AuctionSession)
    // -------------------------------------------------------------------------

    /**
     * Xử lý một lần đặt giá.
     *
     * <ol>
     *   <li>Ủy quyền logic nghiệp vụ cho {@link AuctionSession#processBid}</li>
     *   <li>Nếu thành công: freeze tiền người mới, release tiền người cũ</li>
     *   <li>Lưu giao dịch và trạng thái phiên vào DB</li>
     * </ol>
     *
     * @return true nếu đặt giá thành công
     */
    public boolean placeBid(String auctionID, String bidderID, double bidAmount) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null) {
            System.err.println("[MANAGER] Không tìm thấy phiên: " + auctionID);
            return false;
        }

        // Bước 1: Xử lý nghiệp vụ đấu giá (thread-safe qua synchronized trong AuctionSession)
        BidResult result = session.processBid(bidderID, bidAmount);
        if (!result.success) {
            System.out.println("[MANAGER] Đặt giá bị từ chối: " + result.message);
            return false;
        }

        // Bước 2: Xử lý Wallet (freeze tiền mới, release tiền cũ)
        processWalletOperations(result, bidAmount);

        // Bước 3: Lưu giao dịch và phiên vào DB
        try {
            auctionDAO.saveBidTransaction(result.transaction);
            saveSessionSilently(session);
        } catch (Exception e) {
            System.err.println("[MANAGER] Lỗi lưu giao dịch: " + e.getMessage());
            // Không rollback trạng thái in-memory — bid đã được chấp nhận
        }

        return true;
    }

    /**
     * Freeze tiền của bidder mới, release tiền của bidder cũ (nếu có).
     * Thao tác này được thực hiện best-effort: lỗi không làm hỏng kết quả bid.
     */
    private void processWalletOperations(BidResult result, double bidAmount) {
        try {
            UserDAO userDAO = new UserDAO();

            // A. Freeze tiền của bidder mới
            String newBidderID = result.transaction.getBidderID();
            User newUser = userDAO.findById(newBidderID);
            if (newUser instanceof Bidder newBidder) {
                boolean frozen = newBidder.getWallet().freeze(bidAmount);
                if (!frozen) {
                    // Không đủ tiền — nhưng bid đã được ghi nhận trong session
                    // (hệ thống chưa có pre-validation trước bid; cải tiến tương lai)
                    System.out.printf("[WALLET] ⚠ Bidder %s không đủ tiền để freeze %.0f%n",
                            newBidderID, bidAmount);
                } else {
                    userDAO.saveUser(newBidder);
                    System.out.printf("[WALLET] Freeze %.0f từ %s%n", bidAmount, newBidderID);
                }
            }

            // B. Release tiền của bidder cũ (người vừa bị vượt mặt)
            String oldBidderID = result.refundBidderID;
            if (oldBidderID != null && !oldBidderID.isBlank()) {
                User oldUser = userDAO.findById(oldBidderID);
                if (oldUser instanceof Bidder oldBidder) {
                    oldBidder.getWallet().release(result.refundAmount);
                    userDAO.saveUser(oldBidder);
                    System.out.printf("[WALLET] Hoàn %.0f về cho %s%n",
                            result.refundAmount, oldBidderID);
                }
            }

        } catch (Exception e) {
            System.err.println("[WALLET] Lỗi xử lý ví: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Truy vấn
    // -------------------------------------------------------------------------
    public AuctionSession getSession(String auctionID) {
        return sessions.get(auctionID);
    }

    public List<AuctionSession> getAllSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
    }

    // -------------------------------------------------------------------------
    // Lưu tất cả phiên (dùng khi shutdown)
    // -------------------------------------------------------------------------
    public void persistAllSessions() {
        int count = 0;
        for (AuctionSession session : sessions.values()) {
            saveSessionSilently(session);
            count++;
        }
        System.out.println("[MANAGER] ✓ Đã lưu " + count + " phiên vào database.");
    }

    // -------------------------------------------------------------------------
    // Tác vụ tự động đóng phiên hết giờ
    // -------------------------------------------------------------------------
    private void startAutoCloseTask() {
        scheduler.scheduleAtFixedRate(() -> {
            LocalDateTime now = LocalDateTime.now();
            for (AuctionSession session : sessions.values()) {
                AuctionStatus st = session.getStatus();
                if ((st == AuctionStatus.RUNNING || st == AuctionStatus.EXTENDED)
                        && session.getEndTime() != null
                        && now.isAfter(session.getEndTime())) {
                    session.setStatus(AuctionStatus.FINISHED);
                    saveSessionSilently(session);
                    System.out.println("[AUTO-CLOSE] Phiên " + session.getAuctionID() + " đã kết thúc tự động.");
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // DB helpers
    // -------------------------------------------------------------------------
    private void saveSessionSilently(AuctionSession session) {
        try {
            auctionDAO.saveSession(session);
        } catch (Exception e) {
            System.err.println("[MANAGER] ⚠ Lỗi lưu phiên " + session.getAuctionID()
                    + ": " + e.getMessage());
        }
    }

    private void loadSessionsFromDatabase() {
        try {
            List<AuctionSession> loaded = auctionDAO.getAllSessions();
            for (AuctionSession s : loaded) {
                // Chỉ nạp lại các phiên chưa kết thúc
                if (s.getStatus() != AuctionStatus.FINISHED) {
                    sessions.put(s.getAuctionID(), s);
                    System.out.println("[MANAGER] Đã nạp phiên từ DB: " + s.getAuctionID());
                }
            }
        } catch (Exception e) {
            System.err.println("[MANAGER] ⚠ Lỗi nạp phiên từ DB: " + e.getMessage());
        }
    }
}
