package com.persiki84.shared.client.ui;

public final class UiFrame {
    private static final float MAX_DELTA = 0.1f;

    private static long lastNanos;
    private static long frame;
    private static float delta;

    private UiFrame() {}

    public static void advance() {
        frame++;
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
            delta = 0.0f;
            return;
        }
        delta = Math.min((now - lastNanos) / 1_000_000_000.0f, MAX_DELTA);
        lastNanos = now;
    }

    public static long frame() {
        return frame;
    }

    public static float delta() {
        return delta;
    }
}
