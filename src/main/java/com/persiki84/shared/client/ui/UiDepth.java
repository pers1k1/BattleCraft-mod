package com.persiki84.shared.client.ui;

public final class UiDepth {
    public static final float HUSH = 0.05f;

    private static float hush = HUSH;

    private UiDepth() {}

    public static void reset() {
        hush = HUSH;
    }

    public static void hush(float value) {
        hush = value;
    }

    public static float hush() {
        return hush;
    }
}
