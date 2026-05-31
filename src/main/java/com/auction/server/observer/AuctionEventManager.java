package com.auction.server.observer;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.observer.AuctionObserver;
import com.auction.service.AuctionManager;
import com.auction.util.AppLogger;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<UUID, Set<AuctionObserver>> watchersByAuction = new ConcurrentHashMap<>();
    private final Map<UUID, Set<AuctionObserver>> onlineObserversByUser = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> participantsByAuction = new ConcurrentHashMap<>();

    private AuctionEventManager() {
        AuctionManager.getInstance().addGlobalObserver(this);
    }

    public void subscribe(UUID auctionId, AuctionObserver observer) {
        watchersByAuction.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
                .add(observer);
    }

    public void unsubscribe(UUID auctionId, AuctionObserver observer) {
        removeObserverFromMap(watchersByAuction, auctionId, observer);
    }

    public void registerUserSession(UUID userId, AuctionObserver observer) {
        if (userId == null || observer == null) return;

        onlineObserversByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(observer);
    }

    public void unregisterUserSession(UUID userId, AuctionObserver observer) {
        if (userId == null || observer == null) return;

        removeObserverFromMap(onlineObserversByUser, userId, observer);
    }

    public void registerParticipant(UUID auctionId, UUID bidderId) {
        if (auctionId == null || bidderId == null) return;

        participantsByAuction.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
                .add(bidderId);
    }

    public void registerParticipants(UUID auctionId, Collection<UUID> bidderIds) {
        if (auctionId == null || bidderIds == null || bidderIds.isEmpty()) return;

        Set<UUID> participants = participantsByAuction.computeIfAbsent(
                auctionId, k -> ConcurrentHashMap.newKeySet());

        bidderIds.stream()
                .filter(java.util.Objects::nonNull)
                .forEach(participants::add);
    }

    public void unsubscribeAll(AuctionObserver observer) {
        if (observer == null) return;

        watchersByAuction.values().forEach(set -> set.remove(observer));
        onlineObserversByUser.values().forEach(set -> set.remove(observer));
    }

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        registerParticipant(auction.getId(), bid.getBidderId());
        forEachRecipient(auction.getId(), observer -> observer.onBidPlaced(auction, bid));
    }

    @Override
    public void onAuctionExtended(Auction auction, int extendedSeconds) {
        forEachRecipient(auction.getId(),
                observer -> observer.onAuctionExtended(auction, extendedSeconds));
    }

    @Override
    public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
        forEachRecipient(auction.getId(),
                observer -> observer.onStatusChanged(auction, oldStatus, newStatus));
    }

    private void forEachRecipient(UUID auctionId, Consumer<AuctionObserver> action) {
        Set<AuctionObserver> recipients =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        Set<AuctionObserver> watchers = watchersByAuction.get(auctionId);
        if (watchers != null) {
            recipients.addAll(watchers);
        }

        Set<UUID> participants = participantsByAuction.get(auctionId);
        if (participants != null) {
            for (UUID userId : participants) {
                Set<AuctionObserver> onlineObservers = onlineObserversByUser.get(userId);
                if (onlineObservers != null) {
                    recipients.addAll(onlineObservers);
                }
            }
        }

        for (AuctionObserver observer : recipients) {
            try {
                action.accept(observer);
            } catch (Exception e) {
                log.warning(() -> "Observer lỗi: " + e.getMessage());
            }
        }
    }

    private static <K> void removeObserverFromMap(Map<K, Set<AuctionObserver>> map,
                                                  K key,
                                                  AuctionObserver observer) {
        Set<AuctionObserver> observers = map.get(key);
        if (observers == null) return;

        observers.remove(observer);

        if (observers.isEmpty()) {
            map.remove(key, observers);
        }
    }
}