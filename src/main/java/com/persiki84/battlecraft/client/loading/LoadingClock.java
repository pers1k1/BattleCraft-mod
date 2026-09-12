package com.persiki84.battlecraft.client.loading;

// WHY: своё время и своя защёлка кадра, без UiFrame: экран запуска идёт раньше, чем заводится
// WHY: кадровый конвейер мода, и на его отсчёте метка стояла бы неподвижно всю загрузку модов
public final class LoadingClock {
    private static final float MAX_STEP = 0.1f;

    private static float seconds;
    private static long lastNanos;
    private static Object owner;
    private static float bornAt;

    private LoadingClock() {}

    public static void advance() {
        long now = System.nanoTime();
        if (lastNanos != 0L) {
            seconds += Math.min((now - lastNanos) / 1_000_000_000.0f, MAX_STEP);
        }
        lastNanos = now;
    }

    public static float seconds() {
        return seconds;
    }

    // WHY: возраст берётся от смены владельца, а не от начала отсчёта: каждый экран загрузки
    // WHY: должен собирать метку заново, а не показывать её уже собранной с прошлого раза
    public static float age(Object claiming) {
        if (claiming != owner) {
            owner = claiming;
            bornAt = seconds;
        }
        return seconds - bornAt;
    }
}
