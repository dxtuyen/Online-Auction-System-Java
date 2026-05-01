# Hệ thống Đấu giá Trực tuyến

![Java CI](https://github.com/YOUR_USERNAME/Online-Auction-System-Java/actions/workflows/ci.yml/badge.svg)

Hệ thống đấu giá Client-Server bằng Java 17, JavaFX, Socket TCP và JSON.

## Tính năng

### Bắt buộc
- Đăng ký / đăng nhập 3 vai trò (Bidder / Seller / Admin)
- Seller quản lý sản phẩm (Electronics / Art / Vehicle / Other)
- Tạo phiên đấu giá với thời gian + bước nhảy
- Đặt giá realtime, validate giá + trạng thái phiên
- Phiên tự đóng khi hết giờ + settlement
- GUI JavaFX: Login / Register / List / Bidding / Seller Dashboard
- Realtime push cho mọi client đang xem phiên

### Nâng cao
- Anti-Sniping — bid trong 30s cuối → gia hạn 60s
- Auto-Bidding — maxBid + increment + xử lý đệ quy
- Reserved Balance — bidder không thể "ôm" giá vượt số dư khả dụng

### Kỹ thuật
- Concurrency-safe: ReentrantLock per-auction & per-user, ConcurrentHashMap
- Observer pattern cho realtime push qua socket
- Factory Method cho Item
- Singleton cho AuctionService, ClientModel, AuctionEventManager
- JUnit 5: AuctionServiceTest (19 tests) + JsonHelperTest (8 tests)
- GitHub Actions CI/CD

## Cài đặt & Chạy

### Yêu cầu
- JDK 17+
- Maven 3.8+

### Build
```bash
mvn clean compile
```

### Chạy Server (terminal 1)
```bash
mvn exec:java -Dexec.mainClass="com.auction.server.ServerMain"
```

### Chạy Client JavaFX (terminal 2, 3, ...)
```bash
mvn javafx:run
```

### Chạy Console demo
```bash
mvn exec:java -Dexec.mainClass="com.auction.Main"
```

### Tài khoản test (seed data)
| Username | Password | Role |
|----------|----------|------|
| alice | 123 | BIDDER |
| bob | 123 | BIDDER |
| seller1 | 123 | SELLER |
| admin | 123 | ADMIN |

## Test
```bash
mvn test
```

##  Design Patterns

| Pattern | Ở đâu | Mục đích |
|---------|-------|----------|
| Singleton | AuctionService, ClientModel, AuctionEventManager | 1 instance toàn hệ thống |
| Factory Method | ItemFactory | Tạo Item theo category |
| Observer | AuctionEventManager → ClientHandler | Realtime push bid mới |

## CI/CD

Workflow `.github/workflows/ci.yml` chạy tự động khi push hoặc tạo PR:
1. Setup JDK 17 (Temurin)
2. `mvn clean compile`
3. `mvn test`
4. Upload test report
