package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Map;

/** Controller cho màn Login. */
public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private ProgressIndicator spinLoading;
    @FXML private Button btnLogin;

    @FXML
    private void initialize() {
        lblError.setText("");
        // Enter trên ô password = nhấn nút login
        txtPassword.setOnAction(e -> handleLogin());
    }

    @FXML
    private void handleLogin() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            lblError.setText("Vui lòng nhập đầy đủ thông tin");
            return;
        }

        setLoading(true);

        // Gửi lên server trên thread riêng, không block UI
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                if (!model.isConnected()) model.connect("localhost", 8888);

                model.sendRequest("LOGIN", Map.of(
                        "username", username, "password", password));
                Response res = model.waitForResponse("LOGIN", 5000);

                // Cập nhật UI phải chạy trên JavaFX thread — dùng Platform.runLater
                Platform.runLater(() -> {
                    setLoading(false);
                    if (res != null && res.isSuccess()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) res.getData();
                        model.setUserId(String.valueOf(((Number) data.get("userId")).intValue()));
                        model.setUsername((String) data.get("username"));
                        model.setRole((String) data.get("role"));
                        ClientApp.switchScene("auction_list.fxml");
                    } else {
                        lblError.setText(res != null ? res.getMessage() : "Không phản hồi từ server");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    lblError.setText("Lỗi kết nối: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void goToRegister() {
        ClientApp.switchScene("register.fxml");
    }

    private void setLoading(boolean loading) {
        spinLoading.setVisible(loading);
        btnLogin.setDisable(loading);
        txtUsername.setDisable(loading);
        txtPassword.setDisable(loading);
    }
}
