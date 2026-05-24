package com.auction.server.dao;

import com.auction.common.models.*;
import com.auction.server.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    public void saveItem(Item item) throws SQLException {
        String sql = "INSERT INTO items (id, name, description, init_price, category) VALUES (?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name=?, description=?, init_price=?, category=?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getId());
            stmt.setString(2, item.getName());
            stmt.setString(3, item.getDescription());
            stmt.setDouble(4, item.getInitPrice());
            stmt.setString(5, item.getCategory());

            stmt.setString(6, item.getName());
            stmt.setString(7, item.getDescription());
            stmt.setDouble(8, item.getInitPrice());
            stmt.setString(9, item.getCategory());

            stmt.executeUpdate();
        }
    }

    public Item findById(String id) throws SQLException {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToItem(rs);
                }
            }
        }
        return null;
    }

    public List<Item> getAllItems() throws SQLException {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                items.add(mapRowToItem(rs));
            }
        }
        return items;
    }

    private Item mapRowToItem(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        double initPrice = rs.getDouble("init_price");
        String category = rs.getString("category");

        // Factory Pattern nên được áp dụng ở đây, tạm thời dùng Switch-Case
        switch (category.toUpperCase()) {
            case "ART":
                return new Art(id, name, description, initPrice, "Unknown Artist", 0, "Unknown Technique");
            case "ELECTRONICS":
                return new Electronics(id, name, description, initPrice, "Unknown Brand", "Unknown Model", "Unknown Serial", "Unknown Warranty");
            case "FASHION":
                return new Fashion(id, name, description, initPrice, "Unknown Brand", "Unknown Size", "Unknown Material");
            case "FURNITURE":
                return new Furniture(id, name, description, initPrice, "Unknown Material", "Unknown Dimensions");
            case "VEHICLE":
                return new Vehicle(id, name, description, initPrice, "Unknown Brand", "Unknown Model", 0);
            default:
                // Nếu không có class con cụ thể, cần một Item implementation chung
                // Tạm thời ném lỗi hoặc trả về subclass mặc định
                return new Electronics(id, name, description, initPrice, "Unknown", "Unknown", "Unknown", "Unknown");
        }
    }
}
