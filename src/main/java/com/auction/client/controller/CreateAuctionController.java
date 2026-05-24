package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class CreateAuctionController {

    @FXML
    private TextField txtItemName;

    @FXML
    private ComboBox<String> cbCategory;

    @FXML
    private TextArea txtDescription;

    @FXML
    private TextField txtStartPrice;

    @FXML
    private TextField txtDuration; // Nhập số phút

    @FXML
    public void initialize() {
        cbCategory.getItems().addAll("Điện tử (Electronics)", "Nghệ thuật (Art)", "Phương tiện (Vehicle)", "Thời trang (Fashion)", "Khác");
        cbCategory.getSelectionModel().selectFirst();
    }

    @FXML
    void handleCreate(ActionEvent event) {
        String name = txtItemName.getText().trim();
        String priceStr = txtStartPrice.getText().trim();
        String durationStr = txtDuration.getText().trim();

        if (name.isEmpty() || priceStr.isEmpty() || durationStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập đầy đủ Tên, Giá khởi điểm và Thời gian!");
            return;
        }

        try {
            double price = Double.parseDouble(priceStr);
            int duration = Integer.parseInt(durationStr);

            // Lấy thông tin người dùng hiện tại
            User currentUser = UserManager.getInstance().getCurrentUser();
            String sellerId = (currentUser != null) ? currentUser.getId() : "UNKNOWN";

            // Tạo ID duy nhất cho phiên và item
            String auctionId = "AUC" + System.currentTimeMillis();
            String itemId    = "ITEM" + System.currentTimeMillis();

            // Gửi request lên Server để tạo phiên đấu giá
            // Định dạng: CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|durationMinutes
            String request = "CREATE_AUCTION|" + auctionId + "|" + itemId + "|" + name
                           + "|" + sellerId + "|" + price + "|" + duration;

            String response = NetworkClient.getInstance().sendRequest(request);

            if (response != null && response.startsWith("CREATE_AUCTION_SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Đã tạo phiên đấu giá thành công!\nMã phiên: " + auctionId);
                SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
            } else {
                String errMsg = (response != null && response.contains("|"))
                        ? response.split("\\|", 2)[1]
                        : (response != null ? response : "Không nhận được phản hồi từ server");
                showAlert(Alert.AlertType.ERROR, "Lỗi tạo phiên", errMsg);
            }

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi định dạng", "Giá và Thời gian phải là số hợp lệ!");
        }
    }

    @FXML
    void handleCancel(ActionEvent event) {
        SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
