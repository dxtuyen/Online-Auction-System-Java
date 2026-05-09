com.auction
│
├── common/                         # Shared giữa client & server
│   ├── protocol/                   # Giao tiếp network
│   │   ├── Request.java
│   │   ├── Response.java
│   │   └── ActionType.java        # enum thay vì String
│   │
│   └── dto/                        # Data Transfer Object (record)
│       ├── AuctionDto.java
│       ├── BidDto.java
│       ├── UserDto.java
│       └── ItemDto.java
│
├── domain/                         # Core business (model mới)
│   ├── entity/
│   │   ├── User.java
│   │   ├── Auction.java
│   │   ├── Item.java
│   │   └── profile/                # Strategy pattern cho role
│   │       ├── BidderProfile.java
│   │       ├── SellerProfile.java
│   │       └── AdminProfile.java
│   │
│   ├── enums/
│   ├── exception/
│   ├── factory/
│   └── observer/                   # CHỈ giữ 1 hệ observer
│       ├── AuctionObserver.java
│       └── AuctionEvent.java
│
├── service/                        # Business logic (KHÔNG static singleton)
│   ├── AuctionService.java
│   ├── BiddingService.java
│   ├── UserService.java
│   ├── AutoBidService.java
│   └── PaymentService.java
│
├── repository/                     # Persistence abstraction
│   ├── UserRepository.java
│   ├── AuctionRepository.java
│   ├── BidRepository.java
│   ├── ItemRepository.java
│   │
│   └── inmemory/                   # Implement tạm (thi là đủ)
│       ├── InMemoryUserRepository.java
│       ├── InMemoryAuctionRepository.java
│       ├── InMemoryBidRepository.java
│       └── InMemoryItemRepository.java
│
├── security/
│   ├── PasswordEncoder.java
│   └── SessionManager.java         # Token-based session
│
├── server/
│   ├── ServerApp.java              # Entry point server
│   ├── ClientHandler.java
│   ├── RequestRouter.java
│   │
│   ├── controller/                 # Nhận request → gọi service
│   │   ├── AuthController.java
│   │   ├── AuctionController.java
│   │   ├── BidController.java
│   │   └── ItemController.java
│   │
│   └── push/                       # Thay cho observer cũ phía server
│       ├── PushBroker.java
│       └── ClientPushAdapter.java
│
├── client/                         # JavaFX app
│   ├── ClientApp.java
│   │
│   ├── controller/
│   ├── service/                    # gọi API server
│   │   └── ClientApiService.java
│   │
│   └── viewmodel/                  # (optional nhưng rất nên có)
│
├── util/
│   ├── IdGenerator.java
│   ├── JsonHelper.java
│   └── Logger.java
│
├── config/
│   └── AppConfig.java
│
└── resources/
├── fxml/
└── css/