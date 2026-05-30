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

public final class AuctionEventManager implements AuctionObserver {

    private static final Logger log = AppLogger.get(AuctionEventManager.class);

    private static final class Holder {
        private static final AuctionEventManager INSTANCE = new AuctionEventManager();
    }

    public static AuctionEventManager getInstance() {
        return Holder.INSTANCE;
    }

    private final Map<UUID, List<AuctionObserver>> observers = new ConcurrentHashMap<>();

    private AuctionEventManager() {

        AuctionManager.getInstance().addGlobalObserver(this);
    }

    public void subscribe(UUID auctionId, AuctionObserver observer) {
        observers.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>())
                .add(observer);
    }

    public void unsubscribe(UUID auctionId, AuctionObserver observer) {
        List<AuctionObserver> list = observers.get(auctionId);
        if (list != null) list.remove(observer);
    }

    public void unsubscribeAll(AuctionObserver observer) {
        observers.values().forEach(list -> list.remove(observer));
    }

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

                log.warning(() -> "Observer lỗi: " + e.getMessage());
            }
        }
    }
}
