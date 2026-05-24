package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.models.Admin;
import com.auction.common.models.Bidder;
import com.auction.common.models.Seller;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private Label lblMessage;

    @FXML
    void handleLogin(ActionEvent event) {
        String user = txtUsername.getText().trim();
        String pass = txtPassword.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            lblMessage.setText("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        NetworkClient client = NetworkClient.getInstance();
        if (!client.connect("localhost", 9999)) {
            lblMessage.setText("Không thể kết nối đến Máy chủ!");
            return;
        }

        String response = client.sendRequest("LOGIN|" + user + "|" + pass);
        // Format mong đợi: LOGIN_SUCCESS|ROLE|ID|FULLNAME|EMAIL
        
        if (response.startsWith("LOGIN_SUCCESS")) {
            String[] parts = response.split("\\|");
            String role = parts[1];
            String id = parts[2];
            String fullName = parts[3];
            String email = parts[4];
            
            User loggedInUser = null;
            if ("ADMIN".equalsIgnoreCase(role)) {
                loggedInUser = new Admin(id, user, pass, fullName, email);
            } else if ("SELLER".equalsIgnoreCase(role)) {
                loggedInUser = new Seller(id, user, pass, fullName, email);
            } else if ("BIDDER".equalsIgnoreCase(role)) {
                // Tạm thời set balance là 0, luồng mua bán sẽ load lại balance sau
                loggedInUser = new Bidder(id, user, pass, fullName, email, 0.0);
            }

            UserManager.getInstance().setCurrentUser(loggedInUser);

            if ("ADMIN".equalsIgnoreCase(role)) {
                SceneUtil.changeScene(event, "AdminDashboard.fxml", "Quản trị viên");
            } else {
                SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
            }
        } else {
            // Format lỗi: LOI|Tin_Nhan hoặc LOGIN_FAILED|Tin_Nhan
            String[] parts = response.split("\\|");
            lblMessage.setText(parts.length > 1 ? parts[1] : "Đăng nhập thất bại!");
        }
    }

    @FXML
    void goToRegister(ActionEvent event) {
        SceneUtil.changeScene(event, "Register.fxml", "Đăng ký tài khoản");
    }
}
