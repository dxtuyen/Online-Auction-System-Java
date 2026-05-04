package com.auction.model.entity.profile;

import com.auction.model.enums.Role;
import com.auction.model.exception.InsufficientBalanceException;

import java.math.BigDecimal;

import java.util.Objects;

public class BidderProfile implements RoleProfile {

    private static final long serialVersionUID = 1L;

    private  BigDecimal balance;

    /** VALIDATE */
    private static BigDecimal validateNonNegative(BigDecimal v) {
        Objects.requireNonNull(v, "So du khong the la null");
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("So du khong the am");
        }
        return v;
    }

    private static void validatePositive(BigDecimal v) {
        Objects.requireNonNull(v, "So du khong the la null");
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Gia tri phai > 0");
        }
    }

    /** TAO BIDDERPROFILE, BALANCE = 0 */
    public BidderProfile() {
        this(BigDecimal.ZERO);
    }

    /** LOAD TU DATABASE, HOAC TAO DE TEST */
    public BidderProfile(BigDecimal initialBalance) {
        balance = validateNonNegative(initialBalance);
    }

    @Override
    public Role getRole() {
        return Role.BIDDER;
    }

    /** GET BALANCE */
    public synchronized BigDecimal getBalance() {
        return balance;
    }

    /** HOAN TIEN SAU KHI THUA DAU GIA VAO TAI KHOAN */
    public synchronized void credit(BigDecimal amount) {
        validatePositive(amount);
        balance = this.balance.add(amount);
    }

    /** TRU TIEN DAT COC, BAO LOI NEU KHONG DU */
    public synchronized void debit(BigDecimal amount) {
        validatePositive(amount);
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Số dư không đủ. Hiện có: " + balance + ", cần: " + amount);
        }
        balance = balance.subtract(amount);
    }

    /** CHECK TRUOC KHI BID - UI HELPER */
    public synchronized boolean hasEnoughBalance(BigDecimal amount) {
        validatePositive(amount);
        return balance.compareTo(amount) >= 0;
    }

    @Override
    public String toString() {
        return "BidderProfile{balance=" + balance + "}";
    }
}
