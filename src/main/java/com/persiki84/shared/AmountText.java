package com.persiki84.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

// WHY: String.format берёт разделитель дробной части из локали клиента, и на русской раскладке
// WHY: величина модификатора уезжала в запятую, которую ни команда, ни конфиг не читают
public final class AmountText {
    private static final int DECIMALS = 3;

    private AmountText() {}

    public static String of(double value) {
        return BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static String percent(double multiplier) {
        return of(multiplier * 100.0) + "%";
    }

    public static String signed(double value, boolean percent) {
        String shown = percent ? percent(value) : of(value);
        return value > 0.0 ? "+" + shown : shown;
    }
}
