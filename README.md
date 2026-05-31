# Online Auction System (Java)

## Mô tả

Online Auction System là hệ thống đấu giá trực tuyến được xây dựng bằng Java theo mô hình Client - Server.Hệ thống cho phép người dùng đăng ký, đăng nhập, tham gia các phiên đấu giá, đặt giá sản phẩm, tạo sản phẩm đấu giá và quản lý phiên đấu giá. Server xử lý logic nghiệp vụ, lưu trữ dữ liệu trong MySQL và giao tiếp với nhiều client thông qua TCP Socket. Client sử dụng JavaFX để cung cấp giao diện đồ họa cho người dùng.

## Phạm vi hệ thống

Hệ thống tập trung vào các chức năng chính:

* Quản lý tài khoản người dùng.
* Phân quyền người dùng: Bidder, Seller, Admin.
* Quản lý sản phẩm đấu giá.
* Tạo và theo dõi phiên đấu giá.
* Đặt giá thủ công.
* Auto-bid.
* Cập nhật trạng thái phiên đấu giá.
* Quản lý dữ liệu bằng MySQL.
* Hỗ trợ nhiều client kết nối đồng thời tới server.


## Công nghệ sử dụng

| Thành phần                 | Công nghệ                     |
| -------------------------- | ----------------------------- |
| Ngôn ngữ lập trình         | Java 17                       |
| Giao diện người dùng       | JavaFX                        |
| Build tool                 | Maven                         |
| Giao tiếp mạng             | TCP Socket                    |
| Định dạng trao đổi dữ liệu | JSON                          |
| Thư viện JSON              | Gson                          |
| Cơ sở dữ liệu              | MySQL                         |
| Kiểm thử                   | JUnit 5, Mockito, H2 Database |
| Đóng gói chương trình      | Maven Shade Plugin            |
| Môi trường chạy DB         | Docker Compose                |

---

## Yêu cầu

- JDK 17
- Maven 3.9+
- Docker Desktop (để chạy MySQL qua `docker-compose`), hoặc MySQL 8 local
- Git, nếu clone project từ GitHub.

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


```
Online-Auction-System-Java/
├── src/
│   ├── main/
│   │   ├── java/com/auction/
│   │   │   ├── bootstrap/      # Khởi tạo dữ liệu mẫu
│   │   │   ├── client/         # JavaFX Client, controller UI, kết nối server
│   │   │   ├── config/         # Đọc cấu hình từ .env / biến môi trường
│   │   │   ├── controller/     # Controller xử lý request nghiệp vụ
│   │   │   ├── model/          # Entity, enum, exception, factory, observer
│   │   │   ├── persistence/    # Database và DAO làm việc với MySQL
│   │   │   ├── protocol/       # Request, Response, ActionType
│   │   │   ├── security/       # Xử lý mã hóa mật khẩu
│   │   │   ├── server/         # ServerMain, ClientHandler, RequestRouter
│   │   │   ├── service/        # Business logic
│   │   │   └── util/           # Tiện ích chung
│   │   └── resources/
│   │       ├── fxml/           # File giao diện JavaFX
│   │       ├── css/            # Style giao diện
│   │       └── sql/            # File schema.sql khởi tạo database
│   └── test/                   # Unit test và integration test
├── dist/                       # Chứa file JAR sau khi build
├── pom.xml                     # Cấu hình Maven
├── docker-compose.yml          # Cấu hình MySQL bằng Docker
├── .env.example                # File mẫu cấu hình môi trường
└── README.md

```
## Vị trí các file .jar

```text
Online-Auction-System-Java/release/
├── client.jar
└── server.jar
```

## Hướng dẫn chạy Server/Client 
### Bước 1: Di chuyển vào thư mục chứa các file thực thi

Mở Terminal và di chuyển vào thư mục release:

```bash
cd release
```
### Bước 2: Khởi chạy Server 

Tại cửa sổ dòng lệnh hiện tại, chạy lệnh sau để khởi động Server máy chủ:

```bash
java -jar server.jar
```
Lưu ý : giữ nguyên cửa sổ này để Server duy trì hoạt động ngầm. Không được tắt cửa sổ này đi.

### Bước 3: Khởi chạy Client 

Mở một cửa sổ Terminal , di chuyển vào thư mục release tương tự như Bước 1, sau đó chạy lệnh:
```bash
java -jar client.jar
```
Lúc này, giao diện người dùng của hệ thống đấu giá sẽ hiển thị.


## Danh sách các chức năng 

- Đăng ký / Đăng nhập / Phân quyền
- Admin panel quản lý user và phiên đấu giá
- Mã hóa mật khẩu
- Tạo Item, Tạo phiên đấu giá
- Cập nhật trạng thái phiên đấu giá.
- Xem danh sách & chi tiết phiên 
- Đặt bid thủ công, xem lịch sử bid
- Cập nhập người đứng đầu phiên
- Xử lý lỗi & ngoại lệ: Đặt giá thấp hơn giá hiện tại, đấu giá khi phiên đã đóng; lỗi dữ liệu, lỗi kết nối
- Xác định người thắng cuộc khi kết thúc phiên
- Vòng đời phiên tự động (State Machine) 
- Concurrent bidding : Nhiều bidder đặt giá cùng thời điểm 
- Realtime push : thông báo bid mới cho tất cả client
- Anti-sniping : Nếu có người đặt giá trong khoảng thời gian cuối của phiên đấu giá, hệ thống tự động gia hạn thời gian đấu giá 
- Auto-Bid : Cho phép người dùng đặt mức giá tự động theo max
- Bid History Visualization : Hiển thị biểu đồ đường (line chart) giá đấu theo thời gian thực
- Giao diện người dùng (GUI) : JavaFX, FXML/CSS

---

## Link báo cáo PDF và video 
- Báo cáo : https://drive.google.com/file/d/1OJ2X70TvlDUpFKYhrrq9EQNewAHeim2C/view?usp=drive_link
- Link video demo: https://youtu.be/eW1Yb3LRwzs
```
