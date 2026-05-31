# Package Map — Auction System

Tài liệu này mô tả vai trò của từng package trong `src/main/java/com/auction/**`. Mục đích: đọc xong biết file mới nên đặt ở đâu, và nhìn 1 package là hiểu nó "phụ trách cái gì, được phép phụ thuộc cái gì".

> Snapshot dựa trên commit `774ab70` (2026-05-21). Khi đổi cấu trúc package, cập nhật file này + [[lock-ordering]] nếu chạm tới lock.

---

## 1. Sơ đồ phụ thuộc (cho phép)

```
       ┌────────────┐
       │ bootstrap  │  seed dữ liệu mẫu lúc start
       └─────┬──────┘
             ▼
   ┌──────────────────┐        ┌──────────────────┐
   │      server      │◄──────►│      client      │
   │ (ClientHandler,  │protocol│ (JavaFX UI +     │
   │  RequestRouter)  │ (JSON) │  ServerConnection)│
   └────┬─────────────┘        └────┬─────────────┘
        │                            │
        ▼                            ▼
   ┌──────────┐                ┌──────────────┐
   │ service  │                │ client.model │
   │ (Managers│                │ (singleton   │
   │  + obs)  │                │  cache + obs)│
   └────┬─────┘                └──────────────┘
        ▼
   ┌──────────┐   ┌────────────┐   ┌──────────┐
   │  model   │◄──│ persistence│   │ protocol │
   │  (entity,│   │   (DAO +   │   │ (Request,│
   │  enums,  │   │  Database) │   │ Response,│
   │  excep…) │   └────────────┘   │ Action)  │
   └──────────┘                    └──────────┘
        ▲
        │
   ┌──────────┐   ┌──────────┐   ┌──────────┐
   │ security │   │  config  │   │   util   │
   └──────────┘   └──────────┘   └──────────┘
```

Luật phụ thuộc:

- `model.*` KHÔNG được import `service`, `persistence`, `server`, `client`. Entity phải thuần.
- `persistence` chỉ phụ thuộc `model` + JDBC. KHÔNG gọi ngược `service`.
- `service` là tầng duy nhất được mua lock và phối hợp `persistence` + `model`.
- `server` và `client` giao tiếp **chỉ** qua `protocol` (JSON Request/Response). Không share object Java.
- `util`, `security`, `config`, `protocol` là leaf — không phụ thuộc package khác trong project.

---

## 2. Chi tiết từng package

### `com.auction.bootstrap`
- **File**: `DataSeeder.java`
- **Vai trò**: Insert dữ liệu mẫu (admin, item, auction) lúc server start nếu DB trống. Idempotent.
- **Phụ thuộc**: `service`, `model`. Chạy 1 lần từ `ServerMain`.

### `com.auction.config`
- **File**: `AppConfig.java`
- **Vai trò**: Đọc cấu hình (port, DB URL, anti-snipe seconds…) từ env/properties. Singleton.
- **Quy tắc**: KHÔNG bao giờ hardcode magic number ngoài đây.

### `com.auction.protocol`
- **File**: `ActionType`, `Request`, `Response`, `ResponseStatus`
- **Vai trò**: Schema wire-format giữa client ↔ server. Mọi message JSON đều parse về 1 trong 4 class này.
- **Quy tắc**: Thêm action mới = thêm 1 entry `ActionType` + 1 `CommandHandler` ở server. KHÔNG nhồi field tự do vào `Request`.

### `com.auction.security`
- **File**: `PasswordEncoder.java`
- **Vai trò**: Hash + verify password (salt + SHA-256/PBKDF2). Stateless static helper.
- **Quy tắc**: Không bao giờ log raw password. Không tự cuộn crypto khác — mở rộng tại đây.

### `com.auction.util`
- **File**: `AppLogger`, `IdGenerator`, `JsonHelper`, `MoneyHelper`
- **Vai trò**: Helper thuần. Không state, không lock, không I/O bền vững.
- **Quy tắc**: Code đưa vào đây phải **dùng được ở cả server lẫn client**.

### `com.auction.model.entity`
- **File**: `Entity` (base), `User`, `Item` (+ `Art`, `Electronics`, `Vehicle`, `OtherItem`), `Auction`, `AutoBid`, `BidTransaction`
- **Vai trò**: Domain object. Giữ invariant nội tại (vd `User.tryReserve` atomic, `Auction.placeBid` mua L1).
- **Quy tắc**: Lock ordering chính tắc nằm tại đây — xem [[lock-ordering]] §2.

### `com.auction.model.enums`
- **File**: `AuctionStatus`, `BidStatus`, `ItemCategory`, `ItemCondition`, `Role`, `UserStatus`
- **Vai trò**: Enum domain, dùng chung server + client + DB column.
- **Quy tắc**: Thêm value mới → check tất cả `switch` exhaustive + migration DB.

### `com.auction.model.exception`
- **File**: `AuctionException` (base) + `AuctionClosedException`, `IllegalAuctionStateException`, `InvalidBidException`, `InsufficientBalanceException`, `AuthException`
- **Vai trò**: Domain exception, được service ném ra, server map sang `ResponseStatus`.
- **Quy tắc**: Đừng throw `RuntimeException` chung chung trong domain — tạo subclass.

### `com.auction.model.factory`
- **File**: `ItemFactory.java`
- **Vai trò**: Factory Method tạo `Item` con đúng theo `ItemCategory`. Tách `new` khỏi service.
- **Quy tắc**: Thêm subclass `Item` → mở rộng tại đây, không sửa caller.

### `com.auction.model.observer`
- **File**: `AuctionObserver.java`
- **Vai trò**: Interface Observer cho auction event (bid placed, extended, transition). Server đăng ký để fan-out cho client.
- **Quy tắc**: Observer chạy **ngoài lock** L1 — xem [[lock-ordering]] R2.

### `com.auction.persistence`
- **File**: `Database.java` (connection pool / single conn) + sub-package `dao`
- **Vai trò**: Đóng gói JDBC. Service không bao giờ tự `Connection`.

### `com.auction.persistence.dao`
- **File**: Interface `*Dao` + impl `Mysql*Dao` cho `User`, `Item`, `Auction`, `BidTransaction`, `AutoBid`. `PersistenceException` cho lỗi DB.
- **Quy tắc**: DAO chỉ làm CRUD, KHÔNG business logic (vd: không check "balance đủ" — đó là việc của `User.tryReserve`).

### `com.auction.service`
- **File**: `UserManager`, `ItemManager`, `AuctionManager`, `BidManager`
- **Vai trò**: Singleton service. Phối hợp DAO + entity, quản lý lifecycle auction (scheduler tick), trigger observer chain.
- **Quy tắc**: Đây là tầng **được phép mua lock**. Tuân thủ [[lock-ordering]] §5 khi thêm method mới.

### `com.auction.controller`
- **File**: `AuctionController`, `BidController`, `UserController`
- **Vai trò**: Adapter cho server-side request — nhận `Request`, gọi `service`, trả `Response`. Mỏng, không state.
- **Quy tắc**: KHÔNG đặt lock ở đây. Mọi mutate state phải qua `service`.

### `com.auction.server`
- **File**: `ServerMain` (entry), `ClientHandler` (1 thread/socket), `RequestRouter` (map action → handler), `Session` (per-connection auth state)
- **Vai trò**: TCP server. Mỗi connection 1 `ClientHandler`, đọc JSON → `RequestRouter` → `CommandHandler` → service → `Response`.

### `com.auction.server.command`
- **File**: `CommandHandler` (interface), `AuthLevel` (enum: PUBLIC/USER/ADMIN)
- **Vai trò**: Command pattern cho từng `ActionType`. Khai báo quyền tối thiểu để gate ở `RequestRouter`.

### `com.auction.server.observer`
- **File**: `AuctionEventManager.java`
- **Vai trò**: Fan-out event auction → các `ClientHandler` đang subscribe. Đẩy JSON qua `ClientHandler.send` (L3 — xem [[lock-ordering]]).

### `com.auction.client`
- **File**: `Launcher` (JavaFX bootstrap), `ClientApp` (Application chính)
- **Vai trò**: Entry point JavaFX client.

### `com.auction.client.controller`
- **File**: `LoginController`, `RegisterController`, `AuctionListController`, `BiddingController`, `SellerDashboardController`
- **Vai trò**: JavaFX FXML controller. Bind UI ↔ `ClientModel`.
- **Quy tắc**: KHÔNG gọi `ServerConnection` trực tiếp từ FX thread cho blocking call — đẩy qua `ClientModel`.

### `com.auction.client.model`
- **File**: `ClientModel.java` (singleton — L5)
- **Vai trò**: Cache state phía client (user hiện tại, danh sách auction), quản lý map `action → responseQueue`, đăng ký push handler.
- **Điểm yếu đã biết**: W2 trong [[lock-ordering]] (key theo `action` thay vì `requestId`).

### `com.auction.client.network`
- **File**: `ServerConnection.java`
- **Vai trò**: TCP client. 1 socket, 1 thread reader, `send()` synchronized (L4).

---

## 3. Quy ước đặt file mới

| Loại file mới | Đặt ở | Lý do |
|---------------|-------|-------|
| Entity domain | `model.entity` | Giữ purity tầng model |
| Enum domain | `model.enums` | Tránh hardcode string |
| Exception nghiệp vụ | `model.exception` | Để server map sang `ResponseStatus` chuẩn |
| Câu SQL mới / bảng mới | `persistence.dao` | Tách JDBC khỏi service |
| Logic phối hợp / mua lock | `service` | Tầng duy nhất được mua lock |
| Action mới giữa client↔server | `protocol.ActionType` + `server.command` (handler) | Wire-format tập trung |
| Helper dùng cả 2 phía | `util` | Đảm bảo không kéo dep server vào client |
| Cấu hình runtime | `config.AppConfig` | Tránh magic number rải rác |

---

## 4. Anti-patterns đã thấy / cần tránh

- **Import `service` từ `model`**: vỡ tầng. Entity gọi service = ai cũng có thể gọi ai = deadlock + test khó.
- **Cầm lock L1 rồi gọi `dao.save()`**: I/O dưới lock. Snapshot ra biến local, unlock, rồi save.
- **JavaFX controller `Thread.sleep` / blocking socket read**: đứng UI thread. Dùng `ClientModel` queue + callback.
- **Thêm `synchronized` ngoài cùng `Manager`**: vô hiệu hoá `ConcurrentHashMap` — xem [[lock-ordering]] §5.5.

---

## 5. Changelog

| Ngày | Thay đổi | Ghi chú |
|------|---------|---------|
| 2026-05-21 | Khởi tạo package map dựa trên commit `774ab70`. | Cập nhật khi đổi cấu trúc package. |
