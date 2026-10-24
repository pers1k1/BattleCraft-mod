package com.persiki84.battlecraft.client.roster;

import net.minecraft.Util;

public final class ServerPulseClient {
    private static final long NEVER = 0L;
    // WHY: сервер шлёт замер раз в секунду; пять секунд тишины значат, что он завис или замер
    // WHY: не доходит, и последнее число показывать уже нельзя
    private static final long STALE_MS = 5000L;
    private static final float FULL_RATE = 20.0f;
    private static final float SPENT_LIMIT = 60000.0f;

    private static float rate;
    private static float spent;
    private static long receivedAt = NEVER;

    private ServerPulseClient() {}

    public static void accept(float ticksPerSecond, float millisPerTick) {
        if (!Float.isFinite(ticksPerSecond) || !Float.isFinite(millisPerTick)) return;

        rate = Math.max(0.0f, Math.min(FULL_RATE, ticksPerSecond));
        spent = Math.max(0.0f, Math.min(SPENT_LIMIT, millisPerTick));
        receivedAt = Util.getMillis();
    }

    public static void forget() {
        rate = 0.0f;
        spent = 0.0f;
        receivedAt = NEVER;
    }

    public static boolean known() {
        return receivedAt != NEVER && Util.getMillis() - receivedAt < STALE_MS;
    }

    public static float rate() {
        return rate;
    }

    public static float spent() {
        return spent;
    }
}
