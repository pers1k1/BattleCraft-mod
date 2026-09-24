package com.persiki84.airdrop.client;

import com.persiki84.airdrop.cache.CacheTier;

public final class CacheTint {
    private static final int COMMON = 0xFFBFD0E6;
    private static final int RARE = 0xFF58A6FF;
    private static final int EPIC = 0xFFB57BFF;
    private static final int LEGENDARY = 0xFFFFB84D;

    private CacheTint() {}

    public static int color(CacheTier tier) {
        return switch (tier) {
            case RARE -> RARE;
            case EPIC -> EPIC;
            case LEGENDARY -> LEGENDARY;
            default -> COMMON;
        };
    }
}
