package com.persiki84.shared.client.ui;

// WHY: мастер первого запуска держит своё движение независимо от выбора игрока: набор там ещё
// WHY: не выбран, а вход завязан на каскад приветствия и на переход в мир
public final class UiMotion {
    public interface Fixed {
    }

    private static UiMotionSet chosen = UiMotionSet.IGNITE;

    private UiMotion() {}

    public static void set(UiMotionSet value) {
        chosen = value == null ? UiMotionSet.IGNITE : value;
    }

    public static UiMotionSet of(Object screen) {
        if (screen instanceof Fixed) return UiMotionSet.IGNITE;
        if (chosen == UiMotionSet.GLASS && !UiShatter.ready()) return UiMotionSet.IGNITE;
        return chosen;
    }
}
