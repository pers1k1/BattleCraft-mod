package com.persiki84.zones.shop;

import com.persiki84.battlecraft.BattleCraftManager;

// WHY: окно продажи товара считается от старта матча: «откроется через» и «закроется через». Вне
// WHY: матча товар с таймером закрыт, иначе его скупали бы в лобби до старта. Без ядра матча
// WHY: (оно выключено) часов нет вовсе, и таймеры не действуют, как остальные правила матча
public final class ShopSchedule {
    public enum State { OPEN, WAITING, CLOSED }

    public static final long OUTSIDE_MATCH = -1L;

    private ShopSchedule() {}

    public static State server(ShopEntry entry) {
        BattleCraftManager match = BattleCraftManager.getInstance();
        return state(entry, match.matchElapsedMillis(), match.isSoftDisabled());
    }

    public static State state(ShopEntry entry, long elapsedMs, boolean matchless) {
        if (!entry.timed() || matchless) return State.OPEN;
        if (elapsedMs < 0L || elapsedMs < entry.opensAfter() * 1000L) return State.WAITING;
        if (entry.closesAfter() > 0 && elapsedMs >= entry.closesAfter() * 1000L) return State.CLOSED;
        return State.OPEN;
    }

    public static int secondsToOpen(ShopEntry entry, long elapsedMs) {
        if (elapsedMs < 0L) return entry.opensAfter();
        return (int) Math.max(0L, (entry.opensAfter() * 1000L - elapsedMs + 999L) / 1000L);
    }

    public static int secondsToClose(ShopEntry entry, long elapsedMs) {
        if (entry.closesAfter() <= 0 || elapsedMs < 0L) return -1;
        return (int) Math.max(0L, (entry.closesAfter() * 1000L - elapsedMs + 999L) / 1000L);
    }

    public static String clock(int seconds) {
        int hours = seconds / 3600;
        int minutes = seconds / 60 % 60;
        int rest = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, rest) : String.format("%d:%02d", minutes, rest);
    }
}
