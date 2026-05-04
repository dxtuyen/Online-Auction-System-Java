package com.auction.model.entity.profile;

import com.auction.model.enums.Role;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

public interface RoleProfile extends Serializable {

    Role getRole();

    /**
     * Helper validate dùng chung cho BidderProfile (balance) và SellerProfile (revenue).
     * Đặt ở interface để hai profile khỏi copy-paste validate riêng.
     */
    static BigDecimal requireNonNegative(BigDecimal v, String fieldName) {
        Objects.requireNonNull(v, fieldName + " không thể null");
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " không thể âm");
        }
        return v;
    }

    /**
     * Validate cho amount cộng/trừ - phải > 0 (không cho phép 0)
     */
    static void requirePositive(BigDecimal v, String fieldName) {
        Objects.requireNonNull(v, fieldName + " không thể null");
        if (v.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " phải > 0");
        }
    }
}
