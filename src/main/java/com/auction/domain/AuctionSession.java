package com.auction.domain;


import com.auction.server.dao.UserDAO;
import com.auction.server.util.DatabaseUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import com.auction.common.models.Bidder;
import com.auction.common.models.User;
import java.time.LocalDateTime; //ngày giờ hiện tại
import java.time.Duration; // tính khoảng cách thời gian giữa hai thời điểm
import java.time.format.DateTimeFormatter; // định dạng kiểu chuỗi thành ngày - giờ và ngược lại
import java.util.ArrayList; // mảng động, dùng để lưu trữc các tập hợp dữ liệu
import java.util.Collections;  // sort, reverse
import java.util.List; // interface chung

public class AuctionSession {
    private String auctionID; //ID phiên đấu giá
    private String itemID; // ID đồ bán
    private String itemName; // Tên đồ bán
    private String sellerID; // ID người bán
    private double currentHighestBid; // giá đang đặt cao nhất
    private String currentHighestBidderID; // ID người đặt giá cao nhất
    private String winnerID; // ID người thắng
    private LocalDateTime startTime; // giờ bắt đầu
    private LocalDateTime endTime; // giờ kết thúc
    private AuctionStatus status; // trạng thái
    private List<BidTransaction> bidHistory; // lịch sử tất cả các lần đặt giá
    private static final long ANTI_SNIPING_SECONDS = 30; // Số giây cuối phiên để kích hoạt gia hạn
    private static final long EXTENSION_SECONDS = 60; // Thêm bao nhiêu giây khi gia hạn

    public AuctionSession(String auctionID, String itemID, String sellerID, double startPrice,
                         LocalDateTime startTime, LocalDateTime endTime) {
        this(auctionID, itemID, itemID, sellerID, startPrice, startTime, endTime);
    }

    public AuctionSession(String auctionID, String itemID, String itemName, String sellerID, double startPrice,
                          LocalDateTime startTime, LocalDateTime endTime) {
        this.auctionID = auctionID;
        this.itemID = itemID;
        this.itemName = itemName;
        this.sellerID = sellerID;
        this.currentHighestBid = startPrice;
        this.currentHighestBidderID = null;
        this.winnerID = null;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AuctionStatus.OPEN;
        this.bidHistory = new ArrayList<>();
    }

    public String getAuctionID() {
        return auctionID;
    }

    public void setAuctionID(String auctionID) {
        this.auctionID = auctionID;
    }

    public String getItemID() {
        return itemID;
    }

    public void setItemID(String itemID) {
        this.itemID = itemID;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getDisplayItem() {
        return itemName != null && !itemName.isBlank() ? itemName : itemID;
    }

    public String getSellerID() {
        return sellerID;
    }

    public void setSellerID(String sellerID) {
        this.sellerID = sellerID;
    }

    public double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(double currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }

    public String getWinnerID() {
        return winnerID;
    }

    public void setWinnerID(String winnerID) {
        this.winnerID = winnerID;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    // synchronized giúp đảm bảo tại một thời điểm chỉ có duy nhất 1 người được thực hiện đặt giá, 
    // tránh việc hai người cùng đặt một lúc dẫn đến loạn số liệu
    public synchronized boolean processBid(String bidderID, double bidAmount) {
        // 1. Khởi tạo mảng lịch sử nếu nó đang Null (Chống lỗi NullPointerException)
        if (this.bidHistory == null) {
            this.bidHistory = new ArrayList<>();
        }

        // 2. Kiểm tra trạng thái và thời gian hợp lệ
        if (this.status != AuctionStatus.RUNNING && this.status != AuctionStatus.EXTENDED) {
            return false;
        }
        if (this.endTime != null && LocalDateTime.now().isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED;
            return false;
        }

        // 3. Kiểm tra luật lệ: Giá mới phải cao hơn giá hiện tại
        if (bidAmount <= this.currentHighestBid) {
            return false;
        }

        try {
            UserDAO userDAO = new UserDAO();
            
            // --- MỤC 5: TÍCH HỢP VÍ TIỀN (WALLET) ---
            // A. Tạm khóa tiền (freeze) của người vừa đặt giá mới
            User currentUser = userDAO.findById(bidderID);
            if (currentUser instanceof Bidder) {
                Bidder newBidder = (Bidder) currentUser;
                boolean canFreeze = newBidder.getWallet().freeze(bidAmount);
                if (!canFreeze) {
                    System.out.println("[TỪ CHỐI] Bidder " + bidderID + " không đủ số dư để đặt cọc!");
                    return false; // Không đủ tiền thì đuổi ra
                }
                userDAO.saveUser(newBidder); // Trừ tiền thành công, lưu lại vào DB
            }

            // B. Hoàn tiền cọc (release) cho người dẫn đầu cũ (người vừa bị vượt mặt)
            if (this.currentHighestBidderID != null && !this.currentHighestBidderID.isEmpty()) {
                User oldUser = userDAO.findById(this.currentHighestBidderID);
                if (oldUser instanceof Bidder) {
                    Bidder oldBidder = (Bidder) oldUser;
                    oldBidder.getWallet().release(this.currentHighestBid); // Trả lại tiền
                    userDAO.saveUser(oldBidder); // Cập nhật lại số dư vào DB
                }
            }

            // --- CẬP NHẬT TRẠNG THÁI PHIÊN ---
            this.currentHighestBid = bidAmount;
            this.currentHighestBidderID = bidderID;
            this.winnerID = bidderID;

            // --- MỤC 6: LƯU LỊCH SỬ GIAO DỊCH (BID TRANSACTION) ---
            // C. Tạo Transaction và lưu vào RAM
            BidTransaction newTransaction = new BidTransaction(this.auctionID, bidderID, bidAmount, LocalDateTime.now());
            this.bidHistory.add(newTransaction);

            // D. Ghi thẳng xuống Database (Bảng bid_transactions đã có sẵn trong schema.sql)
            saveTransactionToDB(newTransaction);

            // 4. Kiểm tra xem có cần gia hạn giờ (Anti-sniping) không
            checkAndExtendTime();

            System.out.println("[THÀNH CÔNG] " + bidderID + " đã vươn lên dẫn đầu với giá: " + bidAmount);
            return true;

        } catch (Exception e) {
            System.err.println("[LỖI HỆ THỐNG] Lỗi khi xử lý đấu giá: " + e.getMessage());
            return false;
        }
    }

    // Hàm phụ trợ được gọi ở trên để ném lịch sử xuống DB
    private void saveTransactionToDB(BidTransaction tx) {
        String sql = "INSERT INTO bid_transactions (auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tx.getAuctionID());
            stmt.setString(2, tx.getBidderID());
            stmt.setDouble(3, tx.getBidAmount());
            stmt.setString(4, tx.getBidTime().toString()); // Lưu giờ dưới dạng chuỗi
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[LỖI DB] Không thể lưu lịch sử đặt giá: " + e.getMessage());
        }
    }
    // nếu trạng thái khác running hoặc extended thì in ra thông báo
    // còn không thực hiện tiếp khối bên dưới, nếu giá đặt nhỏ hơn giá cao nhất đã đặt thì in ra thông báo
    // không có gì thì xuống hai dòng dưới, gán giá cao nhất cho giá vừa đặt và winnerID thành bidderID
    // gọi hàm mở rộng thời gian
    // in ra thông báo nếu đặt giá thành công
    
    private void checkAndExtendTime(){
        if (this.endTime == null || this.status == AuctionStatus.FINISHED) return;

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime end = this.endTime;
            Duration duration = Duration.between(now, end);
            long secondsLeft = duration.getSeconds();

            if (secondsLeft > 0 && secondsLeft <= 30) {
                LocalDateTime newEndTime = end.plusSeconds(60);
                this.endTime = newEndTime;
                this.status = AuctionStatus.EXTENDED;
                System.out.println("[HỆ THỐNG]: Tự động gia hạn thêm 60s");
            }
    }   catch (Exception e){
            System.out.println("Lỗi xử lý thời gian: " + e.getMessage());
    }
    // Nếu chưa cài đặt thời gian kết thúc hoặc phiên đã xong thì không cần đến hàm này
    // Lấy thời gian hiện tại và biến chuỗi endtime về định dạng ngày giờ
    // Tính toán khoảng cách thời gian từ lúc bắt đầu đến kết thúc phiên bằng duration(giúp tính chuẩn hơn, đỡ phải đổi đơn vị)
    // Nếu thời gian lớn hơn 0 nhỏ hơn hoặc bằng 30 mà vẫn có người đặt, thì cộng thêm 60 giây, in ra thông báo
    // nếu việc parse thời gian bị lỗi định dạng, in ra thông báo lỗi
}
}

