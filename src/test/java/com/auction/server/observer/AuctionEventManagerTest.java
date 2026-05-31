package com.auction.server.observer;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.observer.AuctionObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionEventManager")
class AuctionEventManagerTest {

    private final AuctionEventManager mgr = AuctionEventManager.getInstance();

    private static Auction auction() {
        LocalDateTime now = LocalDateTime.now();
        return new Auction(UUID.randomUUID(), UUID.randomUUID(),
                now.minusMinutes(1), now.plusHours(1),
                new BigDecimal("1000"), new BigDecimal("100"));
    }

    private static AuctionObserver counting(AtomicInteger bid, AtomicInteger status, AtomicInteger ext) {
        return new AuctionObserver() {
            public void onBidPlaced(Auction a, BidTransaction b) { bid.incrementAndGet(); }
            public void onStatusChanged(Auction a, AuctionStatus o, AuctionStatus n) { status.incrementAndGet(); }
            public void onAuctionExtended(Auction a, int s) { ext.incrementAndGet(); }
        };
    }

    @Test
    @DisplayName("subscribe rồi forward đúng các event")
    void subscribeForwards() {
        Auction a = auction();
        AtomicInteger bid = new AtomicInteger(), status = new AtomicInteger(), ext = new AtomicInteger();
        AuctionObserver obs = counting(bid, status, ext);

        mgr.subscribe(a.getId(), obs);
        try {
            mgr.onBidPlaced(a, new BidTransaction(a.getId(), UUID.randomUUID(), new BigDecimal("1000")));
            mgr.onStatusChanged(a, AuctionStatus.PENDING, AuctionStatus.RUNNING);
            mgr.onAuctionExtended(a, 30);

            assertEquals(1, bid.get());
            assertEquals(1, status.get());
            assertEquals(1, ext.get());
        } finally {
            mgr.unsubscribe(a.getId(), obs);
        }
    }

    @Test
    @DisplayName("unsubscribe thì không nhận event nữa")
    void unsubscribe() {
        Auction a = auction();
        AtomicInteger bid = new AtomicInteger(), status = new AtomicInteger(), ext = new AtomicInteger();
        AuctionObserver obs = counting(bid, status, ext);

        mgr.subscribe(a.getId(), obs);
        mgr.unsubscribe(a.getId(), obs);
        mgr.onBidPlaced(a, new BidTransaction(a.getId(), UUID.randomUUID(), new BigDecimal("1000")));

        assertEquals(0, bid.get());
    }

    @Test
    @DisplayName("unsubscribeAll gỡ khỏi mọi phiên")
    void unsubscribeAll() {
        Auction a1 = auction();
        Auction a2 = auction();
        AtomicInteger bid = new AtomicInteger(), status = new AtomicInteger(), ext = new AtomicInteger();
        AuctionObserver obs = counting(bid, status, ext);

        mgr.subscribe(a1.getId(), obs);
        mgr.subscribe(a2.getId(), obs);
        mgr.unsubscribeAll(obs);

        mgr.onBidPlaced(a1, new BidTransaction(a1.getId(), UUID.randomUUID(), new BigDecimal("1000")));
        mgr.onBidPlaced(a2, new BidTransaction(a2.getId(), UUID.randomUUID(), new BigDecimal("1000")));
        assertEquals(0, bid.get());
    }

    @Test
    @DisplayName("forward tới phiên chưa có ai subscribe - không lỗi")
    void noSubscribers_noError() {
        Auction a = auction();
        assertDoesNotThrow(() ->
                mgr.onBidPlaced(a, new BidTransaction(a.getId(), UUID.randomUUID(), new BigDecimal("1000"))));
    }
}
