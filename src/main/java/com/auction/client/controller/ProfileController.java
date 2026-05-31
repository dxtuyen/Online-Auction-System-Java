package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.client.util.ImageCacheService;
import com.auction.client.util.PasswordToggle;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller cho trang Profile.
 *
 * <p>Hiển thị thông tin tài khoản + form sửa fullName/email + form đổi mật khẩu.
 * Đổi mật khẩu yêu cầu nhập đúng mật khẩu cũ trước khi đặt mật khẩu mới.</p>
 */
public class ProfileController {

    @FXML private Label lblUsername;
    @FXML private Label lblRole;
    @FXML private Label lblStatus;
    @FXML private Label lblBalance;
    @FXML private Label lblRevenue;

    @FXML private ImageView imgAvatar;
    @FXML private Circle avatarMask;
    @FXML private Label lblAvatarMsg;
    private String currentAvatarUrl;

    @FXML private TextField txtFullName;
    @FXML private TextField txtEmail;
    @FXML private Label lblProfileMsg;
    @FXML private Button btnSaveProfile;

    @FXML private PasswordField txtOldPassword;
    @FXML private TextField txtOldPasswordVisible;
    @FXML private ToggleButton btnToggleOld;
    @FXML private SVGPath eyeIconOld;

    @FXML private PasswordField txtNewPassword;
    @FXML private TextField txtNewPasswordVisible;
    @FXML private ToggleButton btnToggleNew;
    @FXML private SVGPath eyeIconNew;

    @FXML private PasswordField txtConfirmPassword;
    @FXML private TextField txtConfirmPasswordVisible;
    @FXML private ToggleButton btnToggleConfirm;
    @FXML private SVGPath eyeIconConfirm;

    @FXML private Label lblPasswordMsg;
    @FXML private Button btnChangePassword;

    /** Có quay được về dashboard hay phải về auction_list — phụ thuộc nơi mở. */
    private String backFxml = "auction_list.fxml";

    @FXML
    private void initialize() {
        lblProfileMsg.setText("");
        lblPasswordMsg.setText("");
        lblAvatarMsg.setText("");
        // Clip avatar tròn — ImageView vuông, dùng Circle làm clip.
        imgAvatar.setClip(new Circle(60, 60, 60));
        PasswordToggle.bind(txtOldPassword, txtOldPasswordVisible, btnToggleOld, eyeIconOld);
        PasswordToggle.bind(txtNewPassword, txtNewPasswordVisible, btnToggleNew, eyeIconNew);
        PasswordToggle.bind(txtConfirmPassword, txtConfirmPasswordVisible, btnToggleConfirm, eyeIconConfirm);
        loadProfile();
    }

    /** Caller (AuctionList hoặc SellerDashboard) gọi sau khi load FXML để biết quay về đâu. */
    public void setBackFxml(String fxml) {
        this.backFxml = fxml;
    }

    private void loadProfile() {
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest("GET_PROFILE", Map.of());
                Response res = model.waitForResponse("GET_PROFILE", 5000);
                if (res != null && res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) res.getData();
                    Platform.runLater(() -> fillProfile(data));
                } else if (res != null) {
                    Platform.runLater(() -> ClientApp.showError(res.getMessage()));
                }
            } catch (Exception e) {
                Platform.runLater(() -> ClientApp.showError(
                        "Không tải được thông tin tài khoản: " + e.getMessage()));
            }
        }, "profile-load").start();
    }

    private void fillProfile(Map<String, Object> data) {
        lblUsername.setText(s(data, "username"));
        lblRole.setText(s(data, "displayRole"));
        lblStatus.setText(s(data, "displayStatus"));
        lblBalance.setText(money(data.get("balance")));
        lblRevenue.setText(money(data.get("revenue")));
        txtFullName.setText(s(data, "fullName"));
        txtEmail.setText(s(data, "email"));

        String avatarUrl = s(data, "avatarUrl");
        currentAvatarUrl = avatarUrl.isBlank() ? null : avatarUrl;
        if (currentAvatarUrl != null) {
            ImageCacheService.getInstance().loadAsync(currentAvatarUrl, imgAvatar::setImage);
        } else {
            imgAvatar.setImage(null);
        }
    }

    @FXML
    private void handleChangeAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh đại diện");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Ảnh (jpg, png, gif, webp)", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"));
        File file = chooser.showOpenDialog(imgAvatar.getScene().getWindow());
        if (file == null) return;

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            setAvatarMsg("Không đọc được file: " + e.getMessage(), true);
            return;
        }
        if (bytes.length > 3 * 1024 * 1024) {
            setAvatarMsg("File vượt quá 3MB", true);
            return;
        }
        setAvatarMsg("Đang tải lên...", false);

        String base64 = Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileName", file.getName());
        data.put("dataBase64", base64);

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            model.sendRequest("UPLOAD_AVATAR", data);
            Response res = model.waitForResponse("UPLOAD_AVATAR", 10000);
            Platform.runLater(() -> {
                if (res != null && res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) res.getData();
                    String url = body == null ? null : (String) body.get("avatarUrl");
                    if (url != null) {
                        // Hiển thị ngay từ file vừa chọn — đỡ phải round-trip GET_IMAGE.
                        ImageCacheService.getInstance().invalidate(currentAvatarUrl);
                        currentAvatarUrl = url;
                        imgAvatar.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
                    }
                    setAvatarMsg("✓ " + res.getMessage(), false);
                } else {
                    setAvatarMsg(res != null ? res.getMessage() : "Không phản hồi từ server", true);
                }
            });
        }, "avatar-upload").start();
    }

    private void setAvatarMsg(String msg, boolean error) {
        lblAvatarMsg.setStyle(error ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #059669;");
        lblAvatarMsg.setText(msg);
    }

    @FXML
    private void handleSaveProfile() {
        String fullName = txtFullName.getText() == null ? "" : txtFullName.getText().trim();
        String email = txtEmail.getText() == null ? "" : txtEmail.getText().trim();
        if (fullName.isEmpty() || email.isEmpty()) {
            setProfileMsg("Họ tên và email không được rỗng", true);
            return;
        }

        btnSaveProfile.setDisable(true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullName", fullName);
        data.put("email", email);

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            model.sendRequest("UPDATE_PROFILE", data);
            Response res = model.waitForResponse("UPDATE_PROFILE", 5000);
            Platform.runLater(() -> {
                btnSaveProfile.setDisable(false);
                if (res != null && res.isSuccess()) {
                    setProfileMsg("✓ " + res.getMessage(), false);
                    loadProfile();
                } else {
                    setProfileMsg(res != null ? res.getMessage() : "Không phản hồi từ server", true);
                }
            });
        }, "profile-save").start();
    }

    @FXML
    private void handleChangePassword() {
        String oldPwd = txtOldPassword.getText();
        String newPwd = txtNewPassword.getText();
        String confirmPwd = txtConfirmPassword.getText();

        if (oldPwd == null || oldPwd.isEmpty()) {
            setPasswordMsg("Vui lòng nhập mật khẩu cũ", true);
            return;
        }
        if (newPwd == null || newPwd.length() < 6) {
            setPasswordMsg("Mật khẩu mới phải >= 6 ký tự", true);
            return;
        }
        if (!newPwd.equals(confirmPwd)) {
            setPasswordMsg("Xác nhận mật khẩu không khớp", true);
            return;
        }

        btnChangePassword.setDisable(true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("oldPassword", oldPwd);
        data.put("newPassword", newPwd);

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            model.sendRequest("CHANGE_PASSWORD", data);
            Response res = model.waitForResponse("CHANGE_PASSWORD", 5000);
            Platform.runLater(() -> {
                btnChangePassword.setDisable(false);
                if (res != null && res.isSuccess()) {
                    setPasswordMsg("✓ " + res.getMessage(), false);
                    txtOldPassword.clear();
                    txtNewPassword.clear();
                    txtConfirmPassword.clear();
                } else {
                    setPasswordMsg(res != null ? res.getMessage() : "Không phản hồi từ server", true);
                }
            });
        }, "password-change").start();
    }

    @FXML
    private void goBack() {
        ClientApp.switchScene(backFxml);
    }

    @FXML
    private void handleLogout() {
        new Thread(() -> {
            ClientModel.getInstance().logoutAndDisconnect();
            Platform.runLater(() -> ClientApp.switchScene("login.fxml"));
        }, "logout-cleanup").start();
    }

    private void setProfileMsg(String msg, boolean error) {
        lblProfileMsg.setStyle(error ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #059669;");
        lblProfileMsg.setText(msg);
    }

    private void setPasswordMsg(String msg, boolean error) {
        lblPasswordMsg.setStyle(error ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #059669;");
        lblPasswordMsg.setText(msg);
    }

    private String s(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private String money(Object v) {
        if (v instanceof Number n) return String.format("%,.0f VNĐ", n.doubleValue());
        return "0 VNĐ";
    }
}
