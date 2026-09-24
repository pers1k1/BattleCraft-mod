package com.persiki84.airdrop.cache;

import java.util.Locale;

public enum RefillMode {
    MATCH,
    TIMER,
    LOOTED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return "airdrop.cache.refill." + id();
    }

    public boolean timed() {
        return this != MATCH;
    }

    public static RefillMode of(String id) {
        for (RefillMode mode : values()) {
            if (mode.id().equals(id)) return mode;
        }
        return MATCH;
    }
}
