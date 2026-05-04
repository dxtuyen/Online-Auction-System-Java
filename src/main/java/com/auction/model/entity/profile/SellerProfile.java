package com.auction.model.entity.profile;

import com.auction.model.enums.Role;

import java.math.BigDecimal;
import java.util.Objects;
public class SellerProfile implements RoleProfile {

    private static final long serialVersionUID= 1L;

    private BigDecimal totalRevenue;

    /** VALIDATE */
    private static BigDecimal validateNonNegative(BigDecimal v) {
        Objects.requireNonNull(v, "So du khong the la null");
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("So du khong the am");
        }
        return v;
    }

    @Override
    public Role getRole() {
        return Role.SELLER;
    }

    /** TAO SELLERPROFILE, BALANCE = 0 */
    public SellerProfile() {
        this(BigDecimal.ZERO);
    }

    /** */
    public SellerProfile(BigDecimal initialRevenue) {
        totalRevenue = validateNonNegative(initialRevenue);
    }

    /** GET REVENUE */
    public synchronized BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    /** CONG DOANH THU KHI KET THUC PHIEN DAU GIA */
    public synchronized void addRevenue(BigDecimal amount) {
        Objects.requireNonNull(amount, "Doanh thu khong the null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("So du khong the am");
        }
        totalRevenue = totalRevenue.add(amount);
    }

    @Override
    public String toString() {
        return "SellerProfile{totalRevenue=" + totalRevenue + "}";
    }
}
