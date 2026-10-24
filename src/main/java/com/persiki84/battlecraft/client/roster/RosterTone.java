package com.persiki84.battlecraft.client.roster;

import com.persiki84.battlecraft.client.hud.HudInk;

// WHY: те же тона, что у пинга острова: норма пишется обычными чернилами, просадка янтарём,
// WHY: беда красным; цвет несёт смысл, поэтому он не берётся из акцента темы
final class RosterTone {
    static final int WARN = 0xFFE0A33C;
    static final int BAD = 0xFFD9503F;

    private static final float RATE_FINE = 19.5f;
    private static final float RATE_SLOW = 15.0f;
    private static final float SPENT_FINE = 40.0f;
    private static final float SPENT_SLOW = 50.0f;
    private static final int LATENCY_FINE = 150;
    private static final int LATENCY_SLOW = 300;

    private RosterTone() {}

    public static int rate(float ticksPerSecond) {
        if (ticksPerSecond >= RATE_FINE) return HudInk.text();
        return ticksPerSecond >= RATE_SLOW ? WARN : BAD;
    }

    public static int spent(float millisPerTick) {
        if (millisPerTick <= SPENT_FINE) return HudInk.text();
        return millisPerTick <= SPENT_SLOW ? WARN : BAD;
    }

    public static int latency(int millis) {
        if (millis < LATENCY_FINE) return HudInk.text();
        return millis < LATENCY_SLOW ? WARN : BAD;
    }
}
