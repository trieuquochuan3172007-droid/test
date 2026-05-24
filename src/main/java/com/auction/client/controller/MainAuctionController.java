package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.client.viewmodel.AuctionRow;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

public class MainAuctionController {

    @FXML
    private Label lblWelcome;

    @FXML
    private TableView<AuctionRow> auctionTable;

    @FXML
    private Button btnCreateAuction;

    @FXML
    private Button btnRefresh;

    private final ObservableList<AuctionRow> auctionData = FXCollections.observableArrayList();
    private AuctionRow selectedAuction;

    @FXML
    public void initialize() {
        try {
            User currentUser = UserManager.getInstance().getCurrentUser();
            if (currentUser != null) {
                lblWelcome.setText("Xin chào, " + currentUser.getFullName() + " | Vai trò: " + currentUser.getClass().getSimpleName());
                
                // Nếu không phải là Seller thì ẩn nút Tạo phiên đấu giá
                if (!currentUser.getClass().getSimpleName().equalsIgnoreCase("Seller")) {
                    btnCreateAuction.setVisible(false);
                    btnCreateAuction.setManaged(false);
                }
            }

            // Setup TableView
            auctionTable.setItems(auctionData);
            auctionTable.setOnMouseClicked(e -> {
                selectedAuction = auctionTable.getSelectionModel().getSelectedItem();
            });

            // Load data trong background thread để không block UI
            loadAuctionDataAsync();
        } catch (Exception e) {
            System.err.println("[ERROR] Initialize lỗi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadAuctionDataAsync() {
        new Thread(() -> {
            try {
                loadAuctionData();
            } catch (Exception e) {
                System.err.println("[ERROR] Load auction data lỗi: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.WARNING, "Lỗi tải dữ liệu", "Không thể tải danh sách phiên: " + e.getMessage());
                });
            }
        }).start();
    }

    private void loadAuctionData() {
        auctionData.clear();
        String response = NetworkClient.getInstance().sendRequest("LIST");
        if (response == null || !response.startsWith("DANH_SACH")) {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.WARNING, "Không tải được dữ liệu", "Không thể lấy danh sách phiên từ máy chủ.");
            });
            return;
        }

        String[] entries = response.split("\\|");
        int stt = 1;
        for (int i = 1; i < entries.length; i++) {
            if ("trong".equalsIgnoreCase(entries[i])) {
                return;
            }
            String[] parts = entries[i].split(":");
            if (parts.length != 3) {
                continue;
            }

            String auctionId = parts[0];
            double currentPrice = parseDouble(parts[1]);
            String status = parts[2];

            // Lấy thêm thông tin chi tiết từ server
            String detailResponse = NetworkClient.getInstance().sendRequest("GET_SESSION|" + auctionId);
            String itemName = auctionId;
            int participantCount = 0;
            String timeRemaining = "00:00:00";

            if (detailResponse != null && detailResponse.startsWith("PHIEN|")) {
                String[] details = detailResponse.split("\\|");
                for (int j = 1; j < details.length; j++) {
                    String[] kv = details[j].split("=", 2);
                    if (kv.length == 2) {
                        if ("vat_pham".equals(kv[0])) itemName = kv[1];
                        else if ("end_time".equals(kv[0])) timeRemaining = calculateTimeRemaining(kv[1]);
                    }
                }
            }

            AuctionRow row = new AuctionRow(stt++, auctionId, itemName, currentPrice, participantCount, status, timeRemaining);
            auctionData.add(row);
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String calculateTimeRemaining(String endTimeStr) {
        try {
            java.time.LocalDateTime endTime = java.time.LocalDateTime.parse(endTimeStr);
            java.time.Duration remaining = java.time.Duration.between(java.time.LocalDateTime.now(), endTime);
            long seconds = remaining.getSeconds();
            if (seconds <= 0) {
                return "Đã kết thúc";
            }
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } catch (Exception e) {
            return "00:00:00";
        }
    }

    @FXML
    void handleLogout(ActionEvent event) {
        UserManager.getInstance().setCurrentUser(null);
        SceneUtil.changeScene(event, "Login.fxml", "Đăng nhập");
    }

    @FXML
    void handleCreateAuction(ActionEvent event) {
        SceneUtil.changeScene(event, "CreateAuction.fxml", "Tạo phiên đấu giá mới");
    }

    @FXML
    void handleJoinAuction(ActionEvent event) {
        if (selectedAuction != null) {
            AuctionRoomController.setSelectedAuction(selectedAuction.getAuctionId(), selectedAuction.getItemName());
            SceneUtil.changeScene(event, "AuctionRoom.fxml", "Phòng đấu giá: " + selectedAuction.getItemName());
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Chưa chọn phiên", "Vui lòng chọn một phiên đấu giá để tham gia!");
        }
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        loadAuctionDataAsync();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}


