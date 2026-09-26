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
        RefillMode known = parse(id);
        return known == null ? MATCH : known;
    }

    public static RefillMode parse(String id) {
        for (RefillMode mode : values()) {
            if (mode.id().equalsIgnoreCase(id)) return mode;
        }
        return null;
    }
}
