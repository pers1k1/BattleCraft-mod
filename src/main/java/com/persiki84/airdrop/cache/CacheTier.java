package com.persiki84.airdrop.cache;

import java.util.Locale;

public enum CacheTier {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY,
    HIDDEN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return "airdrop.cache.tier." + id();
    }

    public boolean shown() {
        return this != HIDDEN;
    }

    public static CacheTier of(String id) {
        CacheTier known = parse(id);
        return known == null ? COMMON : known;
    }

    public static CacheTier parse(String id) {
        for (CacheTier tier : values()) {
            if (tier.id().equalsIgnoreCase(id)) return tier;
        }
        return null;
    }

    public static CacheTier byIndex(int index) {
        CacheTier[] all = values();
        return index >= 0 && index < all.length ? all[index] : COMMON;
    }
}
