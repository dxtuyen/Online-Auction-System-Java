com.auction/
├── model/                          # Domain layer (dùng chung Client+Server)
│   ├── entity/                     # ⚠️ CHỈ CHỨA ENTITY
│   │   ├── Entity.java
│   │   ├── User.java
│   │   ├── Admin.java
│   │   ├── Bidder.java
│   │   ├── Seller.java
│   │   ├── Item.java
│   │   ├── Electronics.java        # ❗ rename từ Electronic
│   │   ├── Art.java
│   │   ├── Vehicle.java
│   │   ├── OtherItem.java
│   │   ├── Auction.java
│   │   └── BidTransaction.java
│   ├── enums/
│   │   ├── Role.java
│   │   ├── UserStatus.java
│   │   ├── AuctionStatus.java
│   │   ├── BidStatus.java
│   │   ├── ItemCategory.java
│   │   └── ItemCondition.java
│   ├── exception/                  # ✅ Bạn đã làm đúng
│   │   ├── AuctionException.java
│   │   ├── AuctionClosedException.java
│   │   ├── IllegalAuctionStateException.java
│   │   ├── InsufficientBalanceException.java
│   │   └── InvalidBidException.java
│   ├── factory/                    # 🆕 Move ItemFactory vào đây
│   │   └── ItemFactory.java
│   └── observer/                   # 🆕 Thêm package này
│       └── AuctionObserver.java
│
├── service/                        # Business logic (Singleton, orchestration)
│   ├── AuctionManager.java         # Singleton - quản lý tất cả Auction
│   ├── BiddingService.java         # Logic đặt bid + check balance
│   ├── UserService.java            # Đăng ký/đăng nhập
│   └── AutoBidService.java         # Cho chức năng nâng cao
│
├── repository/                     # 🆕 Persistence layer (Serialization/DAO)
│   ├── UserRepository.java
│   ├── AuctionRepository.java
│   └── BidRepository.java
│
├── network/                        # 🆕 Network protocol (Client+Server share)
│   ├── Message.java
│   ├── MessageType.java
│   └── dto/                        # DTOs cho gửi qua socket
│       ├── BidRequest.java
│       └── AuctionResponse.java
│
├── server/                         # 🆕 Server-side only
│   ├── ServerApp.java
│   ├── ClientHandler.java
│   └── handler/
│       └── BidRequestHandler.java
│
├── client/                         # 🆕 JavaFX client (MVC)
│   ├── ClientApp.java
│   ├── controller/                 # FXML controllers
│   ├── view/                       # FXML resources nên ở src/main/resources
│   └── service/                    # Client-side service (network calls)
│
├── util/                           # Helpers
│   ├── PasswordHasher.java
│   └── JsonUtil.java
│
└── Main.java                       # Có thể bỏ nếu đã có ServerApp + ClientApp