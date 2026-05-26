package com.auction.domain;

/**
 * Trạng thái của một phiên đấu giá.
 *
 * <p>Luồng trạng thái hợp lệ:
 * <pre>
 *   OPEN → RUNNING ⇄ EXTENDED → FINISHED → PAID
 *                                         ↘ CANCELED
 * </pre>
 * </p>
 */
public enum AuctionStatus {

    /** Phiên đã được tạo, chờ đến giờ bắt đầu. */
    OPEN,

    /** Phiên đang hoạt động, người dùng có thể đặt giá. */
    RUNNING,

    /** Phiên được gia hạn tự động (Anti-sniping) khi có bid trong 30 giây cuối. */
    EXTENDED,

    /** Phiên đã kết thúc (hết giờ hoặc đóng thủ công). */
    FINISHED,

    /** Người thắng đã thanh toán — giao dịch hoàn tất. */
    PAID,

    /** Phiên bị hủy (không có bid nào hoặc Admin can thiệp). */
    CANCELED;

    /** Kiểm tra phiên có đang trong trạng thái nhận bid không. */
    public boolean isAcceptingBids() {
        return this == RUNNING || this == EXTENDED;
    }

    /** Kiểm tra phiên đã kết thúc (không thể đặt giá). */
    public boolean isTerminal() {
        return this == FINISHED || this == PAID || this == CANCELED;
    }
}
