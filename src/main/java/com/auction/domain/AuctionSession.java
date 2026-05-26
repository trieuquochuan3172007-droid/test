package com.auction.domain;

import com.auction.common.dto.BidResult;
import com.auction.common.exception.InvalidBidException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đại diện cho một phiên đấu giá — <strong>thuần nghiệp vụ, không phụ thuộc DB</strong>.
 *
 * <p>Lớp này chỉ quản lý trạng thái và quy tắc đấu giá.
 * Mọi thao tác I/O (Database, Wallet, Network) đều do {@link AuctionManager} xử lý.</p>
 *
 * <p>Trạng thái hợp lệ: OPEN → RUNNING → (EXTENDED ↔ RUNNING) → FINISHED → PAID / CANCELED</p>
 */
public class AuctionSession {

    // -------------------------------------------------------------------------
    // Hằng số Anti-sniping
    // -------------------------------------------------------------------------
    /** Số giây cuối phiên — nếu có bid trong khoảng này thì gia hạn. */
    private static final long ANTI_SNIPING_THRESHOLD_SECONDS = 30;
    /** Số giây gia hạn thêm khi anti-sniping kích hoạt. */
    private static final long EXTENSION_SECONDS = 60;

    // -------------------------------------------------------------------------
    // Thuộc tính phiên đấu giá
    // -------------------------------------------------------------------------
    private String auctionID;
    private String itemID;
    private String itemName;
    private String sellerID;
    private double currentHighestBid;
    private String currentHighestBidderID;
    private String winnerID;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;
    private List<BidTransaction> bidHistory;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public AuctionSession(String auctionID, String itemID, String sellerID,
                          double startPrice, LocalDateTime startTime, LocalDateTime endTime) {
        this(auctionID, itemID, itemID, sellerID, startPrice, startTime, endTime);
    }

    public AuctionSession(String auctionID, String itemID, String itemName, String sellerID,
                          double startPrice, LocalDateTime startTime, LocalDateTime endTime) {
        this.auctionID              = auctionID;
        this.itemID                 = itemID;
        this.itemName               = (itemName != null && !itemName.isBlank()) ? itemName : itemID;
        this.sellerID               = sellerID;
        this.currentHighestBid      = startPrice;
        this.currentHighestBidderID = null;
        this.winnerID               = null;
        this.startTime              = startTime;
        this.endTime                = endTime;
        this.status                 = AuctionStatus.OPEN;
        this.bidHistory             = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Nghiệp vụ đặt giá — KHÔNG GỌI DB
    // -------------------------------------------------------------------------

    /**
     * Xử lý một lần đặt giá.
     *
     * <p><strong>Thread-safe:</strong> synchronized đảm bảo tại một thời điểm
     * chỉ một thread được thay đổi trạng thái phiên.</p>
     *
     * @param bidderID  ID người đặt giá
     * @param bidAmount Số tiền đặt
     * @return {@link BidResult} thành công hoặc thất bại kèm lý do;
     *         nếu thành công còn mang thông tin người cũ cần hoàn tiền.
     */
    public synchronized BidResult processBid(String bidderID, double bidAmount) {
        if (bidHistory == null) {
            bidHistory = new ArrayList<>();
        }

        // 1. Kiểm tra trạng thái phiên
        if (status != AuctionStatus.RUNNING && status != AuctionStatus.EXTENDED) {
            return BidResult.rejected("Phiên không ở trạng thái RUNNING/EXTENDED (trạng thái hiện tại: " + status + ")");
        }

        // 2. Kiểm tra thời gian
        if (endTime != null && LocalDateTime.now().isAfter(endTime)) {
            status = AuctionStatus.FINISHED;
            return BidResult.rejected("Phiên đấu giá đã hết thời gian");
        }

        // 3. Kiểm tra giá hợp lệ
        if (bidAmount <= 0) {
            return BidResult.rejected("Số tiền đặt giá phải lớn hơn 0");
        }
        if (bidAmount <= currentHighestBid) {
            return BidResult.rejected(String.format(
                    "Giá đặt %.0f phải cao hơn giá hiện tại %.0f", bidAmount, currentHighestBid));
        }

        // 4. Lưu thông tin người cũ để hoàn tiền
        String previousBidderID = this.currentHighestBidderID;
        double previousBidAmount = this.currentHighestBid;

        // 5. Cập nhật trạng thái phiên
        this.currentHighestBid      = bidAmount;
        this.currentHighestBidderID = bidderID;
        this.winnerID               = bidderID;

        // 6. Ghi lịch sử giao dịch
        BidTransaction transaction = new BidTransaction(auctionID, bidderID, bidAmount, LocalDateTime.now());
        bidHistory.add(transaction);

        // 7. Kiểm tra gia hạn Anti-sniping
        checkAndExtendTime();

        System.out.printf("[PHIÊN %s] Bidder %s đặt giá %.0f — thành công%n",
                auctionID, bidderID, bidAmount);

        return BidResult.accepted(transaction, previousBidderID, previousBidAmount);
    }

    /**
     * Anti-sniping: Nếu bid mới xuất hiện trong {@value #ANTI_SNIPING_THRESHOLD_SECONDS} giây cuối,
     * tự động gia hạn thêm {@value #EXTENSION_SECONDS} giây.
     */
    private void checkAndExtendTime() {
        if (endTime == null || status == AuctionStatus.FINISHED) return;

        long secondsLeft = Duration.between(LocalDateTime.now(), endTime).getSeconds();
        if (secondsLeft > 0 && secondsLeft <= ANTI_SNIPING_THRESHOLD_SECONDS) {
            endTime = endTime.plusSeconds(EXTENSION_SECONDS);
            status  = AuctionStatus.EXTENDED;
            System.out.printf("[PHIÊN %s] Anti-sniping kích hoạt — gia hạn thêm %ds%n",
                    auctionID, EXTENSION_SECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------
    public String getAuctionID()   { return auctionID; }
    public void   setAuctionID(String auctionID) { this.auctionID = auctionID; }

    public String getItemID()   { return itemID; }
    public void   setItemID(String itemID) { this.itemID = itemID; }

    public String getItemName() { return itemName; }
    public void   setItemName(String itemName) { this.itemName = itemName; }

    /** Ưu tiên trả tên sản phẩm; nếu không có tên thì trả ID. */
    public String getDisplayItem() {
        return (itemName != null && !itemName.isBlank()) ? itemName : itemID;
    }

    public String getSellerID()   { return sellerID; }
    public void   setSellerID(String sellerID) { this.sellerID = sellerID; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void   setCurrentHighestBid(double bid) { this.currentHighestBid = bid; }

    public String getCurrentHighestBidderID() { return currentHighestBidderID; }
    public void   setCurrentHighestBidderID(String id) { this.currentHighestBidderID = id; }

    public String getWinnerID()   { return winnerID; }
    public void   setWinnerID(String winnerID) { this.winnerID = winnerID; }

    public LocalDateTime getStartTime() { return startTime; }
    public void          setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void          setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void          setStatus(AuctionStatus status) { this.status = status; }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }
}
