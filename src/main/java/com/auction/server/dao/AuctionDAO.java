package com.auction.server.dao;

import com.auction.domain.AuctionSession;
import com.auction.domain.AuctionStatus;
import com.auction.domain.BidTransaction;
import com.auction.server.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class AuctionDAO {

    // MySQL trả về định dạng "yyyy-MM-dd HH:mm:ss" (dấu cách, không phải 'T')
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // Thử parse ISO format trước (có chữ 'T')
            return LocalDateTime.parse(raw);
        } catch (Exception e1) {
            try {
                // Parse MySQL format (có dấu cách)
                return LocalDateTime.parse(raw.trim(), MYSQL_DATETIME);
            } catch (Exception e2) {
                System.err.println("[DAO] Không parse được datetime: " + raw);
                return LocalDateTime.now();
            }
        }
    }

    public void saveSession(AuctionSession session) throws SQLException {
        String sql = "INSERT INTO auction_sessions (auction_id, item_id, seller_id, start_time, end_time, status, winner_id, current_highest_bid) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE start_time=?, end_time=?, status=?, winner_id=?, current_highest_bid=?";
        
        Connection conn = DatabaseUtil.getInstance().getConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, session.getAuctionID());
            stmt.setString(2, session.getItemID());
            stmt.setString(3, session.getSellerID());
            
            stmt.setString(4, session.getStartTime() != null ? session.getStartTime().toString() : null); 
            stmt.setString(5, session.getEndTime() != null ? session.getEndTime().toString() : null);
            stmt.setString(6, session.getStatus().name());
            stmt.setString(7, session.getWinnerID());
            stmt.setDouble(8, session.getCurrentHighestBid());

            stmt.setString(9, session.getStartTime() != null ? session.getStartTime().toString() : null);
            stmt.setString(10, session.getEndTime() != null ? session.getEndTime().toString() : null);
            stmt.setString(11, session.getStatus().name());
            stmt.setString(12, session.getWinnerID());
            stmt.setDouble(13, session.getCurrentHighestBid());

            stmt.executeUpdate();
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    System.err.println("Lỗi đóng statement: " + e.getMessage());
                }
            }
            // KHÔNG đóng conn - giữ để dùng lại
        }
    }

    public void saveBidTransaction(BidTransaction tx) throws SQLException {
        String sql = "INSERT INTO bid_transactions (auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?)";
        Connection conn = DatabaseUtil.getInstance().getConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, tx.getAuctionID());
            stmt.setString(2, tx.getBidderID());
            stmt.setDouble(3, tx.getBidAmount());
            stmt.setString(4, tx.getBidTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stmt.executeUpdate();
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    System.err.println("Lỗi đóng statement: " + e.getMessage());
                }
            }
        }
    }

    public java.util.List<com.auction.domain.AuctionSession> getAllSessions() throws SQLException {
        java.util.List<com.auction.domain.AuctionSession> sessions = new java.util.ArrayList<>();
        String sql = "SELECT s.auction_id, s.item_id, i.name AS item_name, s.seller_id, s.start_time, s.end_time, s.status, s.winner_id, s.current_highest_bid " +
                     "FROM auction_sessions s LEFT JOIN items i ON s.item_id = i.id";

        Connection conn = DatabaseUtil.getInstance().getConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            while (rs.next()) {
                String auctionId = rs.getString("auction_id");
                String itemId = rs.getString("item_id");
                String itemName = rs.getString("item_name");
                String sellerId = rs.getString("seller_id");
                java.time.LocalDateTime startTime = parseDateTime(rs.getString("start_time"));
                java.time.LocalDateTime endTime = parseDateTime(rs.getString("end_time"));
                String status = rs.getString("status");
                String winnerId = rs.getString("winner_id");
                double highestBid = rs.getDouble("current_highest_bid");

                com.auction.domain.AuctionSession session = new com.auction.domain.AuctionSession(
                        auctionId,
                        itemId,
                        itemName != null ? itemName : itemId,
                        sellerId,
                        highestBid,
                        startTime,
                        endTime);
                session.setStatus(com.auction.domain.AuctionStatus.valueOf(status));
                session.setWinnerID(winnerId);
                if (highestBid > session.getCurrentHighestBid()) {
                    session.setCurrentHighestBid(highestBid);
                }
                sessions.add(session);
            }
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    System.err.println("Lỗi đóng ResultSet: " + e.getMessage());
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    System.err.println("Lỗi đóng statement: " + e.getMessage());
                }
            }
        }
        return sessions;
    }
}
