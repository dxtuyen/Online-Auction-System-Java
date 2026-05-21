# Lock Ordering Contract — Auction System

Tài liệu này định nghĩa **thứ tự mua/giải phóng lock** cho mọi flow ghi state trong hệ thống. Mọi thay đổi code chạm vào `Auction`, `User.balance`, hay observer chain **phải** đọc và tuân thủ file này.

> Lý do tồn tại: deadlock không reproduce được trong dev, chỉ "bùng" trong demo dưới load. Quy ước cứng từ đầu rẻ hơn debug sau.

---

## 1. Inventory các lock trong hệ thống

| ID | Lock | Granularity | Kiểu | Where |
|----|------|-------------|------|-------|
| **L1** | `Auction.lock` | per-instance | `ReentrantLock` (re-entrant, non-fair) | `Auction.java` |
| **L2** | `User` intrinsic monitor | per-instance | `synchronized` method | `User.checkPassword`, `tryReserve`, `release`, `addRevenue`, `getBalance/setBalance`, `getRevenue/setRevenue`, `changePassword`, `hasEnoughBalance`, `getHashedPassword`, `getPasswordSalt` |
| **L3** | `ClientHandler` intrinsic | per-instance | `synchronized send()` | `ClientHandler.send` |
| **L4** | `ServerConnection` intrinsic | per-instance | `synchronized send()` | `ServerConnection.send` |
| **L5** | `ClientModel` class monitor | static | `synchronized static getInstance()` | `ClientModel.java` |

Ngoài ra có các struct **lock-free** (không tính vào ordering): `ConcurrentHashMap`, `CopyOnWriteArrayList`, `AtomicInteger`, `volatile`, `ThreadLocal`. Chúng không tham gia deadlock potential.

---

## 2. THỨ TỰ MUA LOCK (chính tắc)

```
L2(User: reserver)  →  L1(Auction)  →  L2(User: prev leader)  →  L3(ClientHandler)
```

Quy tắc bất biến:

- **R1**: Không bao giờ mua `L1 Auction.lock` rồi mới mua `L2 User`. Phải reserve TRƯỚC khi vào lock của Auction.
- **R2**: Không bao giờ giữ `L1 Auction.lock` trong khi gọi `notifyXxx(...)`. Notify chuỗi observer LUÔN chạy **ngoài lock** — đã enforce ở `Auction.placeBid/extend/transitionTo` (block `try/finally { lock.unlock(); } then notify(...)`).
- **R3**: Không bao giờ mua 2 `L2 User` cùng lúc trên cùng thread (reserver và prev leader). Reserve trên user A xong → release user B sau khi đã thoát `L1`.
- **R4**: `L3 ClientHandler.send` chỉ được mua từ thread observer chain HOẶC thread request handler — không tự lock-chain với L1/L2.
- **R5**: Khi cần đọc nhanh state của `Auction` mà không mutate (`isActive`, `getCurrentPrice`, …) — **không lock**, chấp nhận stale tích tắc. JLS đảm bảo reference assignment của `BigDecimal`/`UUID` atomic; không có inconsistent compound state nào lộ ra ngoài.

---

## 3. Luồng chính `BidManager.placeBid` — minh họa contract

```
[Thread = ClientHandler-A worker]

  ┌─ L2 User(A).tryReserve(amount)         ── synchronized
  │     atomic check balance >= amount
  │     balance -= amount
  └─ release L2 User(A)

  ┌─ L1 Auction.lock                       ── ReentrantLock
  │     validate (status, time, bidder!=seller, amount>=minNext)
  │     snapshot prevBidderId, prevAmount
  │     mutate: currentPrice, highestBidderId, totalBids
  │     bid.markValid()
  └─ release L1                            ◄── BẮT BUỘC unlock trước khi notify

  notifyBidPlaced(bid)                     ── chuỗi observer, KHÔNG cầm L1
    ├─ AuctionManager.internalObserver
    │    └─ (anti-sniping) auction.extend(sec)
    │         ┌─ L1 Auction.lock (re-enter OK)
    │         │     endTime += sec
    │         └─ release L1
    │         notifyAuctionExtended(...)  (đệ quy 1 cấp, vẫn ngoài lock đầu)
    │    └─ globalObservers.forEach
    │         ├─ BidManager status watcher (no lock taken)
    │         └─ AuctionEventManager
    │              └─ ClientHandler[i].send(JSON)
    │                   ┌─ L3 synchronized
    │                   │     writer.println(...)
    │                   └─ release L3
    └─ (return từ notify chain)

  ┌─ L2 User(prevLeader).release(prevAmount)   ── KHÁC user A
  │     balance += prevAmount
  └─ release L2

  recordBid()              (CHM.computeIfAbsent + CoW.add — lock-free)
  tryTriggerAutoBids()     (đọc CoW list — lock-free; nếu trigger placeBid
                            mới thì ThreadLocal inAutoBid chặn re-entry storm)
```

Quan sát quan trọng: tại **không thời điểm nào** thread giữ đồng thời 2 lock khác kiểu. Đây là điều kiện đủ để vô deadlock với phần hệ thống hiện tại.

---

## 4. Các flow khác — tóm tắt lock taken

| Flow | Lock sequence | Ghi chú |
|------|---------------|---------|
| `Auction.extend` | L1 | Re-entrant từ internalObserver OK |
| `Auction.transitionTo` | L1 → notify ngoài lock | Scheduler tick gọi flow này |
| `AuctionManager.tickLifecycle` | L1 (qua transitionTo) → L2 User(seller) (trong `trySettle.addRevenue`) | Đúng thứ tự L1 trước L2. Nhưng vì `tickLifecycle` không reserve trước, không vi phạm R1 (R1 áp dụng cho path "đặt bid"). |
| `AuctionManager.closeAuction` | L1 (transitionTo) → có thể chạy trySettle (→ L2) | Như trên |
| `User.checkPassword`/`changePassword` | L2 (cùng user) | Không đụng L1 |
| `UserManager.login` | L2 (qua checkPassword) | Không đụng L1 |
| `ClientHandler.send` (gọi từ observer push) | L3 | Có thể block trên socket I/O — xem §6 |
| `ClientModel.getInstance` | L5 | One-shot lazy init |

> **Trường hợp 2 lock L2 trên 2 user khác nhau** chỉ xuất hiện trong `BidManager.placeBid` (reserver + prev leader) và **luôn tuần tự**, không nested. Vì vậy không có chu trình.

---

## 5. Quy tắc khi thêm code mới

5.1. **Muốn mutate `Auction` state**: viết method MỚI trong `Auction.java`, mua L1, snapshot + mutate trong `try { } finally { lock.unlock(); }`, **notify ngoài lock**.

5.2. **Muốn đụng `User.balance`**: dùng `tryReserve` / `release` / `addRevenue` đã có. KHÔNG viết getter-then-setter pattern (gây race).

5.3. **Muốn 1 service mua >=2 lock**: STOP. Mở PR thảo luận. Ngoại lệ phải được ghi vào file này.

5.4. **Muốn cho observer chạy code dài (gửi mail, log file, network call)**: bắt buộc **dispatch async** (ExecutorService riêng) — KHÔNG block thread đang nằm trong chuỗi notify, vì nó là thread đặt bid của user khác.

5.5. **Đừng lạm dụng `synchronized` trên `Manager`**: các singleton đã dùng `ConcurrentHashMap`. Thêm `synchronized` method ngoài cùng sẽ vô hiệu hoá lợi thế concurrent của Map.

---

## 6. Điểm yếu đã biết (không vi phạm contract, nhưng cần biết)

| # | Vấn đề | Mức | Khắc phục đề xuất |
|---|--------|-----|-------------------|
| W1 | `ClientHandler.send` block trên `writer.println` (socket I/O). Một client mạng kém kéo chậm cả chuỗi notify của user khác. | TRUNG | Async writer per ClientHandler (queue + 1 thread riêng). |
| W2 | `ClientModel.responseQueues` dùng `action` làm key → 2 request cùng action song song trên client có thể nhận nhầm response. | TRUNG | Đổi key sang `requestId` (UUID gắn cả Request + Response). |
| W3 | `AutoBid.active` chỉ `volatile`, không atomic với `isActive()`-then-`placeBid`. 1 lần bid "lậu" có thể lọt (vẫn được validate ở entity, không vỡ invariant). | THẤP | Thay bằng `AtomicBoolean` + `compareAndSet`. |
| W4 | `trySettle` không tìm thấy prev leader (`UserManager.findById` empty) → tiền treo, không có job reconcile. | THẤP (đề BTL) | Reservation reconcile job định kỳ; structured logging fail-soft branch. |
| W5 | `System.err.println` cho lỗi observer/scheduler → không structured. | THẤP | Thay bằng `java.util.logging` hoặc SLF4J. |

---

## 7. Test gợi ý cho contract này

- **T1**: 100 thread cùng `placeBid` trên 1 auction, cùng user. Kết thúc: chỉ 1 bid VALID, balance user `= initial - currentPrice`, không "treo" tiền. (Verify R1/R3.)
- **T2**: 50 thread `placeBid` trong khi scheduler đang `transitionTo(FINISHED)`. Mỗi bid hoặc thắng (VALID) hoặc nhận `AuctionClosedException`, **không** lọt VALID sau FINISHED. (Verify L1 atomicity.)
- **T3**: Đăng ký 1 observer cố tình `Thread.sleep(2000)`. Bid của user khác KHÔNG bị chậm theo. **Hiện tại sẽ FAIL** vì notify chạy đồng bộ — xem W1.
- **T4**: Cùng `User` đặt 2 bid song song trên 2 auction khác nhau với balance vừa đủ cho 1 → đúng 1 thắng, 1 nhận `InsufficientBalanceException`. (Verify L2 atomic tryReserve.)

---

## 8. Changelog

| Ngày | Thay đổi | Người |
|------|---------|-------|
| 2026-05-21 | Khởi tạo contract dựa trên codebase commit `7c82c53`. | (BTL) |
