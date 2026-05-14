package com.auction.server.observer;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.observer.AuctionObserver;
import com.auction.service.AuctionManager;
import com.auction.util.AppLogger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Per-auction subscription registry — quản lý observer theo từng phiên cho realtime push.
 *
 * <p>Đóng vai trò CẦU NỐI giữa Observer pattern ở tầng domain và tầng network:
 * <ul>
 *   <li>Khi singleton khởi tạo, tự đăng ký làm global observer của {@link AuctionManager}.</li>
 *   <li>Mọi sự kiện domain ({@code onBidPlaced}, {@code onAuctionExtended}, {@code onStatusChanged})
 *       phát ra từ {@link Auction} đều chảy về đây qua {@code AuctionManager.globalObservers}.</li>
 *   <li>Tra map {@code auctionId → subscribers} và forward đúng cho các observer
 *       (thường là {@code ClientHandler}) đang theo dõi phiên đó.</li>
 * </ul>
 *
 * <p>Trước đây tầng server có một interface {@code AuctionObserver} riêng và phải gọi
 * {@code notifyXxx()} tay — không ai gọi nên client không nhận được push. Bây giờ chỉ
 * còn 1 interface {@link AuctionObserver} duy nhất ở tầng model, event tự chảy về đây
 * và không thể quên đường nào.</p>
 *
 * <p>Dùng {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList} để thread-safe
 * — nhiều thread cùng subscribe/unsubscribe không bị ConcurrentModification.</p>
 */
public final class AuctionEventManager implements AuctionObserver {

    private static final Logger log = AppLogger.get(AuctionEventManager.class);

    private static final class Holder {
        private static final AuctionEventManager INSTANCE = new AuctionEventManager();
    }

    public static AuctionEventManager getInstance() {
        return Holder.INSTANCE;
    }

    /** Map: auctionId → list observer đang xem phiên đó. */
    private final Map<UUID, List<AuctionObserver>> observers = new ConcurrentHashMap<>();

    private AuctionEventManager() {
        // Tự đăng ký làm global observer để mọi event domain tự động chảy về đây.
        AuctionManager.getInstance().addGlobalObserver(this);
    }

    /** Client đăng ký xem phiên. */
    public void subscribe(UUID auctionId, AuctionObserver observer) {
        observers.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>())
                .add(observer);
    }

    /** Client rời khỏi phiên. */
    public void unsubscribe(UUID auctionId, AuctionObserver observer) {
        List<AuctionObserver> list = observers.get(auctionId);
        if (list != null) list.remove(observer);
    }

    /** Gỡ observer khỏi TẤT CẢ phiên (khi client disconnect). */
    public void unsubscribeAll(AuctionObserver observer) {
        observers.values().forEach(list -> list.remove(observer));
    }

    // ============== AuctionObserver — forward event tới subscribers theo auctionId ==============

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        forEachSubscriber(auction.getId(), o -> o.onBidPlaced(auction, bid));
    }

    @Override
    public void onAuctionExtended(Auction auction, int extendedSeconds) {
        forEachSubscriber(auction.getId(), o -> o.onAuctionExtended(auction, extendedSeconds));
    }

    @Override
    public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
        forEachSubscriber(auction.getId(), o -> o.onStatusChanged(auction, oldStatus, newStatus));
    }

    private void forEachSubscriber(UUID auctionId, Consumer<AuctionObserver> action) {
        List<AuctionObserver> list = observers.get(auctionId);
        if (list == null) return;
        for (AuctionObserver o : list) {
            try {
                action.accept(o);
            } catch (Exception e) {
                // Một observer lỗi không ảnh hưởng các observer khác.
                log.warning(() -> "Observer lỗi: " + e.getMessage());
            }
        }
    }
}
