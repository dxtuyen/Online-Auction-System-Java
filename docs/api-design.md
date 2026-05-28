# API Design — Auction System

Tài liệu mô tả giao thức (protocol) giữa **Client JavaFX** và **Server socket**. Đây không phải REST API — hệ thống dùng TCP socket + JSON dạng newline-delimited (NDJSON). Mọi "endpoint" được nhận diện qua trường `action`.

> Nguồn gốc thật trong code: `com.auction.protocol.*`, `com.auction.server.RequestRouter`, `com.auction.controller.*`. Khi đổi schema, cập nhật file này.

---

## 1. Transport layer

| Mục | Giá trị |
|---|---|
| Protocol | TCP socket |
| Default port | `8888` (`SERVER_PORT` trong `.env`) |
| Encoding | UTF-8 |
| Frame | **1 dòng = 1 message JSON**, kết thúc bằng `\n` |
| Connection model | Long-lived, multiplex nhiều request/response trên cùng 1 socket |
| Concurrency | Mỗi connection chạy trên 1 thread của pool (`SERVER_MAX_THREADS`) |

Lỗi parse JSON **không đóng connection** — server trả `ERROR` response, các request sau vẫn tiếp tục.

---

## 2. Message envelope

### 2.1 Request (Client → Server)

```json
{
  "action": "PLACE_BID",
  "data":   { "auctionId": "uuid-string", "amount": 1500000 },
  "token":  null,
  "requestId": "client-generated-uuid"
}
```

| Field | Bắt buộc | Mô tả |
|---|---|---|
| `action` | yes | Tên enum `ActionType` (xem mục 4). Sai tên → `ERROR`. |
| `data` | tuỳ action | Map key/value, schema khác nhau cho từng action. |
| `token` | no | Hiện chưa dùng — auth dựa trên **session per-connection**. Giữ field cho khả năng mở rộng (JWT). |
| `requestId` | khuyến nghị | UUID client tự sinh để ghép với response (nhiều request song song trên 1 connection). |

### 2.2 Response (Server → Client)

```json
{
  "action": "PLACE_BID",
  "status": "SUCCESS",
  "message": "Đặt giá thành công",
  "data": { "bidId": "...", "amount": 1500000 },
  "requestId": "client-generated-uuid"
}
```

| Field | Mô tả |
|---|---|
| `action` | Echo lại action của request, hoặc tên push event. |
| `status` | `SUCCESS` / `ERROR` / `PUSH`. |
| `message` | Thông báo cho user (tiếng Việt, hiện trực tiếp lên UI). |
| `data` | Payload — `null` cho action không cần trả gì. |
| `requestId` | Echo lại từ request (chỉ với `SUCCESS`/`ERROR`). `PUSH` không có. |

---

## 3. Authentication & authorization

### 3.1 Session model

- Mỗi `ClientHandler` (1 connection) giữ một `Session` lưu `currentUserId`.
- `LOGIN` thành công → server set `userId` vào session. **Không có token**.
- `LOGOUT` xoá `userId` khỏi session.
- Disconnect → session bị huỷ cùng connection; client phải `LOGIN` lại khi reconnect.

### 3.2 AuthLevel

Mỗi action được đăng ký với 1 `AuthLevel` tại `RequestRouter`:

| Level | Yêu cầu | Ví dụ action |
|---|---|---|
| `PUBLIC` | Không cần đăng nhập | `LOGIN`, `REGISTER`, `LIST_AUCTIONS`, `GET_AUCTION`, `BID_HISTORY`, `WATCH_AUCTION`, `UNWATCH_AUCTION`, `GET_IMAGE` |
| `USER` | Đã đăng nhập | `PLACE_BID`, `CREATE_AUCTION`, `GET_PROFILE`, `UPLOAD_AVATAR`, ... |
| `ADMIN` | Đăng nhập + role `ADMIN` | `LIST_USERS`, `BAN_USER`, `UNBAN_USER`, `ADMIN_CLOSE_AUCTION` |

Middleware ở `RequestRouter` reject trước khi vào controller — controller không cần lặp check.

### 3.3 Lỗi auth thường gặp

| `message` | Khi nào |
|---|---|
| `"Chưa đăng nhập"` | `AuthLevel ≥ USER` và session chưa login. |
| `"Yêu cầu quyền quản trị viên"` | `AuthLevel = ADMIN` nhưng user không phải role `ADMIN`. |
| `"Action không hỗ trợ: X"` | `action` không match enum `ActionType`. |
| `"Action chưa được đăng ký: X"` | Có trong enum nhưng quên `register()` ở router. |

---

## 4. Action catalog

### 4.1 Quy ước chung

- **UUID** truyền dưới dạng String (`"e2eee48b-..."`). Server convert bằng `getDataUUID`.
- **Tiền (money)** truyền dạng `BigDecimal` qua JSON number (Gson được config `BIG_DECIMAL` policy). Tuyệt đối không dùng `double` cho tiền.
- **Datetime** truyền dạng ISO-8601 string (`LocalDateTime.toString()`).
- **Ảnh** truyền dưới dạng Base64 string, kèm `fileName` để server extract extension. Whitelist: `jpg`, `jpeg`, `png`, `gif`, `webp`. Giới hạn: 3 MB.

### 4.2 USER actions

#### `LOGIN` — `PUBLIC`

**Request data**
```json
{ "username": "alice", "password": "secret123" }
```

**Response data (SUCCESS)**
```json
{
  "userId": "uuid", "username": "alice", "email": "...",
  "fullName": "...", "status": "ACTIVE", "displayStatus": "Hoạt động",
  "role": "NORMAL", "displayRole": "Người dùng", "avatarUrl": "avatars/xxx.png"
}
```

**Side effects**: server set `userId` vào session — các request sau coi như đã auth.

---

#### `REGISTER` — `PUBLIC`

**Request data**
```json
{
  "username": "alice", "password": "secret123",
  "email": "alice@example.com", "fullName": "Alice Nguyen",
  "initialBalance": 1000000
}
```

`initialBalance` optional, mặc định 0.

**Response data (SUCCESS)**: `{ userId, username, email, fullName, role, displayRole, balance }`.

---

#### `LOGOUT` — `USER`

**Request data**: không cần.
**Response**: `{ "message": "Đã đăng xuất" }`, `data: null`.

---

#### `GET_PROFILE` — `USER`

**Response data**: `{ userId, username, email, fullName, role, displayRole, status, displayStatus, balance, revenue, avatarUrl }`.

---

#### `UPDATE_PROFILE` — `USER`

**Request data**: `{ fullName?, email? }` — field nào không gửi (null/blank) thì giữ nguyên.
**Response data**: `{ fullName, email }`.
**Lỗi**: email trùng user khác → `"Email đã tồn tại"`.

---

#### `CHANGE_PASSWORD` — `USER`

**Request data**: `{ oldPassword, newPassword }`.
**Response**: `data: null`.
**Lỗi**: `"Mật khẩu cũ không đúng"`, `"Mật khẩu mới phải >= 6 ký tự"`.

---

#### `UPLOAD_AVATAR` — `USER`

**Request data**: `{ fileName: "me.png", dataBase64: "iVBORw0KGgo..." }`.
**Response data**: `{ avatarUrl: "avatars/<uuid>.png" }`.

---

#### `GET_IMAGE` — `PUBLIC`

**Request data**: `{ url: "avatars/xxx.png" }` (relative URL).
**Response data**: `{ url, dataBase64 }`.
**Lỗi**: `"Không tìm thấy ảnh"` (file không tồn tại, hoặc path traversal bị chặn).

---

### 4.3 ITEM actions

#### `CREATE_ITEM` — `USER`

**Request data**
```json
{
  "category": "ELECTRONICS",
  "name": "iPhone 15 Pro",
  "description": "...",
  "startingPrice": 25000000,
  "condition": "USED_LIKE_NEW",
  "images": ["items/old.png"],
  "specificAttributes": { "brand": "Apple", "ram": "8GB" }
}
```

- `category`: enum `ItemCategory` — `ELECTRONICS`, `VEHICLE`, `ART`, `OTHER`.
- `condition`: enum `ItemCondition`.
- `specificAttributes`: map key/value tuỳ category (xem `ItemFactory`).
- `sellerId` lấy từ session — **không tin client**.

**Response data**: `{ itemId, itemName }`.

---

#### `LIST_MY_ITEMS` — `USER`

**Response data**
```json
{ "items": [
  { "itemId": "...", "name": "...", "startingPrice": ...,
    "category": "Điện tử", "condition": "Đã qua sử dụng", "imageUrl": "items/..." }
]}
```

---

#### `UPLOAD_ITEM_IMAGE` — `USER`

**Request data**: `{ itemId, fileName, dataBase64 }`.
Server xác minh `item.sellerId == session.userId`. Thay thế **1 ảnh chính duy nhất** ở position 0.
**Response data**: `{ itemId, imageUrl }`.
**Lỗi**: `"Bạn không phải chủ sản phẩm này"`.

---

### 4.4 AUCTION actions

#### `LIST_AUCTIONS` — `PUBLIC`

**Response data**
```json
{ "auctions": [
  { "auctionId", "sellerId", "itemName", "itemCategory", "category",
    "imageUrl", "currentPrice", "startingPrice", "totalBids",
    "status", "displayStatus", "endTime" }
]}
```

---

#### `GET_AUCTION` — `PUBLIC`

**Request data**: `{ auctionId }`.
**Response data**: `{ auctionId, itemName, itemDescription, itemCategory, imageUrl, sellerName, sellerAvatarUrl, startingPrice, currentPrice, minimumIncrement, totalBids, highestBidderId, leaderName, status, displayStatus, startTime, endTime }`.

---

#### `CREATE_AUCTION` — `USER`

**Request data**: `{ itemId, durationMinutes?, minimumIncrement? }`.

- `durationMinutes` mặc định 30. `endTime = now + durationMinutes`.
- `minimumIncrement` mặc định 100 000.
- `startingPrice` không cho client gửi — lấy từ `item.startingPrice`.

**Response data**: `{ auctionId, endTime }`.

---

#### `CLOSE_AUCTION` — `USER`

**Request data**: `{ auctionId }`. Chỉ seller của phiên mới được đóng (kiểm tra ở `AuctionManager`).
**Response**: `data: null`.

---

#### `WATCH_AUCTION` / `UNWATCH_AUCTION` — `PUBLIC`

**Request data**: `{ auctionId }`.
Đăng ký/huỷ đăng ký nhận push (`BID_UPDATE`, `AUCTION_STATUS`, `AUCTION_EXTENDED`) cho phiên đó. Không cần login để xem realtime.
Disconnect → tự `unsubscribeAll`.

---

#### `CONFIRM_PAYMENT` — `USER`

**Request data**: `{ auctionId }`. Chỉ winner mới gọi được.
**Response data**: `{ auctionId, status: "PAID", paidAmount }`.

---

#### `FORFEIT_AUCTION` — `USER`

Winner từ chối thanh toán, chịu phí phạt.
**Request data**: `{ auctionId }`.
**Response data**: `{ auctionId, status: "CANCELED" }`.

---

### 4.5 BID actions

#### `PLACE_BID` — `USER`

**Request data**: `{ auctionId, amount }`.
`bidderId` lấy từ session.

**Response data (SUCCESS)**: `{ bidId, amount }`.

**Lỗi nghiệp vụ thường gặp**:
| Exception | `message` |
|---|---|
| `InvalidBidException` | `"Giá đặt phải >= currentPrice + minimumIncrement"` |
| `AuctionClosedException` | `"Phiên đã đóng"` |
| `IllegalAuctionStateException` | `"Phiên chưa bắt đầu"` |
| `InsufficientBalanceException` | `"Số dư không đủ"` |
| `SecurityException` | `"Seller không được tự bid item của mình"` |

**Side effects**: trigger anti-sniping (nếu bid trong `SNIPING_THRESHOLD_SECONDS` cuối → gia hạn `SNIPING_EXTENSION_SECONDS`), push `BID_UPDATE` (và có thể `AUCTION_EXTENDED`) tới mọi subscriber.

---

#### `SET_AUTO_BID` — `USER`

**Request data**: `{ auctionId, maxBid, increment }`.
**Response**: `data: null`, `message: "Auto-bid đã đăng ký: max ..., bước ..."`.

---

#### `BID_HISTORY` — `PUBLIC`

**Request data**: `{ auctionId }`.
**Response data**
```json
{ "auctionId": "...", "bids": [
  { "bidId", "bidderId", "bidderName", "amount", "timestamp" }
]}
```

---

### 4.6 ADMIN actions

#### `LIST_USERS` — `ADMIN`

**Response data**: `{ users: [ { userId, username, email, fullName, role, displayRole, status, displayStatus, balance, revenue } ] }`. **Không trả** `passwordHash`/`salt`.

---

#### `BAN_USER` / `UNBAN_USER` — `ADMIN`

**Request data**: `{ userId }`.
`BAN_USER` không cho self-ban → `"Không thể khóa chính mình"`.
**Response data**: `{ userId, status, displayStatus }`.

---

#### `ADMIN_CLOSE_AUCTION` — `ADMIN`

**Request data**: `{ auctionId }`.
**Response data**: `{ auctionId, status }`.

---

## 5. Server push messages

Phát ra với `status: "PUSH"`, không có `requestId`. Client nhận sau khi `WATCH_AUCTION`.

### 5.1 `BID_UPDATE`

Có bid mới trên phiên đang theo dõi.

```json
{ "action": "BID_UPDATE", "status": "PUSH",
  "data": { "auctionId", "bidderName", "amount", "totalBids" } }
```

### 5.2 `AUCTION_STATUS`

Phiên đổi trạng thái (`RUNNING` → `FINISHED`, `FINISHED` → `PAID/CANCELED`, ...).

```json
{ "action": "AUCTION_STATUS", "status": "PUSH",
  "data": { "auctionId", "status", "displayStatus", "highestBidderId" } }
```

### 5.3 `AUCTION_EXTENDED`

Anti-sniping gia hạn phiên.

```json
{ "action": "AUCTION_EXTENDED", "status": "PUSH",
  "data": { "auctionId", "newEndTime", "extendedSeconds" } }
```

---

## 6. Error handling

| Tình huống | `status` | Cấu trúc |
|---|---|---|
| Sai `action` enum | `ERROR` | `message = "Action không hỗ trợ: X"` |
| Thiếu auth | `ERROR` | `message = "Chưa đăng nhập"` / `"Yêu cầu quyền quản trị viên"` |
| Validation thất bại | `ERROR` | `message` cụ thể từ controller |
| Business exception (`AuctionClosedException`, `InvalidBidException`, ...) | `ERROR` | Router normalize `e.getMessage()` thành `Response.error` |
| JSON parse fail | `ERROR` | `action = "UNKNOWN"`, `message = "Lỗi parse: ..."` |

Quy ước: `message` là **tiếng Việt, có thể hiện trực tiếp lên dialog**. Client không cần map code → text.

---

## 7. Sequence diagrams

### 7.1 Login + xem auction realtime

```
Client                                   Server
  │  LOGIN { username, password }          │
  │ ─────────────────────────────────────▶ │
  │ ◀────────────── SUCCESS { userId, ...} │  (session.userId set)
  │  LIST_AUCTIONS                         │
  │ ─────────────────────────────────────▶ │
  │ ◀──────────── SUCCESS { auctions: [] } │
  │  WATCH_AUCTION { auctionId }           │
  │ ─────────────────────────────────────▶ │
  │ ◀────────────────────── SUCCESS (null) │
  │                                        │
  │ ◀────────── PUSH BID_UPDATE { ... }    │  (user khác đặt giá)
  │ ◀────── PUSH AUCTION_EXTENDED { ... }  │  (anti-sniping)
  │ ◀────── PUSH AUCTION_STATUS FINISHED   │
```

### 7.2 Place bid

```
Client                                   Server
  │  PLACE_BID { auctionId, amount }       │
  │ ─────────────────────────────────────▶ │
  │                                        │ BidManager.placeBid()
  │                                        │   → validate
  │                                        │   → save BidTransaction
  │                                        │   → auction.placeBid() (fires onBidPlaced)
  │                                        │   → check anti-sniping (fires onAuctionExtended)
  │ ◀────────── PUSH BID_UPDATE { ... }    │  (cho cả bidder hiện tại nếu đang WATCH)
  │ ◀────────── SUCCESS { bidId, amount }  │
```

---

## 8. Thêm action mới — checklist

1. Thêm tên vào enum `ActionType`.
2. Viết method handler trong controller phù hợp (`UserController` / `AuctionController` / `BidController` / `AdminController`). Method signature: `Response handle(Request req, ClientHandler ctx)`.
3. `RequestRouter.register(ActionType.X, AuthLevel.Y, controller::method)` — chỉ 1 dòng.
4. Cập nhật mục 4 trong file này.
5. Nếu cần push event mới: thêm vào `ActionType`, expose method trên `ClientHandler` (`AuctionObserver` impl) hoặc bridge khác, document ở mục 5.

---

## 9. Tham chiếu code

| Khái niệm | File |
|---|---|
| Enum action | `src/main/java/com/auction/protocol/ActionType.java` |
| Envelope | `protocol/Request.java`, `protocol/Response.java`, `protocol/ResponseStatus.java` |
| Router + auth middleware | `server/RequestRouter.java`, `server/command/AuthLevel.java` |
| Session per-connection | `server/Session.java`, `server/ClientHandler.java` |
| Push bridge | `server/observer/AuctionEventManager.java` |
| Controllers | `controller/UserController.java`, `controller/AuctionController.java`, `controller/BidController.java`, `controller/AdminController.java` |
