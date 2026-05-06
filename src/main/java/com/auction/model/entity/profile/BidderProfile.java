package com.auction.model.entity.profile;

import com.auction.model.enums.Role;
import com.auction.model.exception.InsufficientBalanceException;

import java.math.BigDecimal;

public class BidderProfile implements RoleProfile {

    private static final long serialVersionUID = 1L;

    /**
     * Volatile thay vì synchronized cho reader: BigDecimal immutable, chỉ cần thấy reference mới nhất
     */
    private volatile BigDecimal balance;

    /**
     * TAO BIDDERPROFILE, BALANCE = 0
     */
    public BidderProfile() {
        this(BigDecimal.ZERO);
    }

    /**
     * LOAD TU DATABASE, HOAC TAO DE TEST
     */
    public BidderProfile(BigDecimal initialBalance) {
        balance = RoleProfile.requireNonNegative(initialBalance, "balance");
    }

    @Override
    public Role getRole() {
        return Role.BIDDER;
    }

    /**
     * GET BALANCE - không cần synchronized vì balance là volatile + BigDecimal immutable
     */
    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * HOAN TIEN SAU KHI THUA DAU GIA VAO TAI KHOAN
     */
    public synchronized void credit(BigDecimal amount) {
        RoleProfile.requirePositive(amount, "credit amount");
        balance = this.balance.add(amount);
    }

    /**
     * TRU TIEN DAT COC, BAO LOI NEU KHONG DU
     */
    public synchronized void debit(BigDecimal amount) {
        RoleProfile.requirePositive(amount, "debit amount");
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Số dư không đủ. Hiện có: " + balance + ", cần: " + amount);
        }
        balance = balance.subtract(amount);
    }

    /**
     * CHECK TRUOC KHI BID - UI HELPER
     */
    public boolean hasEnoughBalance(BigDecimal amount) {
        RoleProfile.requirePositive(amount, "amount");
        return balance.compareTo(amount) >= 0;
    }

    @Override
    public String toString() {
        return "BidderProfile{balance=" + balance + "}";
    }
}
