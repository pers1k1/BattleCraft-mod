package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiOklab;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWash;
import net.minecraft.util.FastColor;

public final class IslandTone {
    private static final int COLUMNS = MediaWatch.BANDS;
    private static final int ROWS = 7;
    private static final int GRID = 32;
    private static final int CLEAR_ALPHA = 32;
    private static final float BLUR_SHARE = 0.154f;
    private static final float SPAN_ACROSS = 0.738f;
    private static final float SPAN_DOWN = 0.938f;
    private static final float LIGHT_SCALE = 0.807f;
    private static final float LIGHT_BASE = 0.091f;
    private static final float TOP_TARGET = 0.577f;
    private static final float TOP_GAIN = 1.459f;
    private static final float CHROMA_GAIN = 1.286f;
    private static final float WASH_SECONDS = 0.43f;

    private record Cells(int[] base, float[] light, float[] chroma) {}

    private static final int[] ramp = new int[COLUMNS * ROWS];
    private static final UiWash[] washes = new UiWash[COLUMNS * ROWS];

    private static volatile Cells cells;

    private static Cells shownCells;
    private static float shownLight = -1.0f;
    private static float shownColor = -1.0f;

    static {
        for (int cell = 0; cell < washes.length; cell++) {
            washes[cell] = new UiWash(WASH_SECONDS);
        }
    }

    private IslandTone() {}

    public static void read(NativeImage image) {
        float[][] plane = shrunk(image);
        if (total(plane[3]) <= 0.0f) {
            cells = null;
            return;
        }

        float[][] soft = blurred(plane);
        cells = sampled(soft, topLight(soft));
    }

    public static void advance(float delta) {
        restamp();
        boolean tinted = cells != null && IslandArt.ready() && HudConfig.islandCoverTint();
        for (int cell = 0; cell < washes.length; cell++) {
            washes[cell].aim(tinted ? ramp[cell] : UiAccent.color());
            washes[cell].advance(delta);
        }
    }

    public static int barAt(int column, float share) {
        int at = Math.max(0, Math.min(COLUMNS - 1, column)) * ROWS;
        float row = clamp(share) * (ROWS - 1);
        int low = Math.min(ROWS - 2, (int) row);
        return UiTheme.mix(washes[at + low].get(), washes[at + low + 1].get(), row - low);
    }

    public static void forget() {
        cells = null;
    }

    private static void restamp() {
        Cells fresh = cells;
        if (fresh == shownCells && HudConfig.visualizerLight() == shownLight
                && HudConfig.visualizerColor() == shownColor) {
            return;
        }

        shownCells = fresh;
        shownLight = HudConfig.visualizerLight();
        shownColor = HudConfig.visualizerColor();
        for (int cell = 0; cell < ramp.length; cell++) {
            ramp[cell] = fresh == null ? UiAccent.color() : shown(fresh, cell);
        }
    }

    private static int shown(Cells snapshot, int cell) {
        int placed = UiOklab.withLightness(snapshot.base()[cell], clamp(snapshot.light()[cell] * shownLight));
        return UiOklab.withChroma(placed, snapshot.chroma()[cell] * shownColor);
    }

    private static float[][] shrunk(NativeImage image) {
        float[][] plane = new float[4][GRID * GRID];
        int stride = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / (GRID * 4));
        for (int y = 0; y < image.getHeight(); y += stride) {
            int row = Math.min(GRID - 1, y * GRID / image.getHeight()) * GRID;
            for (int x = 0; x < image.getWidth(); x += stride) {
                int pixel = image.getPixelRGBA(x, y);
                if (FastColor.ABGR32.alpha(pixel) <= CLEAR_ALPHA) continue;

                int cell = row + Math.min(GRID - 1, x * GRID / image.getWidth());
                plane[0][cell] += FastColor.ABGR32.red(pixel);
                plane[1][cell] += FastColor.ABGR32.green(pixel);
                plane[2][cell] += FastColor.ABGR32.blue(pixel);
                plane[3][cell] += 1.0f;
            }
        }
        return plane;
    }

    private static float[][] blurred(float[][] plane) {
        float[] kernel = kernel(BLUR_SHARE * GRID);
        float[] weight = pass(pass(plane[3], kernel, true), kernel, false);
        float[][] soft = new float[3][];
        for (int channel = 0; channel < 3; channel++) {
            soft[channel] = pass(pass(plane[channel], kernel, true), kernel, false);
            for (int cell = 0; cell < weight.length; cell++) {
                soft[channel][cell] = weight[cell] > 0.0f ? soft[channel][cell] / weight[cell] : 0.0f;
            }
        }
        return soft;
    }

    private static float[] kernel(float sigma) {
        int reach = (int) Math.ceil(sigma * 3.0f);
        float[] kernel = new float[reach * 2 + 1];
        float sum = 0.0f;
        for (int tap = -reach; tap <= reach; tap++) {
            kernel[tap + reach] = (float) Math.exp(-(tap * tap) / (2.0f * sigma * sigma));
            sum += kernel[tap + reach];
        }
        for (int tap = 0; tap < kernel.length; tap++) {
            kernel[tap] /= sum;
        }
        return kernel;
    }

    private static float[] pass(float[] source, float[] kernel, boolean across) {
        int reach = kernel.length / 2;
        float[] out = new float[source.length];
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                float sum = 0.0f;
                for (int tap = -reach; tap <= reach; tap++) {
                    int from = across ? y * GRID + edge(x + tap) : edge(y + tap) * GRID + x;
                    sum += source[from] * kernel[tap + reach];
                }
                out[y * GRID + x] = sum;
            }
        }
        return out;
    }

    private static float topLight(float[][] soft) {
        float top = 0.0f;
        for (int cell = 0; cell < GRID * GRID; cell++) {
            top = Math.max(top, UiOklab.lightness(argb(soft, cell)));
        }
        return top;
    }

    private static Cells sampled(float[][] soft, float top) {
        float gain = top > 0.0f ? Math.max(1.0f, Math.min(TOP_GAIN, TOP_TARGET / top)) : 1.0f;
        int[] base = new int[COLUMNS * ROWS];
        float[] light = new float[COLUMNS * ROWS];
        float[] chroma = new float[COLUMNS * ROWS];
        for (int column = 0; column < COLUMNS; column++) {
            float across = 0.5f + (IslandGlyph.along(column) - 0.5f) * SPAN_ACROSS;
            for (int row = 0; row < ROWS; row++) {
                float down = 0.5f + (row / (float) (ROWS - 1) - 0.5f) * SPAN_DOWN;
                int cell = column * ROWS + row;
                base[cell] = pick(soft, across, down);
                light[cell] = LIGHT_SCALE * UiOklab.lightness(base[cell]) * gain + LIGHT_BASE;
                chroma[cell] = UiOklab.chroma(base[cell]) * CHROMA_GAIN;
            }
        }
        return new Cells(base, light, chroma);
    }

    private static int pick(float[][] soft, float across, float down) {
        float x = Math.max(0.0f, Math.min(GRID - 1.001f, across * GRID - 0.5f));
        float y = Math.max(0.0f, Math.min(GRID - 1.001f, down * GRID - 0.5f));
        int left = (int) x;
        int top = (int) y;
        int upper = UiTheme.mix(argb(soft, top * GRID + left), argb(soft, top * GRID + left + 1), x - left);
        int lower = UiTheme.mix(argb(soft, (top + 1) * GRID + left), argb(soft, (top + 1) * GRID + left + 1),
                x - left);
        return UiTheme.mix(upper, lower, y - top);
    }

    private static int argb(float[][] soft, int cell) {
        return 0xFF000000 | (channel(soft[0][cell]) << 16) | (channel(soft[1][cell]) << 8) | channel(soft[2][cell]);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private static int edge(int index) {
        return Math.max(0, Math.min(GRID - 1, index));
    }

    private static float total(float[] weight) {
        float sum = 0.0f;
        for (float value : weight) {
            sum += value;
        }
        return sum;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
