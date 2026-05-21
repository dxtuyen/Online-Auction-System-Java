package com.auction.client.controller;

import com.auction.client.ClientApp;
import com.auction.client.model.ClientModel;
import com.auction.client.util.ImageCacheService;
import com.auction.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.*;

/**
 * Controller hiển thị danh sách phiên đấu giá dưới dạng card grid (FlowPane).
 *
 * <p>Mỗi auction là một card click-được. Filter/refresh re-render lại toàn bộ pane.</p>
 */
public class AuctionListController {

    @FXML private FlowPane paneAuctions;
    @FXML private Label lblEmpty;
    @FXML private TextField txtSearch;
    @FXML private Label lblUserInfo;
    @FXML private Button btnCreateAuction;
    @FXML private ImageView imgHeaderAvatar;

    /** Dữ liệu gốc — filter dựng lại view từ list này. */
    private final List<Map<String, Object>> allData = new ArrayList<>();

    /** Số card/hàng đã render lần cuối; chỉ re-render khi count đổi để tránh loop khi resize. */
    private int lastCardsPerRow = -1;

    // Khoá số cột trong [MIN_CARDS, MAX_CARDS]. Ngoài khoảng này card sẽ dãn/co cho khít.
    private static final int MIN_CARDS_PER_ROW = 4;
    private static final int MAX_CARDS_PER_ROW = 6;
    /** Width tối thiểu để cho phép thêm 1 cột nữa — chọn 185 để 6 cột xuất hiện quanh ~1230px. */
    private static final double CARD_WIDTH_THRESHOLD = 240;

    @FXML
    private void initialize() {
        ClientModel model = ClientModel.getInstance();
        lblUserInfo.setText(String.format("Xin chào, %s (%s)",
                model.getUsername(), model.getRole()));
        imgHeaderAvatar.setClip(new Circle(16, 16, 16));
        loadProfileSummary();

        // ADMIN không có quyền bán; mọi role khác (hiện tại chỉ NORMAL) đều thấy nút tạo phiên.
        if (!"ADMIN".equals(model.getRole())) btnCreateAuction.setVisible(true);

        // Filter live khi gõ — re-render với keyword hiện tại.
        txtSearch.textProperty().addListener((obs, old, val) -> render(val));

        // Khi cửa sổ thay đổi width, tính lại số card/hàng để card kéo dãn lấp đầy
        // — tránh khoảng trống lớn bên phải. Chỉ render lại khi count thay đổi thực sự
        // (lúc resize liên tục, width nhảy từng pixel nhưng count đổi không thường xuyên).
        paneAuctions.widthProperty().addListener((obs, old, val) -> {
            int newCount = computeCardsPerRow(val.doubleValue());
            if (newCount != lastCardsPerRow) {
                lastCardsPerRow = newCount;
                render(txtSearch.getText());
            }
        });

        handleRefresh();
    }

    private int computeCardsPerRow(double width) {
        double padding = paneAuctions.getPadding().getLeft() + paneAuctions.getPadding().getRight();
        double hgap = paneAuctions.getHgap();
        double usable = width - padding;
        if (usable <= 0) return MIN_CARDS_PER_ROW;
        int n = (int) Math.floor((usable + hgap) / (CARD_WIDTH_THRESHOLD + hgap));
        // Khoá vào [MIN, MAX] — narrow vẫn giữ 4, wide tối đa 6 dù màn có rộng đến đâu.
        return Math.max(MIN_CARDS_PER_ROW, Math.min(MAX_CARDS_PER_ROW, n));
    }

    private double computeCardWidth(int cardsPerRow) {
        double padding = paneAuctions.getPadding().getLeft() + paneAuctions.getPadding().getRight();
        double hgap = paneAuctions.getHgap();
        double width = paneAuctions.getWidth();
        if (width <= 0) width = 880;
        // Không clamp — để card kéo dãn lấp đầy width khi user mở rộng cửa sổ với 6 cột.
        return Math.max(120, Math.floor((width - padding - (cardsPerRow - 1) * hgap) / cardsPerRow));
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
                        render(txtSearch.getText());
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> ClientApp.showError("Lỗi: " + e.getMessage()));
            }
        }).start();
    }

    private void render(String keyword) {
        paneAuctions.getChildren().clear();
        int cardsPerRow = computeCardsPerRow(paneAuctions.getWidth());
        lastCardsPerRow = cardsPerRow;
        double cardWidth = computeCardWidth(cardsPerRow);

        String low = keyword == null ? "" : keyword.trim().toLowerCase();
        int rendered = 0;
        for (Map<String, Object> row : allData) {
            if (!low.isEmpty()
                    && !str(row, "itemName").toLowerCase().contains(low)
                    && !str(row, "auctionId").contains(low)) {
                continue;
            }
            paneAuctions.getChildren().add(buildCard(row, cardWidth));
            rendered++;
        }
        lblEmpty.setVisible(rendered == 0);
        lblEmpty.setManaged(rendered == 0);
    }

    /** Một card auction — ảnh trên, thông tin + giá ở dưới. Click mở bidding. */
    private Node buildCard(Map<String, Object> row, double cardWidth) {
        VBox card = new VBox(6);
        card.getStyleClass().add("auction-card");
        card.setPrefWidth(cardWidth);
        card.setPadding(new Insets(8));
        card.setCursor(Cursor.HAND);

        // Ảnh sản phẩm — Rectangle placeholder, ImageView chồng lên trên.
        double imgWidth = cardWidth - 16;   // trừ padding 2 bên
        double imgHeight = Math.round(imgWidth * 0.7);   // tỉ lệ ~10:7
        Rectangle bg = new Rectangle(imgWidth, imgHeight, Color.web("#e5e7eb"));
        bg.setArcWidth(8);
        bg.setArcHeight(8);
        ImageView img = new ImageView();
        img.setFitWidth(imgWidth);
        img.setFitHeight(imgHeight);
        img.setPreserveRatio(false);
        StackPane imageBox = new StackPane(bg, img);
        String imageUrl = str(row, "imageUrl");
        if (!imageUrl.isBlank()) {
            ImageCacheService.getInstance().loadAsync(imageUrl, img::setImage);
        }

        Label name = new Label(str(row, "itemName"));
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        name.setWrapText(true);
        name.setMaxWidth(imgWidth);

        Label category = new Label(str(row, "itemCategory"));
        category.getStyleClass().add("label-muted");

        Label price = new Label(formatMoney(row.get("currentPrice")));
        price.getStyleClass().add("label-price");
        price.setStyle("-fx-font-size: 15px;");

        Label bids = new Label("Lượt bid: " + str(row, "totalBids"));
        bids.getStyleClass().add("label-muted");

        Label status = new Label(str(row, "displayStatus"));
        status.getStyleClass().add("status-badge");
        status.getStyleClass().add(statusStyleClass(str(row, "status")));

        HBox footer = new HBox(8, bids, new Region(), status);
        HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(imageBox, name, category, price, footer);
        String auctionId = str(row, "auctionId");
        card.setOnMouseClicked(e -> openBidding(auctionId));
        return card;
    }

    /** Map trạng thái → CSS class — đổi màu badge. */
    private static String statusStyleClass(String status) {
        return switch (status) {
            case "RUNNING" -> "status-running";
            case "FINISHED" -> "status-finished";
            case "PAID" -> "status-paid";
            case "CANCELED" -> "status-canceled";
            default -> "status-pending";
        };
    }

    private void openBidding(String auctionId) {
        ClientApp.switchSceneWithData("bidding.fxml", ctrl -> {
            ((BiddingController) ctrl).setAuctionId(auctionId);
        });
    }

    @FXML
    private void handleLogout() {
        // disconnect() gọi socket/reader/writer.close() — I/O đồng bộ.
        // Nếu chạy trên FX thread, UI sẽ treo (đặc biệt Windows + writer.close flush).
        // Đẩy sang background; switchScene chỉ chạy SAU KHI disconnect xong để login
        // tiếp theo không bị race với socket đang đóng.
        new Thread(() -> {
            ClientModel.getInstance().disconnect();
            Platform.runLater(() -> ClientApp.switchScene("login.fxml"));
        }, "logout-cleanup").start();
    }

    @FXML
    private void handleViewAccount() {
        ClientApp.switchSceneWithData("profile.fxml", ctrl ->
                ((ProfileController) ctrl).setBackFxml("auction_list.fxml"));
    }

    @FXML
    private void goToSellerDashboard() {
        ClientApp.switchScene("seller_dashboard.fxml");
    }

    private void loadProfileSummary() {
        requestProfile(data -> {
            lblUserInfo.setText(formatProfileSummary(data));
            String avatarUrl = data.get("avatarUrl") == null ? null : data.get("avatarUrl").toString();
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                ImageCacheService.getInstance().loadAsync(avatarUrl, imgHeaderAvatar::setImage);
            }
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

    /**
     * Profile dùng field {@code balance} (số dư có thể chi) và {@code revenue} (doanh thu bán).
     * Hệ thống hiện chưa reserve balance lúc bid nên không có concept available/reserved riêng.
     */
    private String formatProfileSummary(Map<String, Object> data) {
        String base = String.format("Xin chào, %s (%s)", str(data, "username"), str(data, "displayRole"));
        if ("ADMIN".equals(str(data, "role"))) return base;
        return base + " | Số dư: " + formatMoney(data.get("balance"));
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
