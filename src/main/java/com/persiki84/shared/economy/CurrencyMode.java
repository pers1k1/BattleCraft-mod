package com.persiki84.shared.economy;

import java.util.Locale;

public enum CurrencyMode {
    ITEM,
    ACCOUNT;

    public static final CurrencyMode DEFAULT = ITEM;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "sellmod.money.mode." + id();
    }

    public static CurrencyMode byId(String id) {
        if (id == null) return DEFAULT;

        for (CurrencyMode mode : values()) {
            if (mode.id().equalsIgnoreCase(id)) return mode;
        }
        return DEFAULT;
    }
}
