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

/**
 * Dashboard cho người bán: tạo item, mở phiên đấu giá.
 *
 * <p>Sửa sau refactor: itemId/auctionId là UUID String. Profile dùng field {@code revenue}.
 * Attribute name cho VEHICLE khớp với ItemFactory: {@code make}, {@code year}, {@code mileageKm}.</p>
 */
public class SellerDashboardController {

    @FXML private Label lblUserInfo;
    @FXML private TableView<Map<String, Object>> tblItems;
    @FXML private TableColumn<Map<String, Object>, String> colItemId;
    @FXML private TableColumn<Map<String, Object>, String> colItemName;
    @FXML private TableColumn<Map<String, Object>, String> colItemPrice;

    @FXML private ComboBox<String> cboCategory;
    @FXML private TextField txtName;
    @FXML private TextArea txtDesc;
    @FXML private TextField txtStartPrice;
    @FXML private ComboBox<String> cboCondition;
    @FXML private TextField txtBrand;
    @FXML private TextField txtModel;
    @FXML private Label lblItemStatus;

    @FXML private TextField txtAuctionItemId;
    @FXML private TextField txtDuration;
    @FXML private TextField txtMinIncrement;
    @FXML private Label lblAuctionStatus;

    private final ObservableList<Map<String, Object>> itemsData = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        cboCategory.setValue("ELECTRONICS");
        cboCondition.setValue("NEW");

        colItemId.setCellValueFactory(cd -> new SimpleStringProperty(s(cd.getValue(), "itemId")));
        colItemName.setCellValueFactory(cd -> new SimpleStringProperty(s(cd.getValue(), "name")));
        colItemPrice.setCellValueFactory(cd -> new SimpleStringProperty(money(cd.getValue().get("startingPrice"))));
        tblItems.setItems(itemsData);

        // Double-click item để copy id sang khung tạo phiên — đỡ phải gõ UUID dài
        tblItems.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Map<String, Object> sel = tblItems.getSelectionModel().getSelectedItem();
                if (sel != null) txtAuctionItemId.setText(s(sel, "itemId"));
            }
        });

        loadProfileSummary();
        loadMyItems();
    }

    private void loadMyItems() {
        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            model.sendRequest("LIST_MY_ITEMS", Map.of());
            Response res = model.waitForResponse("LIST_MY_ITEMS", 5000);
            if (res != null && res.isSuccess()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) res.getData();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
                Platform.runLater(() -> {
                    itemsData.clear();
                    if (items != null) itemsData.addAll(items);
                });
            }
        }).start();
    }

    @FXML
    private void handleCreateItem() {
        String name = txtName.getText().trim();
        String desc = txtDesc.getText().trim();
        double price = parseMoney(txtStartPrice.getText());

        if (name.isEmpty() || price <= 0) {
            lblItemStatus.setText("Nhập tên và giá khởi điểm hợp lệ");
            return;
        }

        // Tên attribute phải khớp với ItemFactory cho từng category (xem ItemFactory.create()).
        Map<String, String> attrs = new HashMap<>();
        String category = cboCategory.getValue();
        String brand = txtBrand.getText().trim();
        String modelVal = txtModel.getText().trim();

        switch (category) {
            case "ELECTRONICS" -> {
                attrs.put("brand", brand);
                attrs.put("model", modelVal);
                attrs.put("warrantyMonths", "12");
            }
            case "ART" -> {
                // Factory: artist (required), yearCreated (optional), medium (required)
                attrs.put("artist", brand);
                attrs.put("yearCreated", modelVal.isEmpty() ? "2024" : modelVal);
                attrs.put("medium", "Sơn dầu");
            }
            case "VEHICLE" -> {
                // Factory dùng "make"/"year"/"mileageKm" (KHÔNG phải brand/manufactureYear)
                attrs.put("make", brand);
                attrs.put("model", modelVal);
                attrs.put("year", "2022");
                attrs.put("mileageKm", "0");
            }
            default -> { /* OtherItem — không cần attr bắt buộc */ }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category", category);
        data.put("name", name);
        data.put("description", desc);
        data.put("startingPrice", price);
        data.put("condition", cboCondition.getValue());
        data.put("specificAttributes", attrs);

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            model.sendRequest("CREATE_ITEM", data);
            Response res = model.waitForResponse("CREATE_ITEM", 5000);
            Platform.runLater(() -> {
                if (res != null && res.isSuccess()) {
                    lblItemStatus.setStyle("-fx-text-fill: #059669;");
                    lblItemStatus.setText("✓ " + res.getMessage());
                    clearItemForm();
                    loadMyItems();
                } else {
                    lblItemStatus.setStyle("-fx-text-fill: #dc2626;");
                    lblItemStatus.setText(res != null ? res.getMessage() : "Lỗi");
                }
            });
        }).start();
    }

    @FXML
    private void handleCreateAuction() {
        // itemId là UUID string — KHÔNG parse int.
        String itemId = txtAuctionItemId.getText().trim();
        int duration;
        double incr;
        try {
            duration = Integer.parseInt(txtDuration.getText().trim());
            incr = Double.parseDouble(txtMinIncrement.getText().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            lblAuctionStatus.setText("Nhập số hợp lệ cho duration và increment");
            return;
        }
        if (itemId.isEmpty()) {
            lblAuctionStatus.setText("Nhập itemId (double-click item trong bảng để copy)");
            return;
        }

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            model.sendRequest("CREATE_AUCTION", Map.of(
                    "itemId", itemId,
                    "durationMinutes", duration,
                    "minimumIncrement", incr));
            Response res = model.waitForResponse("CREATE_AUCTION", 5000);

            Platform.runLater(() -> {
                if (res != null && res.isSuccess()) {
                    lblAuctionStatus.setStyle("-fx-text-fill: #059669;");
                    lblAuctionStatus.setText("✓ " + res.getMessage());
                } else {
                    lblAuctionStatus.setStyle("-fx-text-fill: #dc2626;");
                    lblAuctionStatus.setText(res != null ? res.getMessage() : "Lỗi");
                }
            });
        }).start();
    }

    @FXML
    private void goBack() {
        ClientApp.switchScene("auction_list.fxml");
    }

    @FXML
    private void handleViewAccount() {
        requestProfile(data -> ClientApp.showInfo(formatProfileDetails(data)));
    }

    private void loadProfileSummary() {
        requestProfile(data -> {
            String summary = String.format("%s | Doanh thu: %s",
                    s(data, "username"), money(data.get("revenue")));
            lblUserInfo.setText(summary);
        });
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

    private String formatProfileDetails(Map<String, Object> data) {
        return String.format("Tài khoản: %s%nVai trò: %s%nTrạng thái: %s%nSố dư: %s%nDoanh thu: %s",
                s(data, "username"),
                s(data, "displayRole"),
                s(data, "displayStatus"),
                money(data.get("balance")),
                money(data.get("revenue")));
    }

    private void clearItemForm() {
        txtName.clear();
        txtDesc.clear();
        txtStartPrice.clear();
        txtBrand.clear();
        txtModel.clear();
    }

    private String s(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return "";
        if (v instanceof Number n) return String.valueOf(n.intValue());
        return v.toString();
    }

    private String money(Object v) {
        if (v instanceof Number n) return String.format("%,.0f VNĐ", n.doubleValue());
        return "0 VNĐ";
    }

    private double parseMoney(String s) {
        try { return Double.parseDouble(s.trim().replace(",", "").replace(".", "")); }
        catch (Exception e) { return -1; }
    }
}
