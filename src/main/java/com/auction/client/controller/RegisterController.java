package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class RegisterController {

    @FXML private TextField txtFullName;
    @FXML private TextField txtUsername;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtBalance;
    @FXML private Label lblError;
    @FXML private Button btnRegister;

    @FXML
    private void initialize() {
        lblError.setText("");
        txtPassword.setOnAction(e -> handleRegister());
    }

    @FXML
    private void handleRegister() {
        String fullName = safeText(txtFullName);
        String username = safeText(txtUsername);
        String email = safeText(txtEmail);
        String password = txtPassword.getText();
        String balanceText = safeText(txtBalance);

        if (fullName.isEmpty() || username.isEmpty()
                || email.isEmpty() || password.isEmpty()) {
            lblError.setText("Vui lòng nhập đầy đủ thông tin bắt buộc");
            return;
        }
        if (password.length() < 6) {
            lblError.setText("Mật khẩu phải ít nhất 6 ký tự");
            return;
        }

        if (!balanceText.isEmpty()) {
            try {
                if (new BigDecimal(balanceText).signum() < 0) {
                    lblError.setText("Số dư không được âm");
                    return;
                }
            } catch (NumberFormatException e) {
                lblError.setText("Số dư phải là số");
                return;
            }
        }

        btnRegister.setDisable(true);

        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                if (!model.isConnected()) model.connect("localhost", 8888);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("username", username);
                payload.put("password", password);
                payload.put("email", email);
                payload.put("fullName", fullName);
                if (!balanceText.isEmpty()) {

                    payload.put("initialBalance", balanceText);
                }
                model.sendRequest("REGISTER", payload);
                Response res = model.waitForResponse("REGISTER", 5000);

                Platform.runLater(() -> {
                    btnRegister.setDisable(false);
                    if (res != null && res.isSuccess()) {
                        ClientApp.showInfo("Đăng ký thành công! Hãy đăng nhập.");
                        ClientApp.switchScene("login.fxml");
                    } else {
                        lblError.setText(res != null ? res.getMessage() : "Không phản hồi từ server");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnRegister.setDisable(false);
                    lblError.setText("Lỗi kết nối: " + e.getMessage());
                });
            }
        }, "register-worker").start();
    }

    @FXML
    private void goToLogin() {
        ClientApp.switchScene("login.fxml");
    }

    private static String safeText(TextField f) {
        return f.getText() == null ? "" : f.getText().trim();
    }
}
