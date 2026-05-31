package com.auction.client.util;

import javafx.scene.control.TextField;

/**
 * Gắn formatter "thousand separator" vào ô nhập tiền: cứ 3 chữ số chèn dấu phẩy.
 *
 * <p>User gõ <code>1000000</code> → hiển thị <code>1,000,000</code>. Backend parser
 * ({@link com.auction.util.MoneyHelper#parseWholeAmountInput}) và các parseMoney
 * cục bộ ở controller đều strip dấu phẩy nên không cần đổi protocol.</p>
 *
 * <p>Caret được giữ theo "số chữ số trước caret" để khi dấu phẩy được chèn vào,
 * con trỏ không nhảy lung tung.</p>
 */
public final class MoneyInputFormatter {

    private static final int MAX_DIGITS = 15;

    private MoneyInputFormatter() {}

    public static void apply(TextField... fields) {
        for (TextField f : fields) {
            if (f != null) bind(f);
        }
    }

    private static void bind(TextField field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            int caretPos = field.getCaretPosition();
            int digitsBeforeCaret = countDigits(newVal, Math.min(caretPos, newVal.length()));

            String digits = newVal.replaceAll("[^0-9]", "");
            if (digits.length() > MAX_DIGITS) digits = digits.substring(0, MAX_DIGITS);

            String formatted = formatWithCommas(digits);
            if (formatted.equals(newVal)) return;

            field.setText(formatted);
            field.positionCaret(positionAfterDigits(formatted, digitsBeforeCaret));
        });
    }

    private static int countDigits(String s, int upTo) {
        int n = 0;
        for (int i = 0; i < upTo; i++) {
            if (Character.isDigit(s.charAt(i))) n++;
        }
        return n;
    }

    private static int positionAfterDigits(String formatted, int digitCount) {
        if (digitCount <= 0) return 0;
        int seen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (Character.isDigit(formatted.charAt(i))) {
                seen++;
                if (seen == digitCount) return i + 1;
            }
        }
        return formatted.length();
    }

    private static String formatWithCommas(String digits) {
        if (digits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3);
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            if (count > 0 && count % 3 == 0) sb.append(',');
            sb.append(digits.charAt(i));
            count++;
        }
        return sb.reverse().toString();
    }
}
