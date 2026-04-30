package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;
import com.auction.model.exception.InsufficientBalanceException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Người đấu giá - có balance để bid.
 *
 * QUAN TRỌNG: balance dùng BigDecimal, KHÔNG dùng double.
 * Lý do: 0.1 + 0.2 != 0.3 trong double → mất tiền của user là chuyện thật.
 */
public class Bidder extends User {

    private static final long serialVersionUID = 1L;

    private BigDecimal balance;

    // ============== CONSTRUCTORS ==============

    /** Tạo bidder mới với balance = 0 */
    public Bidder(String username, String hashedPassword, String email, String fullName) {
        this(username, hashedPassword, email, fullName, BigDecimal.ZERO);
    }

    /** Tạo bidder mới + balance ban đầu */
    public Bidder(String username, String hashedPassword, String email,
                  String fullName, BigDecimal initialBalance) {
        super(username, hashedPassword, email, fullName, Role.BIDDER);
        this.balance = validateBalance(initialBalance);
    }

    /** Restore từ DB */
    public Bidder(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                  String username, String hashedPassword, String email,
                  String fullName, UserStatus status, BigDecimal balance) {
        super(id, createdAt, updatedAt, username, hashedPassword, email,
                fullName, Role.BIDDER, status);
        this.balance = validateBalance(balance);
    }

    // ============== DOMAIN METHODS ==============

    /** Cộng tiền (nạp tiền, hoàn lại sau khi thua đấu giá...) */
    public void credit(BigDecimal amount) {
        validatePositiveAmount(amount);
        this.balance = this.balance.add(amount);
        markUpdated();
    }

    /** Trừ tiền (đặt cọc bid, thanh toán...) */
    public void debit(BigDecimal amount) {
        validatePositiveAmount(amount);
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Số dư không đủ. Hiện có: " + balance + ", cần: " + amount);
        }
        this.balance = this.balance.subtract(amount);
        markUpdated();
    }

    /** Kiểm tra có đủ tiền không (không trừ) - tiện cho UI hoặc check trước bid */
    public boolean hasEnoughBalance(BigDecimal amount) {
        validatePositiveAmount(amount);
        return this.balance.compareTo(amount) >= 0;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    // ============== PERMISSIONS (Template Method) ==============

    @Override public boolean canBid()           { return isActive(); }
    @Override public boolean canSell()          { return false; }
    @Override public boolean canManageSystem()  { return false; }

    // ============== VALIDATION ==============

    private static BigDecimal validateBalance(BigDecimal balance) {
        Objects.requireNonNull(balance, "balance must not be null");
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance không thể âm");
        }
        return balance;
    }

    private static void validatePositiveAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount phải > 0");
        }
    }

    @Override
    public String toString() {
        return "Bidder{" +
                "id=" + getId() +
                ", username='" + getUsername() + '\'' +
                ", status=" + getUserStatus() +
                ", balance=" + balance +
                '}';
    }
}