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

public class SellerDashboardController {

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

        // Build attributes cho từng category
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
                attrs.put("artist", brand);
                attrs.put("year", modelVal.isEmpty() ? "2024" : modelVal);
            }
            case "VEHICLE" -> {
                attrs.put("brand", brand);
                attrs.put("model", modelVal);
                attrs.put("manufactureYear", "2022");
                attrs.put("mileage", "0");
                attrs.put("color", "Đen");
                attrs.put("fuelType", "Xăng");
                attrs.put("transmission", "Số tay");
                attrs.put("ownerCount", "1");
                attrs.put("hasRegistration", "true");
            }
            default -> { /* OtherItem */ }
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
        int itemId;
        int duration;
        double incr;
        try {
            itemId = Integer.parseInt(txtAuctionItemId.getText().trim());
            duration = Integer.parseInt(txtDuration.getText().trim());
            incr = Double.parseDouble(txtMinIncrement.getText().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            lblAuctionStatus.setText("Nhập số hợp lệ");
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
