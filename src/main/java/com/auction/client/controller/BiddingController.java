package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.protocol.Response;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Controller màn hình đấu giá realtime — trái tim của dự án.
 *
 * <p>Sửa sau refactor: auctionId là UUID String (server dùng UUID).
 * Profile response trả {@code balance}/{@code revenue}, không có available/reserved.</p>
 */
public class BiddingController {

    // Info labels
    @FXML private Label lblItemName;
    @FXML private Label lblItemInfo;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblIncrement;
    @FXML private Label lblBidCount;
    @FXML private Label lblLeader;
    @FXML private Label lblTimer;
    @FXML private Label lblError;

    // Bid controls
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;

    // Auto-bid
    @FXML private TextField txtMaxBid;
    @FXML private TextField txtIncrementAuto;
    @FXML private Label lblAutoBidStatus;

    // History + chart
    @FXML private ListView<String> lstBidHistory;
    @FXML private LineChart<String, Number> chartPrice;

    // State — auctionId là UUID dạng String (đồng bộ với server protocol)
    private String auctionId;
    private LocalDateTime endTime;
    private Timeline countdown;
    private XYChart.Series<String, Number> priceSeries;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Gọi từ AuctionListController sau khi load FXML. */
    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
        loadAuctionDetail();
        loadBidHistory();
        watchAuction();
        startCountdown();
    }

    @FXML
    private void initialize() {
        lblError.setText("");
        txtBidAmount.setOnAction(e -> handlePlaceBid());

        priceSeries = new XYChart.Series<>();
        chartPrice.getData().add(priceSeries);

        setupPushHandlers();
    }

    // =========== LOAD DATA ===========

    private void loadAuctionDetail() {
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                Response res = model.sendRequestAndWait("GET_AUCTION", Map.of("auctionId", auctionId), 5000);

                if (res != null && res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) res.getData();
                    Platform.runLater(() -> updateUI(data));
                }
            } catch (Exception e) {
                Platform.runLater(() -> lblError.setText("Lỗi: " + e.getMessage()));
            }
        }).start();
    }

    private void loadBidHistory() {
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                Response res = model.sendRequestAndWait("BID_HISTORY", Map.of("auctionId", auctionId), 5000);

                if (res != null && res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) res.getData();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> bids = (List<Map<String, Object>>) data.get("bids");

                    Platform.runLater(() -> renderHistory(bids));
                }
            } catch (Exception e) { /* ignore */ }
        }).start();
    }

    private void updateUI(Map<String, Object> data) {
        lblItemName.setText(str(data, "itemName"));
        lblItemInfo.setText(str(data, "itemDescription"));
        lblStartPrice.setText(formatMoney(data.get("startingPrice")));
        lblCurrentPrice.setText(formatMoney(data.get("currentPrice")));
        lblIncrement.setText(formatMoney(data.get("minimumIncrement")));
        lblBidCount.setText(str(data, "totalBids"));
        lblLeader.setText(data.get("leaderName") != null ? str(data, "leaderName") : "Chưa có");

        String endStr = str(data, "endTime");
        if (!endStr.isBlank()) {
            try { endTime = LocalDateTime.parse(endStr); }
            catch (Exception e) { endTime = LocalDateTime.now().plusMinutes(5); }
        }

        double curr = num(data.get("currentPrice"));
        double incr = num(data.get("minimumIncrement"));
        txtBidAmount.setPromptText(String.format("Tối thiểu %,.0f", curr + incr));
    }

    private void renderHistory(List<Map<String, Object>> bids) {
        if (bids == null) return;

        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = bids.size() - 1; i >= 0; i--) {
            Map<String, Object> b = bids.get(i);
            items.add(String.format("%s | %s | %s",
                    shortTime(str(b, "timestamp")),
                    str(b, "bidderName"),
                    formatMoney(b.get("amount"))));
        }
        lstBidHistory.setItems(items);

        priceSeries.getData().clear();
        for (Map<String, Object> b : bids) {
            priceSeries.getData().add(new XYChart.Data<>(
                    shortTime(str(b, "timestamp")),
                    num(b.get("amount"))));
        }
    }

    // =========== WATCH + PUSH ===========

    private void watchAuction() {
        new Thread(() -> {
            ClientModel.getInstance().sendRequest("WATCH_AUCTION",
                    Map.of("auctionId", auctionId));
        }).start();
    }

    /**
     * So sánh auctionId trong push payload bằng chuỗi để tránh nhầm "0 != 0" khi cast int trên UUID.
     */
    private boolean matchesAuction(Map<String, Object> data) {
        return auctionId != null
                && auctionId.equals(String.valueOf(data.get("auctionId")));
    }

    private void setupPushHandlers() {
        ClientModel model = ClientModel.getInstance();

        model.addPushHandler("BID_UPDATE", data -> {
            if (!matchesAuction(data)) return;
            Platform.runLater(() -> {
                lblCurrentPrice.setText(formatMoney(data.get("amount")));
                lblBidCount.setText(str(data, "totalBids"));
                loadBidHistory();
                flashLabel(lblCurrentPrice);
            });
        });

        model.addPushHandler("AUCTION_STATUS", data -> {
            if (!matchesAuction(data)) return;
            Platform.runLater(() -> {
                String status = str(data, "status");
                if ("FINISHED".equals(status) || "PAID".equals(status) || "CANCELED".equals(status)) {
                    lblTimer.setText("Phiên đã kết thúc");
                    btnPlaceBid.setDisable(true);
                    txtBidAmount.setDisable(true);
                    ClientApp.showInfo("Phiên đấu giá đã kết thúc!");
                }
            });
        });

        model.addPushHandler("AUCTION_EXTENDED", data -> {
            if (!matchesAuction(data)) return;
            Platform.runLater(() -> {
                try { endTime = LocalDateTime.parse(str(data, "newEndTime")); }
                catch (Exception ignored) {}
                lblError.setStyle("-fx-text-fill: #2563eb;");
                lblError.setText("⏰ Phiên đã được gia hạn!");
            });
        });
    }

    // =========== BID ===========

    @FXML
    private void handlePlaceBid() {
        double amount = parseMoney(txtBidAmount.getText());
        if (amount <= 0) { lblError.setText("Giá không hợp lệ"); return; }

        lblError.setText("");
        btnPlaceBid.setDisable(true);

        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                Response res = model.sendRequestAndWait("PLACE_BID", Map.of(
                        "auctionId", auctionId, "amount", amount), 5000);

                Platform.runLater(() -> {
                    btnPlaceBid.setDisable(false);
                    if (res != null && res.isSuccess()) {
                        txtBidAmount.clear();
                        lblError.setStyle("-fx-text-fill: #059669;");
                        lblError.setText("✓ " + res.getMessage());
                    } else {
                        lblError.setStyle("-fx-text-fill: #dc2626;");
                        lblError.setText(res != null ? res.getMessage() : "Timeout");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnPlaceBid.setDisable(false);
                    lblError.setText("Lỗi: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleSetAutoBid() {
        double maxBid = parseMoney(txtMaxBid.getText());
        double incr = parseMoney(txtIncrementAuto.getText());
        if (maxBid <= 0 || incr <= 0) {
            lblAutoBidStatus.setText("Nhập giá tối đa và bước nhảy");
            return;
        }

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            Response res = model.sendRequestAndWait("SET_AUTO_BID", Map.of(
                    "auctionId", auctionId,
                    "maxBid", maxBid,
                    "increment", incr), 5000);

            Platform.runLater(() -> {
                if (res != null && res.isSuccess()) {
                    lblAutoBidStatus.setStyle("-fx-text-fill: #059669;");
                    lblAutoBidStatus.setText("✓ " + res.getMessage());
                } else {
                    lblAutoBidStatus.setStyle("-fx-text-fill: #dc2626;");
                    lblAutoBidStatus.setText(res != null ? res.getMessage() : "Lỗi");
                }
            });
        }).start();
    }

    // =========== COUNTDOWN ===========

    private void startCountdown() {
        countdown = new Timeline(new javafx.animation.KeyFrame(
                Duration.seconds(1), e -> updateCountdown()));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
    }

    private void updateCountdown() {
        if (endTime == null) return;
        long sec = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
        if (sec <= 0) {
            lblTimer.setText("Hết giờ");
            lblTimer.getStyleClass().setAll("timer-urgent");
            countdown.stop();
            return;
        }
        long mins = sec / 60;
        long secs = sec % 60;
        lblTimer.setText(String.format("⏰ %02d:%02d", mins, secs));

        if (sec < 60) lblTimer.getStyleClass().setAll("timer-urgent");
        else lblTimer.getStyleClass().setAll("timer-normal");
    }

    // =========== EFFECTS ===========

    private void flashLabel(Label label) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), label);
        ft.setFromValue(0.3);
        ft.setToValue(1.0);
        ft.setCycleCount(4);
        ft.setAutoReverse(true);
        ft.play();
    }

    // =========== NAV ===========

    @FXML
    private void handleViewAccount() {
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                Response res = model.sendRequestAndWait("GET_PROFILE", Map.of(), 5000);

                if (res != null && res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) res.getData();
                    Platform.runLater(() -> ClientApp.showInfo(formatProfileDetails(data)));
                } else if (res != null) {
                    Platform.runLater(() -> ClientApp.showError(res.getMessage()));
                }
            } catch (Exception e) {
                Platform.runLater(() -> ClientApp.showError("Không tải được thông tin tài khoản: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void goBack() {
        if (countdown != null) countdown.stop();
        ClientModel.getInstance().clearBiddingPushHandlers();

        new Thread(() -> {
            ClientModel.getInstance().sendRequest("UNWATCH_AUCTION",
                    Map.of("auctionId", auctionId));
        }).start();

        ClientApp.switchScene("auction_list.fxml");
    }

    // =========== HELPERS ===========

    private String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return "";
        if (v instanceof Number n) return String.valueOf(n.intValue());
        return v.toString();
    }

    private double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private String formatMoney(Object v) {
        if (v instanceof Number n) return String.format("%,.0f VNĐ", n.doubleValue());
        return "0 VNĐ";
    }

    private double parseMoney(String s) {
        try { return Double.parseDouble(s.trim().replace(",", "").replace(".", "")); }
        catch (Exception e) { return -1; }
    }

    private String shortTime(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp).format(TIME_FMT);
        } catch (Exception e) {
            return timestamp;
        }
    }

    /**
     * Server trả {@code balance} (số dư) và {@code revenue} (doanh thu). Hệ thống chưa reserve balance
     * lúc bid nên không có concept available/reserved tách biệt.
     */
    private String formatProfileDetails(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tài khoản: ").append(str(data, "username")).append('\n');
        sb.append("Vai trò: ").append(str(data, "displayRole")).append('\n');
        sb.append("Trạng thái: ").append(str(data, "displayStatus"));
        if (!"ADMIN".equals(str(data, "role"))) {
            sb.append('\n').append("Số dư: ").append(formatMoney(data.get("balance")));
            sb.append('\n').append("Doanh thu: ").append(formatMoney(data.get("revenue")));
        }
        return sb.toString();
    }
}
