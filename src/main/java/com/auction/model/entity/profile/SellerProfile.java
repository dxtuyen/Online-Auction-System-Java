package com.auction.model.entity.profile;

import com.auction.model.enums.Role;

import java.math.BigDecimal;

public class SellerProfile implements RoleProfile {

    private static final long serialVersionUID = 1L;

    private volatile BigDecimal totalRevenue;

    /**
     * TAO SELLERPROFILE, BALANCE = 0
     */
    public SellerProfile() {
        this(BigDecimal.ZERO);
    }

    /**
     * Load từ DB hoặc tạo có sẵn doanh thu
     */
    public SellerProfile(BigDecimal initialRevenue) {
        totalRevenue = RoleProfile.requireNonNegative(initialRevenue, "totalRevenue");
    }

    @Override
    public Role getRole() {
        return Role.SELLER;
    }

    /**
     * GET REVENUE
     */
    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    /**
     * CONG DOANH THU KHI KET THUC PHIEN DAU GIA
     */
    public synchronized void addRevenue(BigDecimal amount) {
        RoleProfile.requireNonNegative(amount, "revenue amount");
        totalRevenue = totalRevenue.add(amount);
    }

    @Override
    public String toString() {
        return "SellerProfile{totalRevenue=" + totalRevenue + "}";
    }
}
