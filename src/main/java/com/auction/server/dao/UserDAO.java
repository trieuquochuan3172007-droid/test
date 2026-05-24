package com.auction.server.dao;

import com.auction.common.models.Admin;
import com.auction.common.models.Bidder;
import com.auction.common.models.Seller;
import com.auction.common.models.User;
import com.auction.server.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    public void saveUser(User user) throws SQLException {
        String sql = "INSERT INTO users (id, username, password, full_name, email, role, balance) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE username=?, password=?, full_name=?, email=?, role=?, balance=?";
        Connection conn = DatabaseUtil.getInstance().getConnection();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getPassword());
            stmt.setString(4, user.getFullName());
            stmt.setString(5, user.getEmail());
            stmt.setString(6, user.getRole());
            
            double balance = 0;
            if (user instanceof Bidder) {
                balance = ((Bidder) user).getBalance();
            }
            stmt.setDouble(7, balance);

            stmt.setString(8, user.getUsername());
            stmt.setString(9, user.getPassword());
            stmt.setString(10, user.getFullName());
            stmt.setString(11, user.getEmail());
            stmt.setString(12, user.getRole());
            stmt.setDouble(13, balance);

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

    public User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        Connection conn = DatabaseUtil.getInstance().getConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            rs = stmt.executeQuery();
            if (rs.next()) {
                return mapRowToUser(rs);
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
        return null;
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String username = rs.getString("username");
        String password = rs.getString("password");
        String fullName = rs.getString("full_name");
        String email = rs.getString("email");
        String role = rs.getString("role");
        double balance = rs.getDouble("balance");

        switch (role.toUpperCase()) {
            case "ADMIN":
                return new Admin(id, username, password, fullName, email);
            case "SELLER":
                return new Seller(id, username, password, fullName, email);
            case "BIDDER":
                return new Bidder(id, username, password, fullName, email, balance);
            default:
                throw new SQLException("Unknown role: " + role);
        }
    }
    // Hàm này giúp Trọng tài (AuctionSession) tìm người dùng để trừ/hoàn tiền
    public User findById(String id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        Connection conn = DatabaseUtil.getInstance().getConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, id);
            rs = stmt.executeQuery();
            if (rs.next()) {
                return mapRowToUser(rs);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
        return null;
    }
}
