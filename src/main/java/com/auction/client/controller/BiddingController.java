package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.client.util.ImageCacheService;
import com.auction.client.util.MoneyInputFormatter;
import com.auction.protocol.Response;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.math.BigDecimal;
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
    @FXML private ImageView imgItem;
    @FXML private Label lblItemName;
    @FXML private Label lblItemCategory;
    @FXML private Label lblItemInfo;
    @FXML private ImageView imgSellerAvatar;
    @FXML private Label lblSellerName;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblIncrement;
    @FXML private Label lblBidCount;
    @FXML private Label lblLeader;
    @FXML private Label lblStatusBadge;
    @FXML private Label lblTimer;
    @FXML private Label lblError;

    // Bid controls
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;

    // Auto-bid
    @FXML private TextField txtMaxBid;
    @FXML private TextField txtIncrementAuto;
    @FXML private Label lblAutoBidStatus;

    // Settlement (sau khi phiên FINISHED, winner chọn trả tiền hoặc bỏ)
    @FXML private javafx.scene.layout.VBox paneSettlement;
    @FXML private Label lblSettleInfo;
    @FXML private Label lblSettleCountdown;
    @FXML private Button btnConfirmPay;
    @FXML private Button btnForfeit;
    @FXML private Label lblSettleStatus;

    // History + chart
    @FXML private ListView<String> lstBidHistory;
    @FXML private LineChart<String, Number> chartPrice;

    // State — auctionId là UUID dạng String (đồng bộ với server protocol)
    private String auctionId;
    private LocalDateTime endTime;
    private Timeline countdown;
    private Timeline settleCountdown;
    private XYChart.Series<String, Number> priceSeries;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Cần để dựng panel settlement: giữ startingPrice & currentPrice từ GET_AUCTION
    // để khi push AUCTION_STATUS đến vẫn còn dữ liệu hiển thị mức phạt.
    private double startingPrice;
    private double currentPrice;
    // Cache để cập nhật prompt text "Tối thiểu X" mỗi khi có BID_UPDATE
    // (push không gửi increment lại vì là invariant của auction).
    private double minimumIncrement;

    // Phải đồng bộ với AuctionManager.AUTO_PAY_AFTER_MINUTES / FORFEIT_PENALTY_RATE server-side.
    // Chỉ dùng cho hiển thị; tính toán thật server làm.
    private static final int AUTO_PAY_AFTER_MINUTES = 5;
    private static final double FORFEIT_PENALTY_RATE = 0.4;

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
        MoneyInputFormatter.apply(txtBidAmount, txtMaxBid, txtIncrementAuto);
        txtBidAmount.setOnAction(e -> handlePlaceBid());
        // Clip avatar người bán thành hình tròn — ImageView vốn vuông.
        imgSellerAvatar.setClip(new Circle(14, 14, 14));

        priceSeries = new XYChart.Series<>();
        chartPrice.getData().add(priceSeries);

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
        lblItemCategory.setText(str(data, "itemCategory"));
        lblItemInfo.setText(str(data, "itemDescription"));
        String imageUrl = str(data, "imageUrl");
        if (!imageUrl.isBlank()) {
            ImageCacheService.getInstance().loadAsync(imageUrl, imgItem::setImage);
        }
        lblSellerName.setText(str(data, "sellerName"));
        String sellerAvatar = str(data, "sellerAvatarUrl");
        if (!sellerAvatar.isBlank()) {
            ImageCacheService.getInstance().loadAsync(sellerAvatar, imgSellerAvatar::setImage);
        }
        lblStartPrice.setText(formatMoney(data.get("startingPrice")));
        lblCurrentPrice.setText(formatMoney(data.get("currentPrice")));
        lblIncrement.setText(formatMoney(data.get("minimumIncrement")));
        lblBidCount.setText(str(data, "totalBids"));
        lblLeader.setText(data.get("leaderName") != null ? str(data, "leaderName") : "Chưa có");

        startingPrice = num(data.get("startingPrice"));
        currentPrice = num(data.get("currentPrice"));
        minimumIncrement = num(data.get("minimumIncrement"));

        String endStr = str(data, "endTime");
        if (!endStr.isBlank()) {
            try { endTime = LocalDateTime.parse(endStr); }
            catch (Exception e) { endTime = LocalDateTime.now().plusMinutes(5); }
        }

        txtBidAmount.setPromptText(String.format("Tối thiểu %,.0f", currentPrice + minimumIncrement));

        String status = str(data, "status");
        applyStatusBadge(status, str(data, "displayStatus"));
        // Reload trong khi phiên đã FINISHED và mình là winner → dựng panel ngay.
        String leaderId = data.get("highestBidderId") == null ? null : str(data, "highestBidderId");
        if ("FINISHED".equals(status) && isMyId(leaderId)) {
            showSettlementPanel();
        }
    }

    /**
     * Cập nhật badge trạng thái phiên với CSS class tương ứng (status-running / -finished / ...).
     * Reset class cũ trước khi add mới để không tích lũy nhiều màu khi status đổi.
     */
    private void applyStatusBadge(String status, String displayStatus) {
        lblStatusBadge.setText(displayStatus);
        lblStatusBadge.getStyleClass().removeAll(
                "status-running", "status-finished", "status-paid",
                "status-canceled", "status-pending");
        String cls = switch (status) {
            case "RUNNING" -> "status-running";
            case "FINISHED" -> "status-finished";
            case "PAID" -> "status-paid";
            case "CANCELED" -> "status-canceled";
            default -> "status-pending";
        };
        lblStatusBadge.getStyleClass().add(cls);
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
                currentPrice = num(data.get("amount"));
                // Server giờ kèm bidderName trong push → cập nhật leader ngay
                // mà không cần round-trip BID_HISTORY.
                String bidderName = str(data, "bidderName");
                lblLeader.setText(bidderName.isBlank() ? "Chưa có" : bidderName);
                // Refresh prompt text với min next bid mới.
                txtBidAmount.setPromptText(
                        String.format("Tối thiểu %,.0f", currentPrice + minimumIncrement));
                loadBidHistory();
                flashLabel(lblCurrentPrice);
            });
        });

        model.addPushHandler("AUCTION_STATUS", data -> {
            if (!matchesAuction(data)) return;
            Platform.runLater(() -> handleStatusChanged(data));
        });

        model.addPushHandler("AUCTION_EXTENDED", data -> {
            if (!matchesAuction(data)) return;
            Platform.runLater(() -> {
                try { endTime = LocalDateTime.parse(str(data, "newEndTime")); }
                catch (Exception ignored) {}
                int extended = data.get("extendedSeconds") instanceof Number n ? n.intValue() : 0;
                lblError.setStyle("-fx-text-fill: #2563eb;");
                lblError.setText(extended > 0
                        ? "⏰ Phiên gia hạn thêm " + extended + " giây!"
                        : "⏰ Phiên đã được gia hạn!");
            });
        });
    }

    // =========== BID ===========

    @FXML
    private void handlePlaceBid() {
        BigDecimal amount = parseMoney(txtBidAmount.getText());
        if (amount == null || amount.signum() <= 0) {
            lblError.setText("Giá không hợp lệ");
            return;
        }

        lblError.setText("");
        btnPlaceBid.setDisable(true);

        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                // Gửi dạng String để server parse BigDecimal — giữ precision.
                // Nếu gửi qua double, JSON serialize có thể rơi vào "1.0E7" hoặc
                // mất số lẻ.
                model.sendRequest("PLACE_BID", Map.of(
                        "auctionId", auctionId, "amount", amount.toPlainString()));
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

    // =========== SETTLEMENT ===========

    /**
     * Xử lý push AUCTION_STATUS.
     * - FINISHED + mình là winner → hiện panel trả tiền / bỏ.
     * - FINISHED + không phải winner → chỉ disable bid.
     * - PAID / CANCELED → ẩn panel, hiển thị kết quả.
     */
    private void handleStatusChanged(Map<String, Object> data) {
        String status = str(data, "status");
        String leaderId = data.get("highestBidderId") == null ? null : str(data, "highestBidderId");
        applyStatusBadge(status, str(data, "displayStatus"));

        if ("FINISHED".equals(status)) {
            lblTimer.setText("Phiên đã kết thúc");
            btnPlaceBid.setDisable(true);
            txtBidAmount.setDisable(true);
            if (isMyId(leaderId)) {
                showSettlementPanel();
            } else {
                ClientApp.showInfo("Phiên đấu giá đã kết thúc!");
            }
        } else if ("PAID".equals(status)) {
            hideSettlementPanel();
            btnPlaceBid.setDisable(true);
            txtBidAmount.setDisable(true);
            ClientApp.showInfo("Phiên đã được thanh toán!");
        } else if ("CANCELED".equals(status)) {
            hideSettlementPanel();
            btnPlaceBid.setDisable(true);
            txtBidAmount.setDisable(true);
            ClientApp.showInfo("Phiên đã bị hủy.");
        }
    }

    private boolean isMyId(String id) {
        String myId = ClientModel.getInstance().getUserId();
        return id != null && id.equals(myId);
    }

    private void showSettlementPanel() {
        if (paneSettlement == null) return;
        double penalty = startingPrice * FORFEIT_PENALTY_RATE;
        lblSettleInfo.setText(String.format(
                "Số tiền phải trả: %,.0f VNĐ\nNếu bỏ, bạn sẽ mất %,.0f VNĐ phí phạt (%.0f%% giá khởi điểm) cho người bán.",
                currentPrice, penalty, FORFEIT_PENALTY_RATE * 100));
        lblSettleStatus.setText("");
        btnConfirmPay.setDisable(false);
        btnForfeit.setDisable(false);
        paneSettlement.setVisible(true);
        paneSettlement.setManaged(true);
        startSettleCountdown();
    }

    private void hideSettlementPanel() {
        if (paneSettlement == null) return;
        paneSettlement.setVisible(false);
        paneSettlement.setManaged(false);
        if (settleCountdown != null) settleCountdown.stop();
    }

    /**
     * Đếm ngược tới thời điểm server tự PAY. Tính từ endTime (khi push FINISHED đến,
     * server vừa transition xong nên endTime ~ thời điểm hiện tại).
     */
    private void startSettleCountdown() {
        if (settleCountdown != null) settleCountdown.stop();
        settleCountdown = new Timeline(new javafx.animation.KeyFrame(
                Duration.seconds(1), e -> updateSettleCountdown()));
        settleCountdown.setCycleCount(Timeline.INDEFINITE);
        settleCountdown.play();
        updateSettleCountdown();
    }

    private void updateSettleCountdown() {
        if (endTime == null) {
            lblSettleCountdown.setText("");
            return;
        }
        LocalDateTime autoPayAt = endTime.plusMinutes(AUTO_PAY_AFTER_MINUTES);
        long sec = ChronoUnit.SECONDS.between(LocalDateTime.now(), autoPayAt);
        if (sec <= 0) {
            lblSettleCountdown.setText("Đang tự động thanh toán...");
            if (settleCountdown != null) settleCountdown.stop();
            return;
        }
        long mins = sec / 60;
        long secs = sec % 60;
        lblSettleCountdown.setText(String.format(
                "⏰ Tự động thanh toán sau %02d:%02d", mins, secs));
    }

    @FXML
    private void handleConfirmPayment() {
        btnConfirmPay.setDisable(true);
        btnForfeit.setDisable(true);
        lblSettleStatus.setStyle("-fx-text-fill: #6b7280;");
        lblSettleStatus.setText("Đang gửi yêu cầu thanh toán...");

        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest("CONFIRM_PAYMENT", Map.of("auctionId", auctionId));
                Response res = model.waitForResponse("CONFIRM_PAYMENT", 5000);

                Platform.runLater(() -> {
                    if (res != null && res.isSuccess()) {
                        lblSettleStatus.setStyle("-fx-text-fill: #059669;");
                        lblSettleStatus.setText("✓ " + res.getMessage());
                    } else {
                        btnConfirmPay.setDisable(false);
                        btnForfeit.setDisable(false);
                        lblSettleStatus.setStyle("-fx-text-fill: #dc2626;");
                        lblSettleStatus.setText(res != null ? res.getMessage() : "Timeout");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnConfirmPay.setDisable(false);
                    btnForfeit.setDisable(false);
                    lblSettleStatus.setStyle("-fx-text-fill: #dc2626;");
                    lblSettleStatus.setText("Lỗi: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleForfeit() {
        // Xác nhận trước khi bỏ — chống bấm nhầm.
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Bạn chắc chắn bỏ phiên?\nSẽ mất %,.0f VNĐ phí phạt cho người bán.",
                        startingPrice * FORFEIT_PENALTY_RATE),
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        btnConfirmPay.setDisable(true);
        btnForfeit.setDisable(true);
        lblSettleStatus.setStyle("-fx-text-fill: #6b7280;");
        lblSettleStatus.setText("Đang gửi yêu cầu hủy...");

        new Thread(() -> {
            try {
                ClientModel model = ClientModel.getInstance();
                model.sendRequest("FORFEIT_AUCTION", Map.of("auctionId", auctionId));
                Response res = model.waitForResponse("FORFEIT_AUCTION", 5000);

                Platform.runLater(() -> {
                    if (res != null && res.isSuccess()) {
                        lblSettleStatus.setStyle("-fx-text-fill: #f59e0b;");
                        lblSettleStatus.setText("✓ " + res.getMessage());
                    } else {
                        btnConfirmPay.setDisable(false);
                        btnForfeit.setDisable(false);
                        lblSettleStatus.setStyle("-fx-text-fill: #dc2626;");
                        lblSettleStatus.setText(res != null ? res.getMessage() : "Timeout");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnConfirmPay.setDisable(false);
                    btnForfeit.setDisable(false);
                    lblSettleStatus.setStyle("-fx-text-fill: #dc2626;");
                    lblSettleStatus.setText("Lỗi: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleSetAutoBid() {
        BigDecimal maxBid = parseMoney(txtMaxBid.getText());
        BigDecimal incr = parseMoney(txtIncrementAuto.getText());
        if (maxBid == null || maxBid.signum() <= 0
                || incr == null || incr.signum() <= 0) {
            lblAutoBidStatus.setText("Nhập giá tối đa và bước nhảy");
            return;
        }

        new Thread(() -> {
            ClientModel model = ClientModel.getInstance();
            // Gửi String → server parse BigDecimal — xem comment ở handlePlaceBid.
            model.sendRequest("SET_AUTO_BID", Map.of(
                    "auctionId", auctionId,
                    "maxBid", maxBid.toPlainString(),
                    "increment", incr.toPlainString()));
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
                model.sendRequest("GET_PROFILE", Map.of());
                Response res = model.waitForResponse("GET_PROFILE", 5000);

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
        if (settleCountdown != null) settleCountdown.stop();
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

    /**
     * Parse số tiền VND user nhập. Dấu chấm và dấu phẩy đều coi là thousand
     * separator → "1.000.000", "1,000,000", "1000000" đều ra 1_000_000.
     * <p>VND không có thập phân nên không hỗ trợ phần lẻ. Trả {@code null} nếu
     * input rỗng hoặc không parse được — caller validate signum > 0 trước khi gửi.
     */
    private BigDecimal parseMoney(String s) {
        if (s == null) return null;
        String cleaned = s.trim().replace(".", "").replace(",", "").replace(" ", "");
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
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
