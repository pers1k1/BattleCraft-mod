package com.persiki84.shared.client.menu;

final class Pending {
    private static final long LIFE_MS = 1500L;
    private static final int NONE = Integer.MIN_VALUE;

    private int wanted = NONE;
    private long wantedAt;

    void want(int value) {
        wanted = value;
        wantedAt = System.currentTimeMillis();
    }

    void take(Pending older) {
        wanted = older.wanted;
        wantedAt = older.wantedAt;
    }

    int resolve(int actual) {
        if (wanted == NONE) return actual;
        if (wanted == actual || System.currentTimeMillis() - wantedAt > LIFE_MS) {
            wanted = NONE;
            return actual;
        }
        return wanted;
    }
}
