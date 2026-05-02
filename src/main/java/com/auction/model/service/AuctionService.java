package com.auction.service;

import com.auction.model.entity.*;
import com.auction.model.enums.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service trung tâm của hệ thống đấu giá.
 *
 * <p>Class này đóng vai trò tầng nghiệp vụ chính: quản lý người dùng, sản phẩm,
 * phiên đấu giá và lịch sử đặt giá. Toàn bộ thao tác từ giao diện console sẽ gọi
 * vào đây để xử lý thay vì thao tác trực tiếp với dữ liệu.</p>
 *
 * <p>Class được thiết kế theo mô hình Singleton, nghĩa là trong suốt thời gian
 * chạy chương trình chỉ tồn tại một đối tượng {@code AuctionService} duy nhất.
 * Nhờ đó dữ liệu được quản lý tập trung và nhất quán.</p>
 */
public class AuctionService {

    /** Thể hiện duy nhất của service trong toàn hệ thống. */
    private static AuctionService instance;

    /**
     * Trả về instance duy nhất của {@link AuctionService}.
     *
     * <p>Từ khóa {@code synchronized} giúp phương thức này an toàn nếu có nhiều
     * luồng cùng gọi tại một thời điểm.</p>
     *
     * @return instance duy nhất của service
     */
    public static synchronized AuctionService getInstance() {
        if (instance == null) instance = new AuctionService();
        return instance;
    }

    /** Kho lưu toàn bộ người dùng, truy cập nhanh theo ID. */
    private final Map<Integer, User> users = new HashMap<>();
    /** Kho lưu toàn bộ sản phẩm đã được tạo trong hệ thống. */
    private final Map<Integer, Item> items = new HashMap<>();
    /** Kho lưu toàn bộ phiên đấu giá, mỗi phiên được gắn với một ID duy nhất. */
    private final Map<Integer, Auction> auctions = new HashMap<>();
    /** Lưu lịch sử các lần đặt giá theo từng phiên đấu giá. */
    private final Map<Integer, List<BidTransaction>> bidHistory = new HashMap<>();

    /** Bộ đếm tự tăng cho ID người dùng. */
    private final AtomicInteger userIdCounter = new AtomicInteger(0);
    /** Bộ đếm tự tăng cho ID sản phẩm. */
    private final AtomicInteger itemIdCounter = new AtomicInteger(0);
    /** Bộ đếm tự tăng cho ID phiên đấu giá. */
    private final AtomicInteger auctionIdCounter = new AtomicInteger(0);
    /** Bộ đếm tự tăng cho ID giao dịch đặt giá. */
    private final AtomicInteger bidIdCounter = new AtomicInteger(0);

    /** Constructor private để đảm bảo class chỉ được khởi tạo qua Singleton. */
    private AuctionService() {}

    // ======================== USER ========================

    /**
     * Đăng ký một tài khoản mới theo vai trò được chọn.
     *
     * <p>Tùy theo {@code role}, service sẽ tạo đúng kiểu đối tượng con của
     * {@link User} như {@link Bidder}, {@link Seller} hoặc {@link Admin}.</p>
     *
     * @param username tên đăng nhập
     * @param password mật khẩu
     * @param role vai trò tài khoản
     * @param extraValue giá trị phụ, ví dụ số dư ban đầu của bidder
     * @return đối tượng user vừa được tạo
     */
    public User register(String username, String password, Role role, double extraValue) {
        // Kiểm tra username đã tồn tại hay chưa để tránh trùng tài khoản.
        for (User u : users.values()) {
            if (u.getUsername().equals(username)) {
                throw new RuntimeException("Username '" + username + "' đã được sử dụng!");
            }
        }

        int id = userIdCounter.incrementAndGet();
        // switch expression giúp tạo đúng loại user theo vai trò được chọn.
        User user = switch (role) {
            case BIDDER -> new Bidder(id, username, password, extraValue);
            case SELLER -> new Seller(id, username, password, extraValue);
            case ADMIN  -> new Admin(id, username, password, extraValue);
        };

        // Sau khi tạo thành công, user được lưu vào kho dữ liệu chính.
        users.put(id, user);
        return user;
    }

    /**
     * Xác thực đăng nhập bằng username và password.
     *
     * <p>Nếu tài khoản tồn tại, mật khẩu đúng và không bị khóa, phương thức sẽ
     * trả về đối tượng user tương ứng. Ngược lại sẽ ném ra lỗi mô tả nguyên nhân.</p>
     *
     * @param username tên đăng nhập
     * @param password mật khẩu
     * @return user đăng nhập thành công
     */
    public User login(String username, String password) {
        for (User u : users.values()) {
            if (u.getUsername().equals(username)) {
                if (u.getPassword().equals(password)) {
                    // Người dùng bị khóa sẽ không được phép đăng nhập.
                    if (u.getUserStatus() == UserStatus.BANNED) {
                        throw new RuntimeException("Tài khoản đã bị khóa!");
                    }
                    return u;
                } else {
                    throw new RuntimeException("Sai mật khẩu!");
                }
            }
        }
        throw new RuntimeException("Không tìm thấy user: " + username);
    }

    /**
     * Lấy thông tin người dùng theo ID.
     *
     * @param id ID người dùng
     * @return user tương ứng hoặc {@code null} nếu không tồn tại
     */
    public User getUser(int id) { return users.get(id); }

    // ======================== ITEM ========================

    /**
     * Tạo mới một sản phẩm để seller có thể đưa lên đấu giá.
     *
     * <p>Phương thức này kiểm tra người tạo có đúng là seller hay không,
     * sau đó dùng {@link ItemFactory} để sinh ra đúng loại sản phẩm theo category.</p>
     *
     * @param category loại sản phẩm
     * @param name tên sản phẩm
     * @param description mô tả sản phẩm
     * @param startingPrice giá khởi điểm
     * @param sellerId ID người bán
     * @param condition tình trạng sản phẩm
     * @param specificAttributes các thuộc tính riêng theo từng loại hàng
     * @return sản phẩm vừa tạo
     */
    public Item createItem(ItemCategory category, String name, String description,
                           double startingPrice, int sellerId, ItemCondition condition,
                           Map<String, String> specificAttributes) {
        User seller = users.get(sellerId);
        // Chỉ seller hợp lệ mới được phép tạo sản phẩm.
        if (seller == null || seller.getRole() != Role.SELLER) {
            throw new RuntimeException("Seller không tồn tại!");
        }

        int id = itemIdCounter.incrementAndGet();
        // Factory tách riêng logic tạo item để hỗ trợ nhiều loại sản phẩm khác nhau.
        Item item = ItemFactory.createItem(category, name, description, startingPrice,
                sellerId, new ArrayList<>(), condition, specificAttributes);
        item.setId(id);
        items.put(id, item);
        return item;
    }

    /**
     * Lấy sản phẩm theo ID.
     *
     * @param id ID sản phẩm
     * @return item tương ứng hoặc {@code null} nếu không tìm thấy
     */
    public Item getItem(int id) { return items.get(id); }

    /**
     * Lấy danh sách sản phẩm thuộc về một seller cụ thể.
     *
     * @param sellerId ID người bán
     * @return danh sách sản phẩm của seller đó
     */
    public List<Item> getItemsBySeller(int sellerId) {
        List<Item> result = new ArrayList<>();
        for (Item item : items.values()) {
            if (item.getSellerId() == sellerId) result.add(item);
        }
        return result;
    }

    // ======================== AUCTION ========================

    /**
     * Tạo một phiên đấu giá mới cho sản phẩm của seller.
     *
     * <p>Mỗi phiên sẽ có thời điểm bắt đầu là hiện tại, thời điểm kết thúc được tính
     * bằng số phút truyền vào, đồng thời trạng thái ban đầu sẽ chuyển sang {@code RUNNING}.</p>
     *
     * @param itemId ID sản phẩm
     * @param sellerId ID người bán
     * @param durationMinutes thời lượng phiên tính theo phút
     * @param minimumIncrement bước nhảy tối thiểu cho mỗi lần đặt giá
     * @return phiên đấu giá vừa tạo
     */
    // minimumIncrement là tiền nhỏ nhất cho món hàng
    public Auction createAuction(int itemId, int sellerId, int durationMinutes,
                                 double minimumIncrement) {
        Item item = items.get(itemId);
        if (item == null) throw new RuntimeException("Sản phẩm không tồn tại: " + itemId);
        // Seller chỉ được tạo phiên cho chính sản phẩm của mình.
        if (item.getSellerId() != sellerId) throw new RuntimeException("Bạn không sở hữu sản phẩm này!");

        int id = auctionIdCounter.incrementAndGet();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusMinutes(durationMinutes);

        // Phiên mới lấy giá mở đầu từ giá khởi điểm của sản phẩm.
        Auction auction = new Auction(itemId, sellerId, start, end,
                item.getStartingPrice(), minimumIncrement);
        auction.setId(id);
        auction.transitionTo(AuctionStatus.RUNNING);

        // Đồng thời khởi tạo luôn danh sách lịch sử bid rỗng cho phiên này.
        auctions.put(id, auction);
        bidHistory.put(id, new ArrayList<>());
        return auction;
    }

    /**
     * Đóng một phiên đấu giá thủ công.
     *
     * @param auctionId ID phiên đấu giá cần đóng
     */
    public void closeAuction(int auctionId) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) throw new RuntimeException("Phiên không tồn tại: " + auctionId);
        auction.transitionTo(AuctionStatus.FINISHED);
    }

    /**
     * Lấy phiên đấu giá theo ID.
     *
     * @param id ID phiên đấu giá
     * @return auction tương ứng hoặc {@code null} nếu không tồn tại
     */
    public Auction getAuction(int id) { return auctions.get(id); }

    /**
     * Lấy toàn bộ phiên đấu giá hiện có trong hệ thống.
     *
     * @return collection chứa tất cả auction
     */
    public Collection<Auction> getAllAuctions() { return auctions.values(); }

    // ======================== BID ========================

    /**
     * Thực hiện một lượt đặt giá mới cho phiên đấu giá.
     *
     * <p>Đây là phương thức nghiệp vụ quan trọng nhất của hệ thống. Nó kiểm tra:
     * sự tồn tại của phiên, trạng thái phiên, thời gian kết thúc, mức giá tối thiểu,
     * và tính hợp lệ của bidder trước khi cập nhật dữ liệu.</p>
     *
     * @param auctionId ID phiên đấu giá
     * @param bidderId ID người đặt giá
     * @param bidAmount số tiền đặt giá
     * @return giao dịch bid vừa được ghi nhận
     */
    public BidTransaction placeBid(int auctionId, int bidderId, double bidAmount) {
        Auction auction = auctions.get(auctionId);
        if (auction == null)
            throw new RuntimeException("Phiên không tồn tại: " + auctionId);

        // Chỉ phiên đang mở mới được nhận bid mới.
        if (auction.getStatus() != AuctionStatus.RUNNING)
            throw new RuntimeException("Phiên " + auctionId + " không đang đấu giá!");

        // Nếu thời gian hiện tại đã vượt quá thời điểm kết thúc, phiên sẽ tự đóng.
        if (LocalDateTime.now().isAfter(auction.getEndTime())) {
            auction.transitionTo(AuctionStatus.FINISHED);
            throw new RuntimeException("Phiên " + auctionId + " đã hết thời gian!");
        }

        // Người chơi phải trả ít nhất giá hiện tại cộng với bước nhảy tối thiểu.
        double minRequired = auction.getCurrentPrice() + auction.getMinimumIncrement();
        if (bidAmount < minRequired) {
            throw new RuntimeException(String.format(
                    "Giá %,.0f không hợp lệ! Tối thiểu: %,.0f (hiện tại %,.0f + bước nhảy %,.0f)",
                    bidAmount, minRequired, auction.getCurrentPrice(), auction.getMinimumIncrement()));
        }

        User bidder = users.get(bidderId);
        // Chỉ tài khoản bidder hợp lệ mới được phép tham gia đặt giá.
        if (bidder == null || bidder.getRole() != Role.BIDDER)
            throw new RuntimeException("Bidder không hợp lệ!");

        int bidId = bidIdCounter.incrementAndGet();
        BidTransaction bid = new BidTransaction(auctionId, bidderId, bidAmount);
        bid.setId(bidId);

        // Sau khi bid hợp lệ, cập nhật ngay trạng thái hiện tại của phiên.
        auction.setCurrentPrice(bidAmount);
        auction.setHighestBidderId(bidderId);
        auction.incrementTotalBids();

        // Ghi lại giao dịch vào lịch sử để có thể tra cứu sau.
        bidHistory.computeIfAbsent(auctionId, k -> new ArrayList<>()).add(bid);
        return bid;
    }

    /**
     * Lấy lịch sử đặt giá của một phiên đấu giá.
     *
     * @param auctionId ID phiên đấu giá
     * @return danh sách lịch sử bid; nếu chưa có thì trả về danh sách rỗng
     */
    public List<BidTransaction> getBidHistory(int auctionId) {
        return bidHistory.getOrDefault(auctionId, new ArrayList<>());
    }

    // ======================== SEED DATA ========================

    /**
     * Tạo dữ liệu mẫu để tiện kiểm thử và demo chương trình ngay sau khi khởi động.
     *
     * <p>Phương thức này sinh sẵn một số tài khoản, sản phẩm và một phiên đấu giá mẫu
     * để người dùng có thể đăng nhập và thao tác ngay mà không cần nhập toàn bộ dữ liệu từ đầu.</p>
     */
    public void seedData() {
        // Tạo sẵn các tài khoản mẫu cho nhiều vai trò khác nhau.
        register("alice", "123", Role.BIDDER, 10_000_000);
        register("bob", "123", Role.BIDDER, 15_000_000);
        register("seller1", "123", Role.SELLER, 0);
        register("admin", "123", Role.ADMIN, 0);

        // Tạo trước một vài sản phẩm thuộc nhiều danh mục để dễ test.
        createItem(ItemCategory.ELECTRONICS, "MacBook Pro M3", "Mới nguyên seal, BH 12 tháng",
                35_000_000, 3, ItemCondition.NEW,
                Map.of("brand", "Apple", "model", "MacBook Pro", "warrantyMonths", "12"));

        createItem(ItemCategory.ART, "Tranh sơn dầu Phong cảnh", "Tranh gốc, có chứng nhận",
                5_000_000, 3, ItemCondition.NEW,
                Map.of("artist", "Nguyễn Văn A", "year", "2024"));

        createItem(ItemCategory.VEHICLE, "Honda Wave Alpha 2022", "Đã đi 5000km",
                15_000_000, 3, ItemCondition.USED,
                Map.of("brand", "Honda", "model", "Wave Alpha", "manufactureYear", "2022",
                        "mileage", "5000", "color", "Đỏ", "fuelType", "Xăng",
                        "transmission", "Số tay", "ownerCount", "1", "hasRegistration", "true"));

        // Mở sẵn một phiên đấu giá để người dùng có thể vào xem hoặc bid ngay.
        createAuction(1, 3, 60, 1_000_000);

        System.out.println(" Seed data đã tạo:");
        System.out.println("   Tài khoản: alice/123 (Bidder), bob/123 (Bidder), seller1/123 (Seller)");
        System.out.println("   Sản phẩm: 3 items | Phiên đấu giá: 1 phiên MacBook (60 phút)");
    }
}
