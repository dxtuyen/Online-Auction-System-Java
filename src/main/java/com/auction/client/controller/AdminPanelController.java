package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bảng điều khiển dành cho ADMIN: ban/unban user, đóng cưỡng bức phiên đấu giá.
 *
 * <p>Mỗi tab tự load lại dữ liệu khi mở (handleRefreshXxx). Hành động ban/unban
 * /close gọi server xong sẽ reload nguyên trang để UI luôn đồng bộ với DB —
 * tránh patch state tay sai lệch.</p>
 */
public class AdminPanelController {

    @FXML private Label lblAdminInfo;

    // === Users tab ===
    @FXML private TextField txtSearchUser;
    @FXML private TableView<Map<String, Object>> tblUsers;
    @FXML private TableColumn<Map<String, Object>, String> colUsername;
    @FXML private TableColumn<Map<String, Object>, String> colEmail;
    @FXML private TableColumn<Map<String, Object>, String> colFullName;
    @FXML private TableColumn<Map<String, Object>, String> colRole;
    @FXML private TableColumn<Map<String, Object>, String> colStatus;
    @FXML private TableColumn<Map<String, Object>, String> colBalance;
    @FXML private TableColumn<Map<String, Object>, Map<String, Object>> colAction;
    @FXML private Label lblUserStatus;

    private final List<Map<String, Object>> allUsers = new ArrayList<>();
    private final ObservableList<Map<String, Object>> userRows = FXCollections.observableArrayList();

    // === Auctions tab ===
    @FXML private TextField txtSearchAuction;
    @FXML private TableView<Map<String, Object>> tblAuctions;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionId;
    @FXML private TableColumn<Map<String, Object>, String> colItemName;
    @FXML private TableColumn<Map<String, Object>, String> colCurrentPrice;
    @FXML private TableColumn<Map<String, Object>, String> colTotalBids;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionStatus;
    @FXML private TableColumn<Map<String, Object>, Map<String, Object>> colAuctionAction;
    @FXML private Label lblAuctionStatus;

    private final List<Map<String, Object>> allAuctions = new ArrayList<>();
    private final ObservableList<Map<String, Object>> auctionRows = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        ClientModel model = ClientModel.getInstance();
        lblAdminInfo.setText(String.format("👤 %s (%s)", model.getUsername(), model.getRole()));
        lblUserStatus.setText("");
        lblAuctionStatus.setText("");

        // === Users table binding ===
        colUsername.setCellValueFactory(cd -> col(cd.getValue(), "username"));
        colEmail.setCellValueFactory(cd -> col(cd.getValue(), "email"));
        colFullName.setCellValueFactory(cd -> col(cd.getValue(), "fullName"));
        colRole.setCellValueFactory(cd -> col(cd.getValue(), "displayRole"));
        colStatus.setCellValueFactory(cd -> col(cd.getValue(), "displayStatus"));
        colBalance.setCellValueFactory(cd -> new SimpleStringProperty(money(cd.getValue().get("balance"))));
        colAction.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        colAction.setCellFactory(userActionCellFactory());
        tblUsers.setItems(userRows);

        // === Auctions table binding ===
        colAuctionId.setCellValueFactory(cd -> col(cd.getValue(), "auctionId"));
        colItemName.setCellValueFactory(cd -> col(cd.getValue(), "itemName"));
        colCurrentPrice.setCellValueFactory(cd -> new SimpleStringProperty(money(cd.getValue().get("currentPrice"))));
        colTotalBids.setCellValueFactory(cd -> col(cd.getValue(), "totalBids"));
        colAuctionStatus.setCellValueFactory(cd -> col(cd.getValue(), "displayStatus"));
        colAuctionAction.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        colAuctionAction.setCellFactory(auctionActionCellFactory());
        tblAuctions.setItems(auctionRows);

        txtSearchUser.textProperty().addListener((obs, old, val) -> renderUsers(val));
        txtSearchAuction.textProperty().addListener((obs, old, val) -> renderAuctions(val));

        handleRefreshUsers();
        handleRefreshAuctions();
    }

    // =================== USERS ===================

    @FXML
    private void handleRefreshUsers() {
        lblUserStatus.setStyle("-fx-text-fill: #6b7280;");
        lblUserStatus.setText("Đang tải danh sách người dùng...");
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest("LIST_USERS", Map.of());
                Response res = model.waitForResponse("LIST_USERS", 5000);
                if (res != null && res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) res.getData();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("users");
                    Platform.runLater(() -> {
                        allUsers.clear();
                        if (list != null) allUsers.addAll(list);
                        renderUsers(txtSearchUser.getText());
                        lblUserStatus.setStyle("-fx-text-fill: #6b7280;");
                        lblUserStatus.setText("Đã tải " + allUsers.size() + " người dùng.");
                    });
                } else {
                    Platform.runLater(() -> showUserStatus(res != null ? res.getMessage() : "Không phản hồi", true));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showUserStatus("Lỗi: " + e.getMessage(), true));
            }
        }, "admin-load-users").start();
    }

    private void renderUsers(String keyword) {
        String low = keyword == null ? "" : keyword.trim().toLowerCase();
        userRows.clear();
        for (Map<String, Object> u : allUsers) {
            if (!low.isEmpty()) {
                String uname = s(u, "username").toLowerCase();
                String email = s(u, "email").toLowerCase();
                if (!uname.contains(low) && !email.contains(low)) continue;
            }
            userRows.add(u);
        }
    }

    /** Mỗi row của cột Hành động render nút Ban hoặc Unban tùy theo trạng thái. */
    private Callback<TableColumn<Map<String, Object>, Map<String, Object>>,
            TableCell<Map<String, Object>, Map<String, Object>>> userActionCellFactory() {
        return column -> new TableCell<>() {
            private final Button btnBan = new Button("🔒 Khóa");
            private final Button btnUnban = new Button("🔓 Mở khóa");
            private final HBox box = new HBox(6, btnBan, btnUnban);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnBan.getStyleClass().add("btn-secondary");
                btnUnban.getStyleClass().add("btn-secondary");
                btnBan.setOnAction(e -> {
                    Map<String, Object> row = getItem();
                    if (row != null) banUser(row);
                });
                btnUnban.setOnAction(e -> {
                    Map<String, Object> row = getItem();
                    if (row != null) unbanUser(row);
                });
            }
            @Override
            protected void updateItem(Map<String, Object> row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                String status = s(row, "status");
                String role = s(row, "role");
                String me = ClientModel.getInstance().getUserId();
                boolean isSelf = me != null && me.equals(s(row, "userId"));
                boolean isAdmin = "ADMIN".equals(role);
                btnBan.setVisible(!"BANNED".equals(status));
                btnBan.setManaged(!"BANNED".equals(status));
                btnUnban.setVisible("BANNED".equals(status));
                btnUnban.setManaged("BANNED".equals(status));
                // Cấm thao tác lên admin khác và lên chính mình
                btnBan.setDisable(isAdmin || isSelf);
                btnUnban.setDisable(isAdmin || isSelf);
                setGraphic(box);
            }
        };
    }

    private void banUser(Map<String, Object> row) {
        String username = s(row, "username");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Khóa tài khoản '" + username + "'?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        sendUserAction("BAN_USER", s(row, "userId"));
    }

    private void unbanUser(Map<String, Object> row) {
        sendUserAction("UNBAN_USER", s(row, "userId"));
    }

    private void sendUserAction(String action, String userId) {
        showUserStatus("Đang xử lý...", false);
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest(action, Map.of("userId", userId));
                Response res = model.waitForResponse(action, 5000);
                Platform.runLater(() -> {
                    if (res != null && res.isSuccess()) {
                        showUserStatus("✓ " + res.getMessage(), false);
                        handleRefreshUsers();
                    } else {
                        showUserStatus(res != null ? res.getMessage() : "Không phản hồi", true);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showUserStatus("Lỗi: " + e.getMessage(), true));
            }
        }, "admin-user-action").start();
    }

    // =================== AUCTIONS ===================

    @FXML
    private void handleRefreshAuctions() {
        lblAuctionStatus.setStyle("-fx-text-fill: #6b7280;");
        lblAuctionStatus.setText("Đang tải danh sách phiên đấu giá...");
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest("LIST_AUCTIONS", Map.of());
                Response res = model.waitForResponse("LIST_AUCTIONS", 5000);
                if (res != null && res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) res.getData();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("auctions");
                    Platform.runLater(() -> {
                        allAuctions.clear();
                        if (list != null) allAuctions.addAll(list);
                        renderAuctions(txtSearchAuction.getText());
                        lblAuctionStatus.setStyle("-fx-text-fill: #6b7280;");
                        lblAuctionStatus.setText("Đã tải " + allAuctions.size() + " phiên đấu giá.");
                    });
                } else {
                    Platform.runLater(() -> showAuctionStatus(res != null ? res.getMessage() : "Không phản hồi", true));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAuctionStatus("Lỗi: " + e.getMessage(), true));
            }
        }, "admin-load-auctions").start();
    }

    private void renderAuctions(String keyword) {
        String low = keyword == null ? "" : keyword.trim().toLowerCase();
        auctionRows.clear();
        for (Map<String, Object> a : allAuctions) {
            if (!low.isEmpty()) {
                String name = s(a, "itemName").toLowerCase();
                String id = s(a, "auctionId").toLowerCase();
                if (!name.contains(low) && !id.contains(low)) continue;
            }
            auctionRows.add(a);
        }
    }

    private Callback<TableColumn<Map<String, Object>, Map<String, Object>>,
            TableCell<Map<String, Object>, Map<String, Object>>> auctionActionCellFactory() {
        return column -> new TableCell<>() {
            private final Button btnClose = new Button("🚫 Đóng phiên");
            {
                btnClose.getStyleClass().add("btn-secondary");
                btnClose.setOnAction(e -> {
                    Map<String, Object> row = getItem();
                    if (row != null) closeAuction(row);
                });
            }
            @Override
            protected void updateItem(Map<String, Object> row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                String status = s(row, "status");
                // Chỉ đóng được khi phiên còn mở (PENDING/RUNNING).
                boolean closable = "PENDING".equals(status) || "RUNNING".equals(status);
                btnClose.setDisable(!closable);
                setGraphic(btnClose);
            }
        };
    }

    private void closeAuction(Map<String, Object> row) {
        String name = s(row, "itemName");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Đóng cưỡng bức phiên đấu giá của '" + name + "'?\n"
                        + "Người đang dẫn đầu (nếu có) sẽ được hoàn lại số tiền đã đặt cọc.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        showAuctionStatus("Đang gửi yêu cầu...", false);
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest("ADMIN_CLOSE_AUCTION",
                        Map.of("auctionId", s(row, "auctionId")));
                Response res = model.waitForResponse("ADMIN_CLOSE_AUCTION", 5000);
                Platform.runLater(() -> {
                    if (res != null && res.isSuccess()) {
                        showAuctionStatus("✓ " + res.getMessage(), false);
                        handleRefreshAuctions();
                    } else {
                        showAuctionStatus(res != null ? res.getMessage() : "Không phản hồi", true);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAuctionStatus("Lỗi: " + e.getMessage(), true));
            }
        }, "admin-close-auction").start();
    }

    // =================== NAV ===================

    @FXML
    private void goBack() {
        ClientApp.switchScene("auction_list.fxml");
    }

    @FXML
    private void handleLogout() {
        new Thread(() -> {
            ClientModel.getInstance().logoutAndDisconnect();
            Platform.runLater(() -> ClientApp.switchScene("login.fxml"));
        }, "logout-cleanup").start();
    }

    // =================== HELPERS ===================

    private SimpleStringProperty col(Map<String, Object> row, String key) {
        return new SimpleStringProperty(s(row, key));
    }

    private String s(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return "";
        if (v instanceof Number n) return String.valueOf(n.intValue());
        return v.toString();
    }

    private String money(Object v) {
        if (v instanceof Number n) return String.format("%,.0f VNĐ", n.doubleValue());
        return "0 VNĐ";
    }

    private void showUserStatus(String msg, boolean error) {
        lblUserStatus.setStyle(error ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #059669;");
        lblUserStatus.setText(msg);
    }

    private void showAuctionStatus(String msg, boolean error) {
        lblAuctionStatus.setStyle(error ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #059669;");
        lblAuctionStatus.setText(msg);
    }
}
