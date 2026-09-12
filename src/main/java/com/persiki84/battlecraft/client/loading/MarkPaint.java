package com.persiki84.battlecraft.client.loading;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

public final class MarkPaint {
    private static final float FRONT_WIDTH = 0.34f;
    private static final float JITTER = 0.16f;
    private static final float DIAGONAL_TILT = 0.55f;
    private static final int FALL_CELLS = 4;
    private static final float BREATH_PERIOD = 3.4f;
    private static final float BREATH_CELLS = 1.0f;
    private static final float SWEEP_PERIOD = 5.6f;
    private static final float SWEEP_BAND = 0.16f;
    private static final float SWEEP_LIGHT = 0.55f;
    private static final float SWEEP_TILT = 0.42f;
    private static final float GONE = 0.02f;
    private static final int HASH_MIX = 0x27D4EB2D;
    private static final float HASH_SPAN = 1.0f / 65536.0f;

    private static final Batch BATCH = new Batch();

    private static float[] order;

    private MarkPaint() {}

    public static int width(int scale) {
        return LoadingMark.ready() ? LoadingMark.width() * scale : 0;
    }

    public static int height(int scale) {
        return LoadingMark.ready() ? LoadingMark.height() * scale : 0;
    }

    public static void paint(GuiGraphics graphics, int left, int top, int scale,
                             float assembled, float seconds) {
        if (!LoadingMark.ready() || scale <= 0) return;

        bake();
        BATCH.left = left;
        BATCH.top = top + Math.round(breath(seconds)) * scale;
        BATCH.scale = scale;
        BATCH.assembled = UiAnim.clamp01(assembled);
        BATCH.sweep = sweepHead(seconds);
        graphics.drawManaged(BATCH.on(graphics));
    }

    private static float breath(float seconds) {
        return (float) Math.sin(seconds / BREATH_PERIOD * Math.PI * 2.0) * BREATH_CELLS;
    }

    // WHY: блик уходит за край и ждёт там: без паузы полоса света идёт по голове без остановки
    // WHY: и читается как мигание, а не как редкий проблеск
    private static float sweepHead(float seconds) {
        float phase = (seconds % SWEEP_PERIOD) / SWEEP_PERIOD;
        return phase * (2.0f + SWEEP_BAND * 4.0f) - SWEEP_BAND * 2.0f;
    }

    private static synchronized void bake() {
        if (order != null) return;

        int width = LoadingMark.width();
        int height = LoadingMark.height();
        float reach = (width - 1) + (height - 1) * DIAGONAL_TILT;
        order = new float[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float along = (x + y * DIAGONAL_TILT) / reach;
                order[y * width + x] = UiAnim.clamp01(along + noise(x, y) * JITTER);
            }
        }
    }

    private static float noise(int x, int y) {
        int hash = (x * 73856093) ^ (y * 19349663);
        hash = (hash ^ (hash >>> 13)) * HASH_MIX;
        return ((hash >>> 16) & 0xFFFF) * HASH_SPAN - 0.5f;
    }

    private static final class Batch implements Runnable {
        private GuiGraphics graphics;
        private int left;
        private int top;
        private int scale;
        private float assembled;
        private float sweep;

        private Runnable on(GuiGraphics target) {
            graphics = target;
            return this;
        }

        @Override
        public void run() {
            int width = LoadingMark.width();
            int height = LoadingMark.height();
            float front = assembled * (1.0f + FRONT_WIDTH);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (LoadingMark.opaque(x, y)) cell(x, y, width, front);
                }
            }
        }

        private void cell(int x, int y, int width, float front) {
            float settled = UiAnim.easeOut((front - order[y * width + x]) / FRONT_WIDTH);
            if (settled <= GONE) return;

            int drop = Math.round((1.0f - settled) * FALL_CELLS);
            int cellX = left + x * scale;
            int cellY = top + (y - drop) * scale;
            graphics.fill(cellX, cellY, cellX + scale, cellY + scale, tint(x, y, settled));
        }

        private int tint(int x, int y, float settled) {
            int color = UiTheme.alpha(LoadingMark.pixel(x, y), settled);
            float glint = shine(x, y);
            return glint <= 0.0f ? color : UiTheme.lighten(color, glint * SWEEP_LIGHT);
        }

        private float shine(int x, int y) {
            float reach = (LoadingMark.width() - 1) + (LoadingMark.height() - 1) * SWEEP_TILT;
            float along = (x + (LoadingMark.height() - 1 - y) * SWEEP_TILT) / reach;
            float away = Math.abs(along - sweep) / SWEEP_BAND;
            return away >= 1.0f ? 0.0f : (1.0f - away) * (1.0f - away);
        }
    }
}
