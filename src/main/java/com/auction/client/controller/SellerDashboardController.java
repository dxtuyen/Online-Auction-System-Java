package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.protocol.Response;
import com.auction.util.MoneyHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.util.*;

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

        // ID là UUID dài 36 ký tự — hiển thị 8 ký tự đầu cho gọn, full UUID giữ trong row data.
        colItemId.setCellValueFactory(cd -> new SimpleStringProperty(shortId(s(cd.getValue(), "itemId"))));
        colItemName.setCellValueFactory(cd -> new SimpleStringProperty(s(cd.getValue(), "name")));
        colItemPrice.setCellValueFactory(cd -> new SimpleStringProperty(money(cd.getValue().get("startingPrice"))));
        tblItems.setItems(itemsData);

        // Click row → auto-fill ô Item ID bên panel tạo phiên (UUID khó copy thủ công).
        tblItems.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (newRow != null) txtAuctionItemId.setText(s(newRow, "itemId"));
        });

        loadProfileSummary();
        loadMyItems();
    }

    private void loadMyItems() {
        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            Response res = model.sendRequestAndWait("LIST_MY_ITEMS", Map.of(), 5000);
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
        BigDecimal price;
        try {
            price = MoneyHelper.parseWholeAmountInput(txtStartPrice.getText(), "Giá khởi điểm");
        } catch (IllegalArgumentException e) {
            lblItemStatus.setText(e.getMessage());
            return;
        }

        if (name.isEmpty() || price.signum() <= 0) {
            lblItemStatus.setText("Nhập tên và giá khởi điểm hợp lệ");
            return;
        }

        // Build attributes cho từng category
        Map<String, String> attrs = new HashMap<>();
        String category = cboCategory.getValue();
        String brand = txtBrand.getText().trim();
        String modelVal = txtModel.getText().trim();

        // Key phải khớp với ItemFactory.create — sai key là factory ném "Thiếu thuộc tính bắt buộc".
        switch (category) {
            case "ELECTRONICS" -> {
                attrs.put("brand", brand);
                attrs.put("model", modelVal);
                attrs.put("warrantyMonths", "12");
            }
            case "ART" -> {
                attrs.put("artist", brand);
                attrs.put("yearCreated", modelVal.isEmpty() ? "2024" : modelVal);
            }
            case "VEHICLE" -> {
                attrs.put("make", brand);
                attrs.put("model", modelVal);
                attrs.put("year", "2022");
                attrs.put("mileageKm", "0");
            }
            default -> { /* OtherItem — không có thuộc tính bắt buộc */ }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category", category);
        data.put("name", name);
        data.put("description", desc);
        data.put("startingPrice", price.toPlainString());
        data.put("condition", cboCondition.getValue());
        data.put("specificAttributes", attrs);

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            Response res = model.sendRequestAndWait("CREATE_ITEM", data, 5000);
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
        String itemId = txtAuctionItemId.getText().trim();
        if (itemId.isEmpty()) {
            lblAuctionStatus.setText("Chọn item ở bảng bên trái hoặc nhập Item ID");
            return;
        }
        int duration;
        BigDecimal incr;
        try {
            duration = Integer.parseInt(txtDuration.getText().trim());
            incr = MoneyHelper.parseWholeAmountInput(txtMinIncrement.getText(), "Bước nhảy tối thiểu");
        } catch (Exception e) {
            lblAuctionStatus.setText("Nhập thời gian/bước nhảy hợp lệ");
            return;
        }
        if (incr.signum() <= 0) {
            lblAuctionStatus.setText("Bước nhảy tối thiểu phải > 0");
            return;
        }

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            Response res = model.sendRequestAndWait("CREATE_AUCTION", Map.of(
                    "itemId", itemId,
                    "durationMinutes", duration,
                    "minimumIncrement", incr.toPlainString()), 5000);

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
            String summary = String.format("%s | Số dư: %s | Doanh thu: %s",
                    s(data, "username"),
                    money(data.get("balance")),
                    money(data.get("revenue")));
            lblUserInfo.setText(summary);
        });
    }

    private void requestProfile(java.util.function.Consumer<Map<String, Object>> onSuccess) {
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                Response res = model.sendRequestAndWait("GET_PROFILE", Map.of(), 5000);
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
        if (v instanceof Number n) return String.valueOf(n.longValue());
        return v.toString();
    }

    /** Hiển thị 8 ký tự đầu của UUID cho gọn — UUID đầy đủ 36 ký tự khó đọc. */
    private String shortId(String id) {
        if (id == null || id.length() <= 8) return id == null ? "" : id;
        return id.substring(0, 8);
    }

    private String money(Object v) {
        if (v instanceof Number n) return String.format("%,.0f VNĐ", n.doubleValue());
        return "0 VNĐ";
    }

}
