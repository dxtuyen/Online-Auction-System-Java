package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Map;

public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private ComboBox<String> cboRole;
    @FXML private TextField txtExtra;
    @FXML private Label lblError;
    @FXML private Button btnRegister;

    @FXML
    private void initialize() {
        lblError.setText("");
        cboRole.setValue("BIDDER");
        txtExtra.setText("10000000");
    }

    @FXML
    private void handleRegister() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        String role = cboRole.getValue();
        double extra;
        try { extra = Double.parseDouble(txtExtra.getText().trim().replace(",", "")); }
        catch (NumberFormatException e) { extra = 0; }

        if (username.isEmpty() || password.isEmpty() || role == null) {
            lblError.setText("Nhập đầy đủ thông tin");
            return;
        }

        btnRegister.setDisable(true);
        double finalExtra = extra;

        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                if (!model.isConnected()) model.connect("localhost", 8888);

                model.sendRequest("REGISTER", Map.of(
                        "username", username, "password", password,
                        "role", role, "extra", finalExtra));
                Response res = model.waitForResponse("REGISTER", 5000);

                Platform.runLater(() -> {
                    btnRegister.setDisable(false);
                    if (res != null && res.isSuccess()) {
                        ClientApp.showInfo("Đăng ký thành công! Hãy đăng nhập.");
                        ClientApp.switchScene("login.fxml");
                    } else {
                        lblError.setText(res != null ? res.getMessage() : "Lỗi đăng ký");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnRegister.setDisable(false);
                    lblError.setText("Lỗi: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void goToLogin() { ClientApp.switchScene("login.fxml"); }
}
