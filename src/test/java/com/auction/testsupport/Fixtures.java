package com.auction.testsupport;

import com.auction.model.entity.Auction;
import com.auction.model.entity.Electronics;
import com.auction.model.entity.Item;
import com.auction.model.entity.User;
import com.auction.model.enums.ItemCondition;
import com.auction.model.enums.Role;
import com.auction.persistence.dao.*;
import com.auction.security.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tạo các entity đã persist xuống H2 để dùng làm FK parent trong DAO test.
 */
public final class Fixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private static final UserDao userDao = new MysqlUserDao();
    private static final ItemDao itemDao = new MysqlItemDao();
    private static final AuctionDao auctionDao = new MysqlAuctionDao();

    private Fixtures() { }

    public static User persistUser() {
        int n = SEQ.incrementAndGet();
        return persistUser("user" + n, "user" + n + "@example.com", new BigDecimal("1000000"));
    }

    public static User persistUser(String username, String email, BigDecimal balance) {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("password123", salt);
        User u = new User(username, hash, salt, email, "Full Name", Role.NORMAL);
        if (balance != null && balance.signum() > 0) u.setBalance(balance);
        userDao.insert(u);
        return u;
    }

    public static Item persistItem(UUID sellerId) {
        Item item = new Electronics("iPhone " + SEQ.incrementAndGet(), "desc", sellerId,
                new BigDecimal("1000"), List.of("img1.jpg", "img2.jpg"),
                ItemCondition.USED, "Apple", "15 Pro", 12);
        itemDao.insert(item);
        return item;
    }

    /** Auction RUNNING bắt đầu trong quá khứ, kết thúc xa trong tương lai. */
    public static Auction persistRunningAuction(UUID itemId, UUID sellerId) {
        LocalDateTime now = LocalDateTime.now();
        Auction a = new Auction(itemId, sellerId,
                now.minusHours(1), now.plusHours(2),
                new BigDecimal("1000"), new BigDecimal("100"));
        a.transitionTo(com.auction.model.enums.AuctionStatus.RUNNING);
        auctionDao.insert(a);
        return a;
    }

    public static Auction persistPendingAuction(UUID itemId, UUID sellerId) {
        LocalDateTime now = LocalDateTime.now();
        Auction a = new Auction(itemId, sellerId,
                now.plusHours(1), now.plusHours(2),
                new BigDecimal("1000"), new BigDecimal("100"));
        auctionDao.insert(a);
        return a;
    }
}
