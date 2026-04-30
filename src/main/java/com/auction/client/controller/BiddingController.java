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
 * <p>Chức năng:
 * <ul>
 *   <li>Hiển thị thông tin phiên, giá hiện tại, lịch sử bid</li>
 *   <li>Countdown timer tới khi phiên kết thúc</li>
 *   <li>Nhận push từ server (BID_UPDATE, AUCTION_STATUS, AUCTION_EXTENDED)</li>
 *   <li>LineChart giá theo thời gian (cập nhật realtime)</li>
 *   <li>Hiệu ứng flash khi giá thay đổi</li>
 *   <li>Đặt giá thủ công + Auto-bid</li>
 * </ul>
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

    // State
    private int auctionId;
    private LocalDateTime endTime;
    private Timeline countdown;
    private XYChart.Series<String, Number> priceSeries;
    private static final int MAX_CHART_POINTS = 50;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Gọi từ AuctionListController sau khi load FXML. */
    public void setAuctionId(int auctionId) {
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

        // Khởi tạo chart series
        priceSeries = new XYChart.Series<>();
        chartPrice.getData().add(priceSeries);

        // Đăng ký lắng nghe push từ server
        setupPushHandlers();
    }

    // =========== LOAD DATA ===========

    private void loadAuctionDetail() {
        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest("GET_AUCTION", Map.of("auctionId", auctionId));
                Response res = model.waitForResponse("GET_AUCTION", 5000);

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
                model.sendRequest("BID_HISTORY", Map.of("auctionId", auctionId));
                Response res = model.waitForResponse("BID_HISTORY", 5000);

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

        // Parse endTime cho countdown
        String endStr = str(data, "endTime");
        if (!endStr.isBlank()) {
            try { endTime = LocalDateTime.parse(endStr); }
            catch (Exception e) { endTime = LocalDateTime.now().plusMinutes(5); }
        }

        // Gợi ý giá tiếp theo
        double curr = num(data.get("currentPrice"));
        double incr = num(data.get("minimumIncrement"));
        txtBidAmount.setPromptText(String.format("Tối thiểu %,.0f", curr + incr));
    }

    private void renderHistory(List<Map<String, Object>> bids) {
        if (bids == null) return;

        // List hiển thị: mới nhất trước
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = bids.size() - 1; i >= 0; i--) {
            Map<String, Object> b = bids.get(i);
            items.add(String.format("%s | %s | %s",
                    shortTime(str(b, "timestamp")),
                    str(b, "bidderName"),
                    formatMoney(b.get("amount"))));
        }
        lstBidHistory.setItems(items);

        // Chart: tăng dần theo thời gian (cũ → mới)
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

    private void setupPushHandlers() {
        ClientModel model = ClientModel.getInstance();

        model.addPushHandler("BID_UPDATE", data -> {
            if (num(data.get("auctionId")) != auctionId) return;

            Platform.runLater(() -> {
                lblCurrentPrice.setText(formatMoney(data.get("amount")));
                lblBidCount.setText(str(data, "totalBids"));
                // Tải lại tên người dẫn đầu qua history để có username
                loadBidHistory();
                flashLabel(lblCurrentPrice);
            });
        });

        model.addPushHandler("AUCTION_STATUS", data -> {
            if (num(data.get("auctionId")) != auctionId) return;
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
            if (num(data.get("auctionId")) != auctionId) return;
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
                model.sendRequest("PLACE_BID", Map.of(
                        "auctionId", auctionId, "amount", amount));
                Response res = model.waitForResponse("PLACE_BID", 5000);

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
            model.sendRequest("SET_AUTO_BID", Map.of(
                    "auctionId", auctionId,
                    "maxBid", maxBid,
                    "increment", incr));
            Response res = model.waitForResponse("SET_AUTO_BID", 5000);

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

        // Đổi màu đỏ khi còn < 60s
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
    private void goBack() {
        if (countdown != null) countdown.stop();
        // Gỡ push handler để tránh leak khi mở phiên khác
        ClientModel.getInstance().clearBiddingPushHandlers();

        // Unwatch ở server
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

    /** "2026-04-22T14:30:15.xxx" → "14:30:15" */
    private String shortTime(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp).format(TIME_FMT);
        } catch (Exception e) {
            return timestamp;
        }
    }
}
