package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.client.util.PasswordToggle;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.shape.SVGPath;

import java.util.Map;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;
    @FXML private ToggleButton btnTogglePassword;
    @FXML private SVGPath eyeIcon;
    @FXML private Label lblError;
    @FXML private Button btnLogin;

    @FXML
    private void initialize() {
        lblError.setText("");
        PasswordToggle.bind(txtPassword, txtPasswordVisible, btnTogglePassword, eyeIcon);
        txtPassword.setOnAction(e -> handleLogin());
        txtPasswordVisible.setOnAction(e -> handleLogin());
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

        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                if (!model.isConnected()) {
                    model.connect("localhost", 8888);
                }

                model.sendRequest("LOGIN", Map.of(
                        "username", username,
                        "password", password
                ));

                Response res = model.waitForResponse("LOGIN", 5000);

                Platform.runLater(() -> {
                    setLoading(false);

                    if (res != null && res.isSuccess()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) res.getData();

                        model.setUserId(String.valueOf(data.get("userId")));
                        model.setUsername((String) data.get("username"));
                        model.setRole((String) data.get("role"));

                        installAuctionNotifications(model);

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

    private void installAuctionNotifications(ClientModel model) {
        model.setFallbackPushHandler("BID_UPDATE", data -> {
            String bidderId = value(data, "bidderId");

            if (bidderId.equals(model.getUserId())) {
                return;
            }

            Platform.runLater(() -> ClientApp.showInfo(
                    "Bạn đã bị vượt giá trong phiên " + shortAuctionId(data)
                            + "\nNgười dẫn đầu mới: " + value(data, "bidderName")
                            + "\nGiá hiện tại: " + value(data, "amount")
            ));
        });

        model.setFallbackPushHandler("AUCTION_STATUS", data -> Platform.runLater(() ->
                ClientApp.showInfo("Phiên " + shortAuctionId(data)
                        + " đã chuyển trạng thái: " + value(data, "displayStatus"))
        ));

        model.setFallbackPushHandler("AUCTION_EXTENDED", data -> Platform.runLater(() ->
                ClientApp.showInfo("Phiên " + shortAuctionId(data)
                        + " đã được gia hạn thêm " + value(data, "extendedSeconds") + " giây.")
        ));
    }

    private static String value(Map<String, Object> data, String key) {
        Object v = data == null ? null : data.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String shortAuctionId(Map<String, Object> data) {
        String id = value(data, "auctionId");
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private void setLoading(boolean loading) {
        btnLogin.setDisable(loading);
        txtUsername.setDisable(loading);
        txtPassword.setDisable(loading);
    }
}