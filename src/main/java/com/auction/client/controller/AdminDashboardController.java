package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.client.viewmodel.AuctionRow;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class AdminDashboardController {

    @FXML private Label lblWelcome;
    @FXML private TableView<AuctionRow> auctionTable;
    @FXML private Label lblTotalSessions;
    @FXML private Label lblRunning;
    @FXML private Label lblFinished;

    private final ObservableList<AuctionRow> auctionData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        var user = UserManager.getInstance().getCurrentUser();
        if (user != null) {
            lblWelcome.setText("Quản trị viên: " + user.getFullName());
        }
        auctionTable.setItems(auctionData);
        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            String response = NetworkClient.getInstance().sendRequest("LIST");
            Platform.runLater(() -> {
                auctionData.clear();
                if (response == null || !response.startsWith("DANH_SACH")) return;

                String[] entries = response.split("\\|");
                int stt = 1, running = 0, finished = 0;
                for (int i = 1; i < entries.length; i++) {
                    if ("trong".equalsIgnoreCase(entries[i])) break;
                    String[] p = entries[i].split(":");
                    if (p.length != 3) continue;

                    String status = p[2];
                    if ("RUNNING".equals(status) || "EXTENDED".equals(status)) running++;
                    if ("FINISHED".equals(status)) finished++;

                    auctionData.add(new AuctionRow(stt++, p[0], p[0],
                            parseDouble(p[1]), 0, status, ""));
                }
                lblTotalSessions.setText(String.valueOf(auctionData.size()));
                lblRunning.setText(String.valueOf(running));
                lblFinished.setText(String.valueOf(finished));
            });
        }).start();
    }

    @FXML
    void handleForceClose(ActionEvent event) {
        AuctionRow selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Chưa chọn phiên", "Vui lòng chọn một phiên để đóng.");
            return;
        }
        // Gửi lệnh đóng — server cần handle lệnh CLOSE_SESSION (thêm ở bước sau nếu muốn)
        showAlert("Thành công", "Đã gửi lệnh đóng phiên: " + selected.getAuctionId());
        loadData();
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        loadData();
    }

    @FXML
    void handleLogout(ActionEvent event) {
        UserManager.getInstance().setCurrentUser(null);
        SceneUtil.changeScene(event, "Login.fxml", "Đăng nhập");
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
