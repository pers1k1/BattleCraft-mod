package com.persiki84.shared.client.ui;

public final class UiTheme {

    private UiTheme() {}

    public static final int BACKDROP = 0xFF0C0C10;

    public static final int PANEL = 0xD8121216;
    public static final int PANEL_RAISED = 0xE81E1E24;
    public static final int PANEL_DEEP = 0xE60A0A0C;

    public static final int GLASS_TOP = 0xB4282833;
    public static final int GLASS_BOTTOM = 0xC41B1B24;
    public static final int GLASS_TOP_LIT = 0xC03E3E4C;
    public static final int GLASS_BOTTOM_LIT = 0xCE2C2C38;
    public static final int GLASS_WELL_TOP = 0x9014141A;
    public static final int GLASS_WELL_BOTTOM = 0x7C1E1E26;

    public static final int STROKE = 0x1FFFFFFF;

    public static final int FILL = 0xFFDFE2F0;
    public static final int FILL_DIM = 0xFF9DA1B8;
    public static final int FILL_FAINT = 0xFF676B80;

    public static final int ALERT = 0xFFCE2A22;
    public static final int ALERT_DIM = 0xFF7A1D18;

    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int TEXT = 0xFFE7E9F4;
    public static final int TEXT_DIM = 0xFFBEC2D8;
    public static final int TEXT_FAINT = 0xFF9498AE;

    public static final float RADIUS_PANEL = 6.5f;
    public static final float RADIUS_CELL = 5.5f;

    public static int alpha(int color, float mod) {
        int a = (int) (((color >>> 24) & 0xFF) * clamp(mod));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int withAlpha(int color, float value) {
        int a = (int) (clamp(value) * 255.0f);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int mix(int from, int to, float ratio) {
        float t = clamp(ratio);
        int a = lerpChannel(from >>> 24, to >>> 24, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int muted(int color, float toward) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int gray = (int) (r * 0.299f + g * 0.587f + b * 0.114f);
        float t = clamp(toward);
        r = (int) (r + (gray - r) * t);
        g = (int) (g + (gray - g) * t);
        b = (int) (b + (gray - b) * t);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int lighten(int color, float amount) {
        float t = clamp(amount);
        int r = (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * t);
        int g = (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * t);
        int b = (int) ((color & 0xFF) + (255 - (color & 0xFF)) * t);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return (int) (from + (to - from) * t) & 0xFF;
    }

    private static float clamp(float value) {
        if (value < 0.0f) return 0.0f;
        return Math.min(value, 1.0f);
    }
}
