package com.persiki84.shared.client.ui;

public final class UiMetrics {
    public static final float PHI = 1.618034f;

    public static final float GAP_TIGHT = 2.5f;
    public static final float GAP = 4.0f;
    public static final float GAP_WIDE = 6.5f;
    public static final float PAD = 6.5f;
    public static final float PAD_WIDE = 10.5f;
    public static final float MARGIN = 10.5f;
    public static final float MARGIN_WIDE = 17.0f;

    public static final float BAR_HEIGHT = 3.0f;
    public static final float ROW_HEIGHT = 10.5f;
    public static final float CARD_HEIGHT = 27.5f;

    private static final float GRID = 0.5f;
    private static final float RADIUS_MIN = 3.0f;

    private UiMetrics() {}

    public static float snap(float value) {
        return Math.round(value / GRID) * GRID;
    }

    public static float radius(float height) {
        float half = height / 2.0f;
        return Math.min(half, Math.max(RADIUS_MIN, snap(half / PHI + 1.0f)));
    }

    public static float cardHeight(float content) {
        return snap(content + PAD * 2.0f);
    }

    public static float cardWidth(float content) {
        return snap(content + PAD_WIDE * 2.0f);
    }
}
