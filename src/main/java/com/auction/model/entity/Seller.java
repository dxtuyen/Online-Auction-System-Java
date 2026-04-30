package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Người bán - có totalRevenue tích lũy qua các phiên đấu giá thành công.
 */
public class Seller extends User {

    private static final long serialVersionUID = 1L;

    private BigDecimal totalRevenue;

    // ============== CONSTRUCTORS ==============

    public Seller(String username, String hashedPassword, String email, String fullName) {
        this(username, hashedPassword, email, fullName, BigDecimal.ZERO);
    }

    public Seller(String username, String hashedPassword, String email,
                  String fullName, BigDecimal totalRevenue) {
        super(username, hashedPassword, email, fullName, Role.SELLER);
        this.totalRevenue = validateRevenue(totalRevenue);
    }

    public Seller(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                  String username, String hashedPassword, String email,
                  String fullName, UserStatus status, BigDecimal totalRevenue) {
        super(id, createdAt, updatedAt, username, hashedPassword, email,
                fullName, Role.SELLER, status);
        this.totalRevenue = validateRevenue(totalRevenue);
    }

    // ============== DOMAIN METHODS ==============

    /** Cộng doanh thu khi auction kết thúc thành công */
    public void addRevenue(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Revenue phải > 0");
        }
        this.totalRevenue = this.totalRevenue.add(amount);
        markUpdated();
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    // ============== PERMISSIONS ==============

    @Override public boolean canBid()           { return false; }
    @Override public boolean canSell()          { return isActive(); }
    @Override public boolean canManageSystem()  { return false; }

    // ============== VALIDATION ==============

    private static BigDecimal validateRevenue(BigDecimal revenue) {
        Objects.requireNonNull(revenue, "revenue must not be null");
        if (revenue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Revenue không thể âm");
        }
        return revenue;
    }

    @Override
    public String toString() {
        return "Seller{" +
                "id=" + getId() +
                ", username='" + getUsername() + '\'' +
                ", status=" + getUserStatus() +
                ", totalRevenue=" + totalRevenue +
                '}';
    }
}
