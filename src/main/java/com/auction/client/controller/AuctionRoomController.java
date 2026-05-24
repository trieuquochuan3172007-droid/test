package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AuctionRoomController {

    private static String selectedAuctionId;
    private static String selectedAuctionItem;

    @FXML
    private Label lblItemName;

    @FXML
    private Label lblDescription;

    @FXML
    private Label lblCurrentPrice;

    @FXML
    private Label lblTimer;

    @FXML
    private Label lblWinnerTitle;

    @FXML
    private Label lblWinner;

    @FXML
    private TextField txtBidAmount;

    @FXML
    private TextField txtAutoBidMax;

    @FXML
    private ToggleButton toggleAutoBid;

    @FXML
    private LineChart<String, Number> bidChart;

    private XYChart.Series<String, Number> series;
    
    private double currentPrice = 5000000;
    private int secondsRemaining = 3600; // Mô phỏng 1 tiếng
    private Timer timer;

    private boolean isAutoBidEnabled = false;
    private double maxAutoBidAmount = 0.0;

    public static void setSelectedAuction(String auctionId, String auctionItem) {
        selectedAuctionId = auctionId;
        selectedAuctionItem = auctionItem;
    }

    @FXML
    public void initialize() {
        // Gọi NetworkClient thông qua getInstance()
        NetworkClient.getInstance().setListener(message -> {
            String[] parts = message.split("\\|");
            if (parts[0].equals("CAP_NHAT")) {
                // Platform.runLater giúp JavaFX không bị sập
                javafx.application.Platform.runLater(() -> {
                    lblCurrentPrice.setText(parts[2]); // Tên biến của cậu là lblCurrentPrice
                    lblItemName.setText(parts[1]); 
                });
            }
        });
        // Bắt đầu lắng nghe
        NetworkClient.getInstance().startListening();
        // Khởi tạo biểu đồ lịch sử giá (Tính năng nâng cao)
        series = new XYChart.Series<>();
        series.setName("Lịch sử giá");
        bidChart.getData().add(series);

        String itemName = selectedAuctionItem != null ? selectedAuctionItem : "Tranh phong cảnh cổ điển";
        lblItemName.setText(itemName);
        lblDescription.setText("Phiên đấu giá: " + (selectedAuctionId != null ? selectedAuctionId : "#0001") + "\nSản phẩm: " + itemName);

        if (selectedAuctionId != null) {
            loadAuctionDataFromServer(selectedAuctionId);
        } else {
            updatePriceDisplay(currentPrice, "Chưa có");
            addChartData(currentPrice);
            startTimer();
        }
    }

    private void loadAuctionDataFromServer(String auctionId) {
        String response = NetworkClient.getInstance().sendRequest("GET_SESSION|" + auctionId);
        if (!response.startsWith("PHIEN|")) {
            showAlert("Lỗi tải phiên", "Không thể lấy dữ liệu phiên đấu giá từ máy chủ.");
            updatePriceDisplay(currentPrice, "Chưa có");
            addChartData(currentPrice);
            startTimer();
            return;
        }

        Map<String, String> sessionData = parseKeyValueResponse(response);
        currentPrice = parseDouble(sessionData.getOrDefault("gia_hien_tai", String.valueOf(currentPrice)), currentPrice);
        String winner = sessionData.getOrDefault("nguoi_dan_dau", "");
        String status = sessionData.getOrDefault("trang_thai", "OPEN");
        String item = sessionData.getOrDefault("vat_pham", selectedAuctionItem != null ? selectedAuctionItem : "Sản phẩm chưa rõ");
        String endTimeStr = sessionData.getOrDefault("end_time", "");

        lblItemName.setText(item);
        lblDescription.setText("Phiên đấu giá: " + auctionId + "\nSản phẩm: " + item + "\nTrạng thái: " + status);
        updatePriceDisplay(currentPrice, winner.isBlank() ? "Chưa có" : winner);
        addChartData(currentPrice);

        if ("FINISHED".equalsIgnoreCase(status)) {
            secondsRemaining = 0;
            lblTimer.setText("ĐÃ KẾT THÚC");
            if (lblWinnerTitle != null) lblWinnerTitle.setText("NGƯỜI CHIẾN THẮNG");
            txtBidAmount.setDisable(true);
        } else {
            secondsRemaining = parseSecondsUntil(endTimeStr, 3600);
            if (secondsRemaining <= 0) {
                secondsRemaining = 0;
                lblTimer.setText("ĐÃ KẾT THÚC");
                if (lblWinnerTitle != null) lblWinnerTitle.setText("NGƯỜI CHIẾN THẮNG");
                txtBidAmount.setDisable(true);
            } else {
                startTimer();
            }
        }
    }

    private Map<String, String> parseKeyValueResponse(String response) {
        Map<String, String> data = new HashMap<>();
        String[] parts = response.split("\\|");
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            if (kv.length == 2) {
                data.put(kv[0], kv[1]);
            }
        }
        return data;
    }

    private int parseSecondsUntil(String endTimeStr, int defaultSeconds) {
        if (endTimeStr == null || endTimeStr.isBlank()) {
            return defaultSeconds;
        }
        try {
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
            Duration remaining = Duration.between(LocalDateTime.now(), endTime);
            return (int) Math.max(0, remaining.getSeconds());
        } catch (Exception e) {
            return defaultSeconds;
        }
    }

    private double parseDouble(String raw, double defaultValue) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (secondsRemaining > 0) {
                    secondsRemaining--;
                    Platform.runLater(() -> {
                        int hours = secondsRemaining / 3600;
                        int minutes = (secondsRemaining % 3600) / 60;
                        int secs = secondsRemaining % 60;
                        lblTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, secs));
                        
                        if (secondsRemaining <= 30) {
                            lblTimer.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 36px; -fx-font-weight: bold;"); // Chữ đỏ báo hiệu sắp hết giờ
                        }
                    });
                } else {
                    timer.cancel();
                    Platform.runLater(() -> {
                        lblTimer.setText("ĐÃ KẾT THÚC");
                        if (lblWinnerTitle != null) lblWinnerTitle.setText("NGƯỜI CHIẾN THẮNG");
                        txtBidAmount.setDisable(true);
                    });
                }
            }
        }, 1000, 1000);
    }

    @FXML
    void handlePlaceBid(ActionEvent event) {
        if (secondsRemaining <= 0 || selectedAuctionId == null) return;

        try {
            double bidAmount = Double.parseDouble(txtBidAmount.getText());
            if (bidAmount <= currentPrice) {
                showAlert("Giá không hợp lệ", "Bạn phải đặt giá cao hơn giá hiện tại!");
                return;
            }

            String bidderId = UserManager.getInstance().getCurrentUser() != null
                    ? UserManager.getInstance().getCurrentUser().getId()
                    : "anonymous";
            String response = NetworkClient.getInstance().sendRequest(
                    "PLACE_BID|" + selectedAuctionId + "|" + bidderId + "|" + bidAmount);

            if (response.startsWith("CHAP_NHAN|") || response.startsWith("CAP_NHAT|")) {
                Map<String, String> resultData = parseKeyValueResponse(response);
                currentPrice = parseDouble(resultData.getOrDefault("gia_hien_tai", String.valueOf(currentPrice)), currentPrice);
                String winner = resultData.getOrDefault("nguoi_dan_dau", "");
                String status = resultData.getOrDefault("trang_thai", "RUNNING");

                updatePriceDisplay(currentPrice, winner.isBlank() ? "Chưa có" : winner);
                addChartData(currentPrice);

                if ("FINISHED".equalsIgnoreCase(status)) {
                    secondsRemaining = 0;
                    lblTimer.setText("ĐÃ KẾT THÚC");
                    if (lblWinnerTitle != null) lblWinnerTitle.setText("NGƯỜI CHIẾN THẮNG");
                    txtBidAmount.setDisable(true);
                }
                showAlert("Đặt giá thành công", "Bạn đã đặt giá thành công!");
                txtBidAmount.clear();
                
                checkAndTriggerAutoBid(); // Kích hoạt auto-bid nếu có
            } else {
                String message = response.contains("|") ? response.split("\\|", 2)[1] : "Đặt giá không thành công.";
                showAlert("Đặt giá thất bại", message);
            }
        } catch (NumberFormatException e) {
            showAlert("Lỗi nhập liệu", "Vui lòng nhập số tiền hợp lệ!");
        }
    }

    private void updatePriceDisplay(double price, String winner) {
        lblCurrentPrice.setText(String.format("%,.0f VNĐ", price));
        lblWinner.setText(winner);
    }

    private void addChartData(double price) {
        String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        series.getData().add(new XYChart.Data<>(timeStr, price));
    }

    @FXML
    void handleToggleAutoBid(ActionEvent event) {
        if (toggleAutoBid.isSelected()) {
            try {
                maxAutoBidAmount = Double.parseDouble(txtAutoBidMax.getText());
                if (maxAutoBidAmount <= currentPrice) {
                    showAlert("Lỗi Auto-bid", "Mức giá tối đa phải lớn hơn giá hiện tại!");
                    toggleAutoBid.setSelected(false);
                    return;
                }
                isAutoBidEnabled = true;
                toggleAutoBid.setText("Đang bật Auto-bid");
                toggleAutoBid.setStyle("-fx-background-color: #10b981; -fx-text-fill: white;");
                txtAutoBidMax.setDisable(true);
                
                // Kích hoạt thử ngay nếu chưa dẫn đầu
                checkAndTriggerAutoBid();
            } catch (NumberFormatException e) {
                showAlert("Lỗi nhập liệu", "Vui lòng nhập số tiền Auto-bid hợp lệ!");
                toggleAutoBid.setSelected(false);
            }
        } else {
            isAutoBidEnabled = false;
            toggleAutoBid.setText("Bật Auto-bid");
            toggleAutoBid.setStyle("");
            txtAutoBidMax.setDisable(false);
        }
    }

    private void checkAndTriggerAutoBid() {
        if (!isAutoBidEnabled || secondsRemaining <= 0 || selectedAuctionId == null) return;
        
        String currentUserId = UserManager.getInstance().getCurrentUser() != null
                ? UserManager.getInstance().getCurrentUser().getId() : "anonymous";
        String winnerId = lblWinner.getText();
        
        // Nếu không phải người dẫn đầu, tiến hành auto-bid
        if (!winnerId.equals(currentUserId) && !winnerId.equals("Chưa có")) {
            // Giá bid tự động: Giá hiện tại + 100,000
            double nextBid = currentPrice + 100000;
            if (nextBid <= maxAutoBidAmount) {
                String response = NetworkClient.getInstance().sendRequest(
                        "PLACE_BID|" + selectedAuctionId + "|" + currentUserId + "|" + nextBid);
                        
                if (response.startsWith("CHAP_NHAN|") || response.startsWith("CAP_NHAT|")) {
                    Map<String, String> resultData = parseKeyValueResponse(response);
                    currentPrice = parseDouble(resultData.getOrDefault("gia_hien_tai", String.valueOf(currentPrice)), currentPrice);
                    String winner = resultData.getOrDefault("nguoi_dan_dau", "");
                    updatePriceDisplay(currentPrice, winner.isBlank() ? "Chưa có" : winner);
                    addChartData(currentPrice);
                    System.out.println("[Auto-bid] Đã tự động đặt giá: " + nextBid);
                }
            } else {
                // Tự động tắt nếu đã vượt ngưỡng
                isAutoBidEnabled = false;
                Platform.runLater(() -> {
                    toggleAutoBid.setSelected(false);
                    toggleAutoBid.setText("Bật Auto-bid");
                    toggleAutoBid.setStyle("");
                    txtAutoBidMax.setDisable(false);
                    showAlert("Auto-bid kết thúc", "Giá hiện tại đã vượt mức Auto-bid tối đa của bạn.");
                });
            }
        }
    }

    @FXML
    void handleBack(ActionEvent event) {
        if (timer != null) timer.cancel(); // Dừng đồng hồ khi thoát
        SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
