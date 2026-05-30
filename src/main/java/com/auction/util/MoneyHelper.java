package com.auction.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class MoneyHelper {

    private static final Pattern WHOLE_AMOUNT_INPUT =
            Pattern.compile("^\\d+$|^\\d{1,3}(?:[.,\\s_]\\d{3})+$");

    private MoneyHelper() {  }

    public static BigDecimal parseWholeAmountInput(String raw, String fieldLabel) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldLabel + " không được để trống");
        }

        String trimmed = raw.trim();
        if (!WHOLE_AMOUNT_INPUT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(fieldLabel + " không hợp lệ");
        }

        String normalized = trimmed.replaceAll("[.,\\s_]", "");
        return new BigDecimal(normalized);
    }

    public static BigDecimal parseRequestAmount(Object rawValue, String fieldName) {
        if (rawValue == null) {
            throw new IllegalArgumentException("Thiếu trường '" + fieldName + "'");
        }

        if (rawValue instanceof BigDecimal bd) {
            return bd;
        }
        if (rawValue instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (rawValue instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Thiếu trường '" + fieldName + "'");
            }
            try {
                return new BigDecimal(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Trường '" + fieldName + "' phải là số tiền hợp lệ: " + rawValue);
            }
        }

        throw new IllegalArgumentException(
                "Trường '" + fieldName + "' phải là số tiền hợp lệ: " + rawValue);
    }
}
