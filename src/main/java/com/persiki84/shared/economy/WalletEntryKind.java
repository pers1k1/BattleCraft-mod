package com.persiki84.shared.economy;

import java.util.Locale;

public enum WalletEntryKind {
    BUY,
    SELL,
    KILL,
    DEATH,
    PLUNDER,
    INCOME,
    CAPTURE,
    TREASURY,
    SENT,
    RECEIVED,
    ADMIN,
    RESET;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "sellmod.money.log." + id();
    }

    public static WalletEntryKind byId(String id) {
        if (id == null) return null;

        for (WalletEntryKind kind : values()) {
            if (kind.id().equalsIgnoreCase(id)) return kind;
        }
        return null;
    }
}
