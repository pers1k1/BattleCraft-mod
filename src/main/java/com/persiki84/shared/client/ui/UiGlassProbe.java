package com.persiki84.shared.client.ui;

public final class UiGlassProbe {
    public static final int MODES = 5;

    private static int mode;

    private UiGlassProbe() {}

    public static void mode(int value) {
        mode = Math.max(0, Math.min(MODES, value));
    }

    public static int mode() {
        return mode;
    }
}
