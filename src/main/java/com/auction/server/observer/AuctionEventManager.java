package com.auction.server.observer;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subject trong Observer pattern — quản lý danh sách observer theo từng phiên.
 *
 * <p>Mỗi phiên có list observer riêng (những client đang "xem" phiên đó).
 * Khi có sự kiện → notify tất cả observer của phiên tương ứng.</p>
 *
 * <p>Dùng {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList} để
 * thread-safe — nhiều thread cùng subscribe/unsubscribe không bị lỗi.</p>
 */
public class AuctionEventManager {

    private static AuctionEventManager instance;

    /** Map: auctionId → list observer đang xem phiên đó. */
    private final Map<Integer, List<AuctionObserver>> observers = new ConcurrentHashMap<>();

    private AuctionEventManager() {}

    public static synchronized AuctionEventManager getInstance() {
        if (instance == null) instance = new AuctionEventManager();
        return instance;
    }

    /** Client đăng ký xem phiên. */
    public void subscribe(int auctionId, AuctionObserver observer) {
        observers.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>())
                .add(observer);
    }

    /** Client rời khỏi phiên. */
    public void unsubscribe(int auctionId, AuctionObserver observer) {
        List<AuctionObserver> list = observers.get(auctionId);
        if (list != null) list.remove(observer);
    }

    /** Gỡ observer khỏi TẤT cả phiên (khi client disconnect). */
    public void unsubscribeAll(AuctionObserver observer) {
        observers.values().forEach(list -> list.remove(observer));
    }

    /** Thông báo có bid mới — gọi sau khi AuctionService.placeBid() thành công. */
    public void notifyNewBid(Auction auction, BidTransaction bid) {
        List<AuctionObserver> list = observers.get(auction.getId());
        if (list != null) {
            for (AuctionObserver o : list) {
                try {
                    o.onNewBid(auction, bid);
                } catch (Exception e) {
                    // Observer lỗi không ảnh hưởng các observer khác
                    System.err.println("[Observer] onNewBid lỗi: " + e.getMessage());
                }
            }
        }
    }

    public void notifyStatusChanged(Auction auction) {
        List<AuctionObserver> list = observers.get(auction.getId());
        if (list != null) {
            for (AuctionObserver o : list) {
                try { o.onAuctionStatusChanged(auction); }
                catch (Exception e) { System.err.println("[Observer] statusChanged lỗi: " + e.getMessage()); }
            }
        }
    }

    public void notifyExtended(Auction auction) {
        List<AuctionObserver> list = observers.get(auction.getId());
        if (list != null) {
            for (AuctionObserver o : list) {
                try { o.onAuctionExtended(auction); }
                catch (Exception e) { System.err.println("[Observer] extended lỗi: " + e.getMessage()); }
            }
        }
    }
}
