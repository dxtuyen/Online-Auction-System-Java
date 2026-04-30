package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.*;

/** Controller hiển thị danh sách phiên đấu giá. */
public class AuctionListController {

    @FXML private TableView<Map<String, Object>> tblAuctions;
    @FXML private TableColumn<Map<String, Object>, String> colId;
    @FXML private TableColumn<Map<String, Object>, String> colItem;
    @FXML private TableColumn<Map<String, Object>, String> colCategory;
    @FXML private TableColumn<Map<String, Object>, String> colPrice;
    @FXML private TableColumn<Map<String, Object>, String> colBids;
    @FXML private TableColumn<Map<String, Object>, String> colStatus;
    @FXML private TextField txtSearch;
    @FXML private Label lblUserInfo;
    @FXML private Button btnCreateAuction;

    private final ObservableList<Map<String, Object>> allData = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        ClientModel model = ClientModel.getInstance();
        lblUserInfo.setText(String.format("Xin chào, %s (%s)",
                model.getUsername(), model.getRole()));
        loadProfileSummary();

        // Seller thấy nút tạo phiên
        if ("SELLER".equals(model.getRole())) btnCreateAuction.setVisible(true);

        // Bind từng cột với key trong Map — PropertyValueFactory không dùng được cho Map
        colId.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue(), "auctionId")));
        colItem.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue(), "itemName")));
        colCategory.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue(), "itemCategory")));
        colPrice.setCellValueFactory(cd -> new SimpleStringProperty(
                formatMoney(cd.getValue().get("currentPrice"))));
        colBids.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue(), "totalBids")));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue(), "displayStatus")));

        tblAuctions.setItems(allData);

        // Double-click → mở bidding screen
        tblAuctions.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Map<String, Object> sel = tblAuctions.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    int id = (int) ((Number) sel.get("auctionId")).doubleValue();
                    openBidding(id);
                }
            }
        });

        // Filter live khi gõ
        txtSearch.textProperty().addListener((obs, old, val) -> filterTable(val));

        handleRefresh();
    }

    @FXML
    private void handleRefresh() {
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
                        allData.clear();
                        if (list != null) allData.addAll(list);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> ClientApp.showError("Lỗi: " + e.getMessage()));
            }
        }).start();
    }

    private void openBidding(int auctionId) {
        ClientApp.switchSceneWithData("bidding.fxml", ctrl -> {
            ((BiddingController) ctrl).setAuctionId(auctionId);
        });
    }

    private void filterTable(String kw) {
        if (kw == null || kw.isBlank()) {
            tblAuctions.setItems(allData);
            return;
        }
        String low = kw.toLowerCase();
        ObservableList<Map<String, Object>> filtered = FXCollections.observableArrayList();
        for (Map<String, Object> row : allData) {
            if (str(row, "itemName").toLowerCase().contains(low)
                    || str(row, "auctionId").contains(kw)) {
                filtered.add(row);
            }
        }
        tblAuctions.setItems(filtered);
    }

    @FXML
    private void handleLogout() {
        ClientModel.getInstance().disconnect();
        ClientApp.switchScene("login.fxml");
    }

    @FXML
    private void handleViewAccount() {
        requestProfile(data -> ClientApp.showInfo(formatProfileDetails(data)));
    }

    @FXML
    private void goToSellerDashboard() {
        ClientApp.switchScene("seller_dashboard.fxml");
    }

    private void loadProfileSummary() {
        requestProfile(data -> lblUserInfo.setText(formatProfileSummary(data)));
    }

    private void requestProfile(java.util.function.Consumer<Map<String, Object>> onSuccess) {
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest("GET_PROFILE", Map.of());
                Response res = model.waitForResponse("GET_PROFILE", 5000);
                if (res != null && res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) res.getData();
                    Platform.runLater(() -> onSuccess.accept(data));
                } else if (res != null) {
                    Platform.runLater(() -> ClientApp.showError(res.getMessage()));
                }
            } catch (Exception e) {
                Platform.runLater(() -> ClientApp.showError("Không tải được thông tin tài khoản: " + e.getMessage()));
            }
        }).start();
    }

    private String formatProfileSummary(Map<String, Object> data) {
        String base = String.format("Xin chào, %s (%s)", str(data, "username"), str(data, "displayRole"));
        String role = str(data, "role");
        if ("BIDDER".equals(role)) {
            return base + " | Khả dụng: " + formatMoney(data.get("availableBalance"));
        }
        if ("SELLER".equals(role)) {
            return base + " | Doanh thu: " + formatMoney(data.get("totalRevenue"));
        }
        return base;
    }

    private String formatProfileDetails(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tài khoản: ").append(str(data, "username")).append('\n');
        sb.append("Vai trò: ").append(str(data, "displayRole")).append('\n');
        sb.append("Trạng thái: ").append(str(data, "displayStatus"));

        String role = str(data, "role");
        if ("BIDDER".equals(role)) {
            sb.append('\n').append("Số dư ví: ").append(formatMoney(data.get("balance")));
            sb.append('\n').append("Đang giữ chỗ: ").append(formatMoney(data.get("reservedBalance")));
            sb.append('\n').append("Số dư khả dụng: ").append(formatMoney(data.get("availableBalance")));
        } else if ("SELLER".equals(role)) {
            sb.append('\n').append("Doanh thu hiện tại: ").append(formatMoney(data.get("totalRevenue")));
        }
        return sb.toString();
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return "";
        if (v instanceof Number n) return String.valueOf(n.intValue());
        return v.toString();
    }

    private String formatMoney(Object v) {
        if (v instanceof Number n) return String.format("%,.0f VNĐ", n.doubleValue());
        return "0 VNĐ";
    }
}
