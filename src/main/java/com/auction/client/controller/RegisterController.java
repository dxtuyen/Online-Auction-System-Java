package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.client.util.MoneyInputFormatter;
import com.auction.client.util.PasswordToggle;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.shape.SVGPath;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller cho màn Đăng ký.
 *
 * <p>{@code txtBalance} optional: bỏ trống → server set balance = 0; có giá trị thì gửi
 * dạng String để giữ precision (BigDecimal).</p>
 */
public class RegisterController {

    @FXML private TextField txtFullName;
    @FXML private TextField txtUsername;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;
    @FXML private ToggleButton btnTogglePassword;
    @FXML private SVGPath eyeIcon;
    @FXML private TextField txtBalance;
    @FXML private Label lblError;
    @FXML private Button btnRegister;

    @FXML
    private void initialize() {
        lblError.setText("");
        PasswordToggle.bind(txtPassword, txtPasswordVisible, btnTogglePassword, eyeIcon);
        MoneyInputFormatter.apply(txtBalance);
        txtPassword.setOnAction(e -> handleRegister());
        txtPasswordVisible.setOnAction(e -> handleRegister());
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
        // Validate balance format nếu có nhập — server chưa nhận field này
        // (default = 0) nhưng vẫn check để feedback sớm cho user.
        // Strip dấu phẩy do MoneyInputFormatter chèn trước khi parse.
        String balanceRaw = balanceText.replace(",", "");
        if (!balanceRaw.isEmpty()) {
            try {
                if (new BigDecimal(balanceRaw).signum() < 0) {
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
                if (!balanceRaw.isEmpty()) {
                    // Gửi String đã strip dấu phẩy để server parse BigDecimal — giữ precision.
                    payload.put("initialBalance", balanceRaw);
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
