package com.auction;

import com.auction.model.entity.*;
import com.auction.model.enums.*;
import com.auction.server.service.AuctionService;

import java.util.*;

/**
 * Điểm vào của ứng dụng đấu giá chạy trên console.
 *
 * <p>Class này chịu trách nhiệm cho toàn bộ luồng tương tác với người dùng:
 * hiển thị menu, đọc dữ liệu nhập từ bàn phím, gọi các hàm nghiệp vụ trong
 * {@link AuctionService} và in kết quả ra màn hình. Nói cách khác, đây là
 * lớp điều phối giữa tầng giao diện dòng lệnh và tầng service.</p>
 *
 * <p>Biến {@code currentUser} quyết định trạng thái của chương trình:
 * nếu chưa đăng nhập thì chỉ hiển thị menu xác thực; nếu đã đăng nhập thì
 * chuyển sang menu chức năng tương ứng với vai trò hiện tại.</p>
 */
public class Main {

    /** Đường kẻ phân tách chung cho các màn hình console. */
    private static final String DIVIDER = "------------------------------------------------------------";
    /** Đường kẻ dùng cho các bảng dữ liệu để giữ cột dễ nhìn. */
    private static final String TABLE_DIVIDER = "--------------------------------------------------------------------------";

    /** Scanner dùng chung cho toàn bộ ứng dụng console. */
    private static final Scanner sc = new Scanner(System.in);
    /** Service trung tâm xử lý đăng nhập, tạo phiên, đặt giá và truy vấn dữ liệu. */
    private static final AuctionService service = AuctionService.getInstance();
    /** Người dùng đang đăng nhập; bằng {@code null} khi chưa xác thực. */
    private static User currentUser = null;

    /**
     * Khởi động chương trình, nạp dữ liệu mẫu và duy trì vòng lặp menu vô hạn.
     *
     * @param args tham số dòng lệnh, hiện tại chưa sử dụng
     */
    public static void main(String[] args) {
        printHeader("HỆ THỐNG ĐẤU GIÁ TRỰC TUYẾN");

        // Khởi tạo dữ liệu demo để người dùng có thể thao tác ngay sau khi chạy chương trình.
        service.seedData();
        System.out.println();

        // Vòng lặp chính: phân luồng theo trạng thái đăng nhập hiện tại.
        while (true) {
            if (currentUser == null) {
                showAuthMenu();
            } else {
                showMainMenu();
            }
        }
    }

    // ==================== ĐĂNG NHẬP / ĐĂNG KÝ ====================

    /**
     * Hiển thị menu trước đăng nhập.
     *
     * <p>Người dùng chỉ có 3 lựa chọn: đăng nhập, đăng ký hoặc thoát chương trình.
     * Mỗi lựa chọn sẽ được chuyển tới một hàm xử lý riêng để tách luồng rõ ràng.</p>
     */
    private static void showAuthMenu() {
        printSection("Đăng nhập / Đăng ký");
        printOption(1, "Đăng nhập");
        printOption(2, "Đăng ký tài khoản");
        printOption(0, "Thoát");
        printPrompt("Chọn");

        int choice = readInt();
        // switch dùng để kiểm tra giá trị của biến choice.
        // Mỗi case là một trường hợp cụ thể của choice mà chương trình cần xử lý.
        switch (choice) {
            // case 1: nếu người dùng nhập 1 thì chuyển sang chức năng đăng nhập.
            case 1 -> handleLogin();
            // case 2: nếu người dùng nhập 2 thì chuyển sang chức năng đăng ký.
            case 2 -> handleRegister();
            // case 0: nếu người dùng nhập 0 thì thoát chương trình.
            case 0 -> { printLine("Tạm biệt."); System.exit(0); }
        }
    }

    /**
     * Thu thập thông tin đăng nhập và xác thực với hệ thống.
     *
     * <p>Nếu xác thực thành công, {@code currentUser} sẽ được gán để chuyển ứng dụng
     * sang trạng thái đã đăng nhập. Nếu thất bại, thông báo lỗi từ service sẽ được in ra.</p>
     */
    private static void handleLogin() {
        printSection("Đăng nhập");
        printPrompt("Username");
        String username = sc.nextLine().trim();
        printPrompt("Password");
        String password = sc.nextLine().trim();

        try {
            currentUser = service.login(username, password);
            printSuccess(String.format("Xin chào %s (%s)",
                    currentUser.getUsername(), currentUser.getRole().getDisplayRole()));
        } catch (RuntimeException e) {
            printError(e.getMessage());
        }
    }

    /**
     * Tạo tài khoản mới cho bidder hoặc seller.
     *
     * <p>Với bidder, chương trình cho phép nhập số dư ban đầu. Nếu số dư nhập vào
     * không hợp lệ, hệ thống dùng giá trị mặc định để tài khoản có thể tham gia đấu giá.
     * Với seller, không cần thông tin số dư.</p>
     */
    private static void handleRegister() {
        printSection("Đăng ký tài khoản");
        printPrompt("Username");
        String username = sc.nextLine().trim();
        printPrompt("Password");
        String password = sc.nextLine().trim();

        if (username.isEmpty() || password.isEmpty()) {
            printError("Username và password không được để trống.");
            return;
        }

        printLine("Vai trò:");
        printOption(1, "Bidder");
        printOption(2, "Seller");
        printPrompt("Chọn");
        int roleChoice = readInt();

        Role role;
        double balance = 0;
        double revenue = 0;
        role = roleChoice == 1 ? Role.BIDDER : Role.SELLER;

        try {
            User user = service.register(username, password, role, balance, revenue);
            printSuccess(String.format("Tạo tài khoản thành công. ID: %d", user.getId()));
            printLine("Hãy đăng nhập để tiếp tục.");
        } catch (RuntimeException e) {
            printError(e.getMessage());
        }
    }

    // ==================== MENU CHÍNH ====================

    /**
     * Hiển thị menu chính sau đăng nhập.
     *
     * <p>Một số chức năng chỉ xuất hiện cho bidder hoặc seller. Ngoài việc hiển thị
     * theo role, phần {@code switch} bên dưới còn kiểm tra lại kiểu người dùng trước
     * khi gọi hàm xử lý để tránh thao tác sai quyền.</p>
     */
    private static void showMainMenu() {
        printSection(String.format("Menu chính - %s (%s)",
                currentUser.getUsername(), currentUser.getRole().getDisplayRole()));

        printOption(1, "Xem danh sách phiên đấu giá");
        printOption(2, "Xem chi tiết phiên");
        printOption(4, "Xem thông tin tài khoản");
        printOption(3, "Đặt giá");
        printOption(5, "Thêm sản phẩm");
        printOption(6, "Tạo phiên đấu giá");
        printOption(7, "Xem sản phẩm của tôi");
        printOption(8, "Đóng phiên");
        printOption(9, "Đăng xuất");
        printPrompt("Chọn");

        int choice = readInt();
        // switch này đóng vai trò điều hướng menu chính.
        // Tùy giá trị choice, chương trình sẽ gọi đúng chức năng tương ứng.
        switch (choice) {
            // case 1: xem danh sách tất cả phiên đấu giá.
            case 1 -> listAuctions();
            // case 2: xem thông tin chi tiết của một phiên đấu giá.
            case 2 -> viewAuctionDetail();
            // Các thao tác đặt giá chỉ dành cho bidder.
            // case 3: bidder đặt giá cho một phiên.
            case 3 -> { handlePlaceBid(); }
            // case 4: mọi role đều có thể xem thông tin tài khoản của chính mình.
            case 4 -> showAccountInfo();
            // Các thao tác quản lý hàng hóa và phiên đấu giá chỉ dành cho seller.
            // case 5: seller thêm sản phẩm mới.
            case 5 -> { handleCreateItem(); }
            // case 6: seller tạo phiên đấu giá từ sản phẩm đã có.
            case 6 -> { handleCreateAuction(); }
            // case 7: seller xem danh sách sản phẩm của mình.
            case 7 -> { listMyItems(); }
            // case 8: seller đóng một phiên đấu giá.
            case 8 -> { handleCloseAuction(); }
            // case 9: đăng xuất khỏi tài khoản hiện tại.
            case 9 -> { currentUser = null; printSuccess("Đã đăng xuất."); }
        }
    }

    /**
     * Hiển thị thông tin tài khoản của user đang đăng nhập.
     *
     * <p>Bidder sẽ thấy số dư ví, phần đang giữ chỗ ở các auction đang dẫn đầu
     * và số dư khả dụng còn lại. Seller thấy doanh thu hiện tại. Admin hiện chỉ có thông tin cơ bản.</p>
     */
    private static void showAccountInfo() {
        printSection("Thông tin tài khoản");
        printField("Username", currentUser.getUsername());
        printField("Vai trò", currentUser.getRole().getDisplayRole());
        printField("Trạng thái", currentUser.getUserStatus().getDisplayStatus());
        double reserved = service.getReservedBalance(currentUser.getId());
        double available = service.getAvailableBalance(currentUser.getId());
        printField("Số dư ví", formatCurrency(currentUser.getBalance()));
        printField("Đang giữ chỗ", formatCurrency(reserved));
        printField("Số dư khả dụng", formatCurrency(available));
        printField("Doanh thu", formatCurrency(currentUser.getRevenue()));
    }

    // ==================== XEM PHIÊN ====================

    /**
     * In danh sách tất cả phiên đấu giá hiện có dưới dạng bảng ngắn gọn.
     *
     * <p>Thông tin hiển thị gồm tên sản phẩm, giá hiện tại, số lượt bid và trạng thái.
     * Tên sản phẩm dài sẽ được cắt bớt để giữ bố cục bảng ổn định trên console.</p>
     */
    private static void listAuctions() {
        Collection<Auction> auctions = service.getAllAuctions();
        if (auctions.isEmpty()) { printLine("Chưa có phiên đấu giá."); return; }

        printSection("Danh sách phiên đấu giá");
        System.out.printf("  %-4s %-24s %16s %6s  %-16s%n",
                "ID", "Sản phẩm", "Giá hiện tại", "Bids", "Trạng thái");
        System.out.println("  " + TABLE_DIVIDER);
        for (Auction a : auctions) {
            Item item = service.getItem(a.getItemId());
            String name = truncate(item != null ? item.getName() : null, 24);

            System.out.printf("  %-4d %-24s %16s %6d  %-16s%n",
                    a.getId(), name, formatCurrency(a.getCurrentPrice()), a.getTotalBids(),
                    truncate(a.getStatus().getDisplayStatus(), 16));
        }
    }

    /**
     * Hiển thị thông tin chi tiết của một phiên đấu giá.
     *
     * <p>Ngoài dữ liệu chính như sản phẩm, giá và thời gian, hàm còn truy xuất lịch sử
     * đặt giá để người dùng biết ai đang dẫn đầu và phiên đã diễn ra như thế nào.</p>
     */
    private static void viewAuctionDetail() {
        printSection("Chi tiết phiên đấu giá");
        printPrompt("Auction ID");
        int id = readInt();
        Auction a = service.getAuction(id);
        if (a == null) { printError("Không tìm thấy phiên đấu giá."); return; }

        Item item = service.getItem(a.getItemId());
        User seller = service.getUser(a.getSellerId());

        printLine("Thông tin phiên #" + a.getId());
        printField("Sản phẩm", item != null ? item.getName() : "N/A");
        printField("Mô tả", item != null && !item.getDescription().isBlank() ? item.getDescription() : "-");
        printField("Danh mục", item != null ? item.getCategory().getDisplayName() : "-");
        printField("Người bán", seller != null ? seller.getUsername() : "-");
        printField("Giá khởi điểm", formatCurrency(a.getStartingPrice()));
        printField("Giá hiện tại", formatCurrency(a.getCurrentPrice()));
        printField("Bước nhảy", formatCurrency(a.getMinimumIncrement()));
        printField("Tổng bids", String.valueOf(a.getTotalBids()));

        Integer highestBidderId = a.getHighestBidderIdOrNull();
        if (highestBidderId != null) {
            User leader = service.getUser(highestBidderId);
            printField("Dẫn đầu", leader != null ? leader.getUsername() : "ID " + highestBidderId);
        } else {
            printField("Dẫn đầu", "Chưa có người đặt giá");
        }

        printField("Trạng thái", a.getStatus().getDisplayStatus());
        printField("Thời gian", a.getStartTime() + " -> " + a.getEndTime());
        if (a.isActive()) printField("Còn lại", a.getRemainedSeconds() + " giây");

        // Lịch sử bid được in từ mới nhất về cũ nhất để thấy ngay lượt đang dẫn đầu.
        List<BidTransaction> bids = service.getBidHistory(id);
        if (!bids.isEmpty()) {
            printLine("Lịch sử đặt giá:");
            System.out.printf("  %-20s %-14s %16s%n", "Thời gian", "Người đặt", "Số tiền");
            System.out.println("  " + TABLE_DIVIDER);
            for (int i = bids.size() - 1; i >= 0; i--) {
                BidTransaction b = bids.get(i);
                User bidder = service.getUser(b.getBidderId());
                String bName = bidder != null ? bidder.getUsername() : "ID " + b.getBidderId();
                String marker = (i == bids.size() - 1) ? " (dẫn đầu)" : "";
                System.out.printf("  %-20s %-14s %16s%s%n",
                        b.getTimestamp(), truncate(bName, 14), formatCurrency(b.getBidAmount()), marker);
            }
        } else {
            printLine("Chưa có lịch sử đặt giá.");
        }
    }

    // ==================== BIDDER: ĐẶT GIÁ ====================

    /**
     * Cho bidder nhập giá mới và gửi yêu cầu đặt giá tới service.
     *
     * <p>Phần giao diện chỉ đảm nhiệm hiển thị mức giá tối thiểu hợp lệ; toàn bộ việc
     * kiểm tra quyền, trạng thái phiên, số dư và mức giá thực tế vẫn do service xác nhận.</p>
     */
    private static void handlePlaceBid() {
        printSection("Đặt giá");
        printPrompt("Auction ID");
        int auctionId = readInt();

        Auction a = service.getAuction(auctionId);
        if (a == null) { printError("Phiên không tồn tại."); return; }

        Item item = service.getItem(a.getItemId());
        printField("Sản phẩm", item != null ? item.getName() : "N/A");
        printField("Giá hiện tại", formatCurrency(a.getCurrentPrice()));
        printField("Bước nhảy", formatCurrency(a.getMinimumIncrement()));
        printField("Giá tối thiểu", formatCurrency(a.getCurrentPrice() + a.getMinimumIncrement()));
        printPrompt("Nhập giá");
        double amount = readDouble();

        try {
            BidTransaction bid = service.placeBid(auctionId, currentUser.getId(), amount);
            printSuccess("Đặt giá thành công: " + formatCurrency(bid.getBidAmount()));
        } catch (RuntimeException e) {
            printError(e.getMessage());
        }
    }

    // ==================== SELLER: SẢN PHẨM ====================

    /**
     * Tạo một sản phẩm mới để seller có thể đưa vào phiên đấu giá sau đó.
     *
     * <p>Một số nhóm hàng yêu cầu thêm thuộc tính chuyên biệt. Các thuộc tính này
     * được gom vào {@code attrs} để service có thể xử lý linh hoạt theo loại sản phẩm.</p>
     */
    private static void handleCreateItem() {
        printSection("Thêm sản phẩm");

        ItemCategory[] cats = ItemCategory.values();
        printLine("Danh mục:");
        for (int i = 0; i < cats.length; i++)
            printOption(i + 1, cats[i].getDisplayName());
        printPrompt("Chọn loại");
        int catIdx = readInt();
        if (catIdx < 1 || catIdx > cats.length) { printError("Lựa chọn không hợp lệ."); return; }
        ItemCategory category = cats[catIdx - 1];

        printPrompt("Tên");
        String name = sc.nextLine().trim();
        printPrompt("Mô tả");
        String desc = sc.nextLine().trim();
        printPrompt("Giá khởi điểm (VNĐ)");
        double price = readDouble();

        printLine("Tình trạng: 1. Mới  2. Cũ");
        printPrompt("Chọn");
        ItemCondition cond = (readInt() == 2) ? ItemCondition.USED : ItemCondition.NEW;

        // Thuộc tính mở rộng theo từng category, giúp mô tả sản phẩm sát thực tế hơn.
        Map<String, String> attrs = new HashMap<>();
        // switch ở đây dùng để phân loại sản phẩm.
        // Mỗi case sẽ yêu cầu nhập thêm những thuộc tính riêng cho từng loại hàng.
        switch (category) {
            // case ELECTRONICS: đồ điện tử cần hãng, dòng sản phẩm, thời gian bảo hành.
            case ELECTRONICS -> {
                printPrompt("Hãng"); attrs.put("brand", sc.nextLine().trim());
                printPrompt("Dòng sản phẩm"); attrs.put("model", sc.nextLine().trim());
                printPrompt("Bảo hành (tháng)"); attrs.put("warrantyMonths", String.valueOf(readInt()));
            }
            // case ART: tác phẩm nghệ thuật cần tác giả và năm sáng tác.
            case ART -> {
                printPrompt("Tác giả"); attrs.put("artist", sc.nextLine().trim());
                printPrompt("Năm"); attrs.put("year", String.valueOf(readInt()));
            }
            // case VEHICLE: xe cộ cần nhiều thông tin kỹ thuật và tình trạng sử dụng hơn.
            case VEHICLE -> {
                printPrompt("Hãng"); attrs.put("brand", sc.nextLine().trim());
                printPrompt("Dòng xe"); attrs.put("model", sc.nextLine().trim());
                printPrompt("Năm sản xuất"); attrs.put("manufactureYear", String.valueOf(readInt()));
                printPrompt("Số km đã đi"); attrs.put("mileage", String.valueOf(readInt()));
                printPrompt("Màu"); attrs.put("color", sc.nextLine().trim());
                printPrompt("Nhiên liệu"); attrs.put("fuelType", sc.nextLine().trim());
                printPrompt("Hộp số"); attrs.put("transmission", sc.nextLine().trim());
            }
            // default: các loại còn lại không cần nhập thuộc tính bổ sung.
            default -> {}
        }

        try {
            Item item = service.createItem(category, name, desc, price, currentUser.getId(), cond, attrs);
            printSuccess(String.format("Đã thêm sản phẩm [%d] %s - %s",
                    item.getId(), item.getName(), formatCurrency(item.getStartingPrice())));
        } catch (RuntimeException e) {
            printError(e.getMessage());
        }
    }

    // ==================== SELLER: PHIÊN ĐẤU GIÁ ====================

    /**
     * Tạo phiên đấu giá cho một sản phẩm thuộc seller hiện tại.
     *
     * <p>Người bán phải chọn một sản phẩm đã có sẵn, sau đó nhập thời lượng và bước nhảy.
     * Nếu dữ liệu nhập không hợp lệ, chương trình sẽ thay bằng giá trị mặc định an toàn.</p>
     */
    private static void handleCreateAuction() {
        printSection("Tạo phiên đấu giá");
        listMyItems();
        printPrompt("Item ID");
        int itemId = readInt();
        printPrompt("Thời gian (phút)");
        int duration = readInt(); if (duration <= 0) duration = 30;
        printPrompt("Bước nhảy (VNĐ)");
        double incr = readDouble(); if (incr <= 0) incr = 100_000;

        try {
            Auction a = service.createAuction(itemId, currentUser.getId(), duration, incr);
            printSuccess(String.format("Đã tạo phiên #%d. Kết thúc lúc %s", a.getId(), a.getEndTime()));
        } catch (RuntimeException e) {
            printError(e.getMessage());
        }
    }

    /**
     * Liệt kê toàn bộ sản phẩm do seller hiện tại sở hữu.
     *
     * <p>Danh sách này thường được gọi trước khi tạo phiên đấu giá để người bán
     * dễ chọn đúng {@code itemId} cần đưa lên sàn.</p>
     */
    private static void listMyItems() {
        List<Item> my = service.getItemsBySeller(currentUser.getId());
        if (my.isEmpty()) { printLine("Bạn chưa có sản phẩm."); return; }
        printLine("Sản phẩm của bạn:");
        System.out.printf("  %-4s %-24s %16s  %-18s%n",
                "ID", "Tên", "Giá khởi điểm", "Danh mục");
        System.out.println("  " + TABLE_DIVIDER);
        for (Item i : my)
            System.out.printf("  %-4d %-24s %16s  %-18s%n",
                    i.getId(),
                    truncate(i.getName(), 24),
                    formatCurrency(i.getStartingPrice()),
                    truncate(i.getCategory().getDisplayName(), 18));
    }

    /**
     * Đóng thủ công một phiên đấu giá do seller quản lý.
     *
     * <p>Sau khi đóng, chương trình thông báo ngay kết quả thắng cuộc nếu đã có người bid,
     * giúp seller nắm được ai là người mua tạm thời và mức giá chốt hiện tại.</p>
     */
    private static void handleCloseAuction() {
        printSection("Đóng phiên đấu giá");
        printPrompt("Auction ID cần đóng");
        int id = readInt();
        try {
            // Console app cũng đi qua cùng rule phân quyền của service như server API.
            service.closeAuction(id, currentUser.getId());
            Auction a = service.getAuction(id);
            printSuccess("Đã đóng phiên #" + id);
            Integer highestBidderId = a.getHighestBidderIdOrNull();
            if (highestBidderId != null) {
                User w = service.getUser(highestBidderId);
                printField("Người thắng", w != null ? w.getUsername() : "?");
                printField("Giá chốt", formatCurrency(a.getCurrentPrice()));
            } else {
                printLine("Phiên không có người đặt giá.");
            }
        } catch (RuntimeException e) {
            printError(e.getMessage());
        }
    }

    // ==================== HELPERS ====================

    /**
     * Các helper bên dưới giúp chuẩn hóa cách hiển thị để giao diện console gọn và đồng nhất hơn.
     */

    private static void printHeader(String title) {
        System.out.println(title);
        System.out.println(DIVIDER);
    }

    private static void printSection(String title) {
        System.out.println();
        printHeader(title);
    }

    private static void printOption(int number, String label) {
        System.out.printf("  %d. %s%n", number, label);
    }

    private static void printPrompt(String label) {
        System.out.printf("  %s: ", label);
    }

    private static void printField(String label, String value) {
        System.out.printf("  %-14s: %s%n", label, value);
    }

    private static void printLine(String message) {
        System.out.println("  " + message);
    }

    private static void printSuccess(String message) {
        printLine("[OK] " + message);
    }

    private static void printError(String message) {
        printLine("[Lỗi] " + message);
    }

    private static String formatCurrency(double amount) {
        return String.format("%,.0f VNĐ", amount);
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Đọc số nguyên từ console.
     *
     * @return giá trị số nguyên; trả về {@code -1} nếu người dùng nhập sai định dạng
     */
    private static int readInt() {
        try { return Integer.parseInt(sc.nextLine().trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    /**
     * Đọc số thực từ console.
     *
     * <p>Hàm cho phép người dùng nhập dấu phẩy phân tách hàng nghìn, sau đó sẽ loại bỏ
     * trước khi chuyển sang kiểu {@code double}.</p>
     *
     * @return giá trị số thực; trả về {@code -1} nếu dữ liệu không hợp lệ
     */
    private static double readDouble() {
        try { return Double.parseDouble(sc.nextLine().trim().replace(",", "")); }
        catch (NumberFormatException e) { return -1; }
    }
}
