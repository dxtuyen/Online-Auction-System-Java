# Online Auction System (Java)

Hệ thống đấu giá trực tuyến viết bằng Java 17, kiến trúc client–server qua TCP socket + JSON. Client là ứng dụng JavaFX, server lưu dữ liệu vào MySQL.

## Tính năng chính

- Đăng ký / đăng nhập, phân quyền Bidder / Seller / Admin
- Tạo phiên đấu giá với nhiều loại item (Art, Electronics, Vehicle, Other)
- Đặt giá thủ công + auto-bid
- Anti-sniping: tự động gia hạn phiên khi có bid ở phút cuối
- Upload ảnh avatar / item (lưu local trong `uploads/`)
- Admin panel quản lý user và phiên đấu giá

## Yêu cầu

- JDK 17
- Maven 3.9+
- Docker Desktop (để chạy MySQL qua `docker-compose`), hoặc MySQL 8 local

## Cài đặt

```bash
cp .env.example .env       # rồi sửa lại password DB nếu cần
docker compose up -d       # bật MySQL — schema được nạp tự động lần đầu
```

## Chạy

Server (port 8888 mặc định):

```bash
mvn exec:java
```

Client (JavaFX):

```bash
mvn javafx:run
```

Có thể chạy nhiều client cùng lúc kết nối tới một server.

## Cấu hình

Tất cả config đọc từ `.env` (xem `.env.example` để biết các key — DB credentials, `SERVER_PORT`, `SERVER_MAX_THREADS`, `SNIPING_THRESHOLD_SECONDS`, `SNIPING_EXTENSION_SECONDS`).

## Cấu trúc dự án

Xem [`docs/packages.md`](docs/packages.md) để biết vai trò từng package và quy tắc phụ thuộc.

Tóm tắt cấp cao:

```
src/main/java/com/auction/
├── bootstrap/      # seed dữ liệu mẫu lúc start
├── client/         # JavaFX UI + kết nối server
├── server/         # Socket server, RequestRouter, ClientHandler
├── controller/     # nhận Request từ client → gọi service
├── service/        # business logic (AuctionManager, BidManager, ...)
├── persistence/    # Database, DAO (MySQL)
├── model/          # entity, enums, exception, factory, observer
├── protocol/       # Request / Response / ActionType
├── security/       # PasswordEncoder
├── config/         # AppConfig (đọc .env)
└── util/           # AppLogger, JsonHelper, MoneyHelper, ...
```

## Test

```bash
mvn test
```
