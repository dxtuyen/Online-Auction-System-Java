package com.auction.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyHelperTest {

    @Test
    @DisplayName("UI amount cho phép dấu phân tách hàng nghìn")
    void parseWholeAmountInput_groupedValue_success() {
        BigDecimal amount = MoneyHelper.parseWholeAmountInput("1.250.000", "Giá đấu");

        assertEquals(new BigDecimal("1250000"), amount);
    }

    @Test
    @DisplayName("UI amount reject định dạng thập phân")
    void parseWholeAmountInput_decimalValue_rejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MoneyHelper.parseWholeAmountInput("1000.50", "Giá đấu"));

        assertEquals("Giá đấu không hợp lệ", ex.getMessage());
    }

    @Test
    @DisplayName("Request amount parse exact String payload")
    void parseRequestAmount_stringValue_success() {
        BigDecimal amount = MoneyHelper.parseRequestAmount("100000", "amount");

        assertEquals(new BigDecimal("100000"), amount);
    }
}
