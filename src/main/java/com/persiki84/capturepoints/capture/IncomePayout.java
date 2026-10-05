package com.persiki84.capturepoints.capture;

import java.util.Locale;

// WHY: доход точки раньше получал каждый игрок команды в сети целиком, и команда из пяти получала
// WHY: впятеро больше. EACH оставлен как было по выбору владельца, SHARE делит сумму на всех, TREASURY
// WHY: кладёт её один раз в казну команды
public enum IncomePayout {
    EACH,
    SHARE,
    TREASURY;

    public static final IncomePayout DEFAULT = TREASURY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "capturepoints.payout." + id();
    }

    public static IncomePayout byId(String id) {
        if (id == null || id.isEmpty()) return DEFAULT;

        for (IncomePayout payout : values()) {
            if (payout.id().equalsIgnoreCase(id)) return payout;
        }
        return DEFAULT;
    }
}
