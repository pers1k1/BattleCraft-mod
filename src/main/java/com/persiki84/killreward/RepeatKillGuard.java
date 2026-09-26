package com.persiki84.killreward;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// WHY: два сговорившихся игрока иначе печатали награду бесконечно: один убивает другого сразу
// WHY: после каждого возрождения. Пара убийца-жертва оплачивается не чаще раза в окно
public final class RepeatKillGuard {
    public static final int WINDOW_SECONDS = 90;
    private static final long WINDOW_MS = WINDOW_SECONDS * 1000L;

    private static final Map<Pair, Long> paidAt = new HashMap<>();

    private RepeatKillGuard() {}

    public static long secondsLeft(UUID killer, UUID victim) {
        Long last = paidAt.get(new Pair(killer, victim));
        if (last == null) return 0L;

        long left = WINDOW_MS - (System.currentTimeMillis() - last);
        return left <= 0L ? 0L : (left + 999L) / 1000L;
    }

    public static void remember(UUID killer, UUID victim) {
        long now = System.currentTimeMillis();
        paidAt.values().removeIf(last -> now - last >= WINDOW_MS);
        paidAt.put(new Pair(killer, victim), now);
    }

    public static void clear() {
        paidAt.clear();
    }

    private record Pair(UUID killer, UUID victim) {}
}
