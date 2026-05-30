# Online Auction System (Java)

Hệ thống đấu giá trực tuyến client–server: server TCP đa luồng xử lý nghiệp vụ đấu giá,
client JavaFX cho người dùng đặt giá / theo dõi phiên realtime.

## Tech stack

- **Java 17**, build bằng **Maven**
- **JavaFX 21** (client UI, FXML + CSS)
- **MySQL 8** qua **JDBC + HikariCP** (connection pool); **H2** cho test
- **Gson** để serialize Request/Response JSON qua socket
- **JUnit 5 + Mockito** cho test, **JaCoCo** cho coverage
- Giao tiếp: TCP socket, mỗi client một thread (`ClientHandler`)

## Cấu trúc package (`src/main/java/com/auction`)

```
com.auction
├── bootstrap/        # DataSeeder — nạp dữ liệu mẫu khi khởi động
├── config/           # AppConfig — đọc cấu hình từ .env / biến môi trường
│
├── protocol/         # Giao thức network dùng chung client ↔ server
│   ├── ActionType.java       # enum action (LOGIN, PLACE_BID, ...) thay cho String
│   ├── Request.java / Response.java
│   └── ResponseStatus.java
│
├── model/            # Tầng domain (entity + nghiệp vụ thuần)
│   ├── entity/       # User, Auction, Item (+ Art/Electronics/Vehicle/OtherItem),
│   │                 #   BidTransaction, AutoBid, Entity (base)
│   ├── enums/        # AuctionStatus, BidStatus, ItemCategory, ItemCondition, Role, UserStatus
│   ├── exception/    # AuctionException, InvalidBidException, AuthException, ...
│   ├── factory/      # ItemFactory — tạo Item theo ItemCategory (Factory pattern)
│   └── observer/     # AuctionObserver — interface duy nhất cho event đấu giá
│
├── service/          # Business logic (singleton, in-memory + đồng bộ DB)
│   ├── AuctionManager.java
│   ├── BidManager.java
│   ├── ItemManager.java
│   └── UserManager.java
│
├── persistence/      # Tầng truy xuất dữ liệu
│   ├── Database.java         # HikariCP datasource
│   └── dao/          # Interface (UserDao, AuctionDao, ItemDao, BidTransactionDao,
│                     #   AutoBidDao) + impl Mysql*Dao + PersistenceException
│
├── security/         # PasswordEncoder (hash mật khẩu)
│
├── server/           # Phía server
│   ├── ServerMain.java       # Entry point server
│   ├── ClientHandler.java    # Mỗi connection một thread
│   ├── RequestRouter.java    # Dispatch action → controller (Command + EnumMap)
│   ├── Session.java          # State per-connection (user đang đăng nhập)
│   ├── command/      # AuthLevel, CommandHandler (functional interface)
│   └── observer/     # AuctionEventManager — cầu nối Observer ↔ push realtime
│
├── controller/       # Request handler phía server (gọi service)
│   ├── UserController.java   # LOGIN, REGISTER, LOGOUT, GET_PROFILE
│   ├── AuctionController.java# Item + Auction
│   └── BidController.java    # PLACE_BID, SET_AUTO_BID, BID_HISTORY
│
├── client/           # JavaFX app
│   ├── ClientApp.java        # Entry point client
│   ├── controller/   # Login/Register/AuctionList/Bidding/SellerDashboard controllers
│   ├── model/        # ClientModel — state phía client
│   └── network/      # ServerConnection — gửi Request, nhận Response/push
│
└── util/             # AppLogger, JsonHelper, MoneyHelper
```

Resources: `src/main/resources/{fxml,css,sql}` — giao diện FXML, stylesheet, và `schema.sql`.

> **Lưu ý 2 package tên `controller`:** `com.auction.controller` là handler **phía server**
> (nhận Request → gọi service); `com.auction.client.controller` là controller **JavaFX**
> của UI client. Vai trò khác nhau hoàn toàn.

## Design patterns

- **Factory** — `ItemFactory` tạo subtype Item theo `ItemCategory`.
- **Observer** — `AuctionObserver` (domain) → `AuctionEventManager` (server) đẩy event
  bid/extend/status về client đang `WATCH_AUCTION`.
- **Command + dispatch table** — `RequestRouter` map `ActionType → CommandHandler`
  (EnumMap, O(1)); thêm action = thêm 1 dòng `register(...)` (Open/Closed).
- **Singleton (Bill Pugh)** — service manager và controller stateless dùng chung 1 instance.

## Chạy dự án

### 1. Cấu hình & khởi động database

```bash
cp .env.example .env          # chỉnh password nếu cần
docker compose up -d          # MySQL 8, tự nạp schema.sql
```

Biến môi trường chính (xem [.env.example](.env.example)): `DB_URL`, `DB_USER`,
`DB_PASSWORD`, `SERVER_PORT` (mặc định 8888), và tham số anti-sniping
(`SNIPING_THRESHOLD_SECONDS`, `SNIPING_EXTENSION_SECONDS`).

### 2. Chạy server

```bash
mvn compile exec:java         # mainClass: com.auction.server.ServerMain
```

### 3. Chạy client (JavaFX)

```bash
mvn javafx:run                # mainClass: com.auction.client.ClientApp
```

### Test

```bash
mvn test                      # JUnit 5 + Mockito, dùng H2 in-memory
mvn verify                    # kèm báo cáo coverage JaCoCo (target/site/jacoco)
```

## Tài liệu

- [docs/](docs/) — sơ đồ UML (`auction.puml`, ảnh PNG) và `lock-ordering.md` (thứ tự
  khóa tránh deadlock khi nhiều bid đồng thời).
