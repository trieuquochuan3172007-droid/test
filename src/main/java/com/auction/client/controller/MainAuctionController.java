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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controller màn hình danh sách phiên đấu giá.
 *
 * <p>Dùng lệnh <code>LIST_DETAIL</code> để lấy toàn bộ thông tin phiên
 * trong <strong>một lần gọi mạng duy nhất</strong>, thay vì N+1 calls như trước.</p>
 *
 * <p>Đồng hồ đếm ngược trong bảng được cập nhật <strong>mỗi giây</strong>
 * qua một Timer daemon — không cần reload lại từ server.</p>
 */
public class MainAuctionController {

    @FXML private Label               lblWelcome;
    @FXML private TableView<AuctionRow> auctionTable;
    @FXML private Button              btnCreateAuction;
    @FXML private Button              btnRefresh;

    private final ObservableList<AuctionRow> auctionData = FXCollections.observableArrayList();
    private AuctionRow selectedAuction;

    /** Timer cập nhật cột "Thời gian còn lại" mỗi giây. */
    private Timer liveCountdownTimer;

    @FXML
    public void initialize() {
        try {
            User currentUser = UserManager.getInstance().getCurrentUser();
            if (currentUser != null) {
                lblWelcome.setText("Xin chào, " + currentUser.getFullName()
                        + " | Vai trò: " + currentUser.getClass().getSimpleName());

                // Ẩn nút Tạo phiên với Bidder và Admin
                boolean isSeller = "Seller".equalsIgnoreCase(currentUser.getClass().getSimpleName());
                btnCreateAuction.setVisible(isSeller);
                btnCreateAuction.setManaged(isSeller);
            }

            auctionTable.setItems(auctionData);
            auctionTable.setOnMouseClicked(e ->
                    selectedAuction = auctionTable.getSelectionModel().getSelectedItem());

            loadAuctionDataAsync();

        } catch (Exception e) {
            System.err.println("[MainController] Initialize lỗi: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Load dữ liệu
    // -------------------------------------------------------------------------

    private void loadAuctionDataAsync() {
        Thread loadThread = new Thread(() -> {
            try {
                loadAuctionData();
            } catch (Exception e) {
                System.err.println("[MainController] Lỗi tải dữ liệu: " + e.getMessage());
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.WARNING, "Lỗi tải dữ liệu",
                                "Không thể tải danh sách phiên: " + e.getMessage()));
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * Tải danh sách phiên đấu giá từ server bằng <strong>một lần gọi duy nhất</strong> (LIST_DETAIL).
     *
     * <p>Format server trả về:
     * {@code DANH_SACH_CHI_TIET|id:itemName:price:status:endTime|...}</p>
     */
    private void loadAuctionData() {
        String response = NetworkClient.getInstance().sendRequest("LIST_DETAIL");

        if (response == null || !response.startsWith("DANH_SACH_CHI_TIET")) {
            Platform.runLater(() ->
                    showAlert(Alert.AlertType.WARNING, "Không tải được dữ liệu",
                            "Không thể lấy danh sách phiên từ máy chủ."));
            return;
        }

        ObservableList<AuctionRow> rows = FXCollections.observableArrayList();
        String[] entries = response.split("\\|");
        int stt = 1;

        for (int i = 1; i < entries.length; i++) {
            if ("trong".equalsIgnoreCase(entries[i])) break;

            // Format: id:itemName:price:status:endTime
            String[] parts = entries[i].split(":", 5);
            if (parts.length < 5) continue;

            String auctionId     = parts[0];
            String itemName      = parts[1];
            double currentPrice  = parseDouble(parts[2]);
            String status        = parts[3];
            String endTimeStr    = parts[4];          // ISO datetime gốc — dùng cho live timer
            String timeRemaining = calculateTimeRemaining(endTimeStr); // hiển thị lần đầu

            rows.add(new AuctionRow(stt++, auctionId, itemName, currentPrice,
                    0, status, endTimeStr, timeRemaining));
        }

        Platform.runLater(() -> {
            auctionData.setAll(rows);
            startLiveCountdown();   // Khởi động timer đếm ngược realtime
        });
    }

    // -------------------------------------------------------------------------
    // Live countdown timer — cập nhật cột "Thời gian còn lại" mỗi giây
    // -------------------------------------------------------------------------

    /**
     * Bắt đầu Timer daemon cập nhật cột thời gian còn lại mỗi giây.
     * Huỷ timer cũ (nếu có) trước khi tạo timer mới.
     */
    private void startLiveCountdown() {
        if (liveCountdownTimer != null) {
            liveCountdownTimer.cancel();
        }
        liveCountdownTimer = new Timer("main-auction-countdown", true); // daemon
        liveCountdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    boolean allDone = true;
                    for (AuctionRow row : auctionData) {
                        String endTimeStr = row.getEndTimeStr();
                        if (endTimeStr == null || endTimeStr.isBlank()) continue;

                        String display = calculateTimeRemaining(endTimeStr);
                        row.timeRemainingProperty().set(display);

                        if (!"Đã kết thúc".equals(display)) {
                            allDone = false;
                        }
                    }
                    // Nếu tất cả đã kết thúc thì huỷ timer — không cần chạy thêm
                    if (allDone && !auctionData.isEmpty()) {
                        liveCountdownTimer.cancel();
                    }
                });
            }
        }, 1000, 1000);
    }

    // -------------------------------------------------------------------------
    // Event Handlers
    // -------------------------------------------------------------------------

    @FXML
    void handleLogout(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        UserManager.getInstance().setCurrentUser(null);
        SceneUtil.changeScene(event, "Login.fxml", "Đăng nhập");
    }

    @FXML
    void handleCreateAuction(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        SceneUtil.changeScene(event, "CreateAuction.fxml", "Tạo phiên đấu giá mới");
    }

    @FXML
    void handleJoinAuction(ActionEvent event) {
        if (selectedAuction == null) {
            showAlert(Alert.AlertType.INFORMATION, "Chưa chọn phiên",
                    "Vui lòng chọn một phiên đấu giá để tham gia!");
            return;
        }
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        AuctionRoomController.setSelectedAuction(
                selectedAuction.getAuctionId(), selectedAuction.getItemName());
        SceneUtil.changeScene(event, "AuctionRoom.fxml",
                "Phòng đấu giá: " + selectedAuction.getItemName());
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        loadAuctionDataAsync();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private double parseDouble(String value) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private String calculateTimeRemaining(String endTimeStr) {
        if (endTimeStr == null || endTimeStr.isBlank()) return "00:00:00";
        try {
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
            long seconds = Duration.between(LocalDateTime.now(), endTime).getSeconds();
            if (seconds <= 0) return "Đã kết thúc";
            return String.format("%02d:%02d:%02d",
                    seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        } catch (Exception e) {
            return "00:00:00";
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
