package com.persiki84.battlecraft.rules;

import com.persiki84.shared.ValueStore;

public final class MarkerRanges {
    private static final ValueStore<MarkerRange> STORE = new ValueStore<>("battlecraft-markers.json",
            MarkerRange.values(), MarkerRange::id, range -> MarkerRange.DEFAULT_BLOCKS,
            MarkerRange.MIN_BLOCKS, MarkerRange.MAX_BLOCKS);

    private MarkerRanges() {}

    public static int blocks(MarkerRange range) {
        return STORE.of(range);
    }

    public static void set(MarkerRange range, int blocks) {
        STORE.set(range, blocks);
    }

    public static void reset() {
        STORE.reset();
    }

    public static void load() {
        STORE.load();
    }
}
