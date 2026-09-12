package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class UiPulse {
    private static final int SHAPE_CAPACITY = 64;
    private static final int MAX_POINTS = 192;
    private static final float MIN_STEP = 0.35f;

    private static final float P_CENTER = 0.145f;
    private static final float P_HALF_WIDTH = 0.055f;
    private static final float P_LEVEL = 0.160f;
    private static final int P_STEPS = 10;

    private static final float T_CENTER = 0.580f;
    private static final float T_HALF_WIDTH = 0.115f;
    private static final float T_LEVEL = 0.250f;
    private static final int T_STEPS = 12;

    private static final float PQ_PHASE = 0.235f;
    private static final float Q_PHASE = 0.255f;
    private static final float Q_LEVEL = -0.130f;
    private static final float R_PHASE = 0.315f;
    private static final float R_LEVEL = 1.000f;
    private static final float R_HALF_WIDTH = 0.013f;
    private static final float R_ROUNDING = 0.055f;
    private static final int R_STEPS = 4;
    private static final float S_PHASE = 0.375f;
    private static final float S_LEVEL = -0.285f;
    private static final float ST_PHASE = 0.415f;
    private static final float REST_PHASE = 0.860f;

    private static final float SPIKE_WIDTH = 0.055f;
    private static final float BASELINE = 0.66f;
    private static final float AMPLITUDE = 0.60f;

    private static final float[] SHAPE_PHASE = new float[SHAPE_CAPACITY];
    private static final float[] SHAPE_LEVEL = new float[SHAPE_CAPACITY];
    private static int shapeCount;

    private static final float[] POINT_X = new float[MAX_POINTS];
    private static final float[] POINT_Y = new float[MAX_POINTS];
    private static final float[] POINT_WEIGHT = new float[MAX_POINTS];

    static {
        keyframe(0.0f, 0.0f);
        hump(P_CENTER, P_HALF_WIDTH, P_LEVEL, P_STEPS);
        keyframe(PQ_PHASE, 0.0f);
        keyframe(Q_PHASE, Q_LEVEL);
        apex(R_PHASE, R_HALF_WIDTH, R_LEVEL, R_ROUNDING, R_STEPS);
        keyframe(S_PHASE, S_LEVEL);
        keyframe(ST_PHASE, 0.0f);
        hump(T_CENTER, T_HALF_WIDTH, T_LEVEL, T_STEPS);
        keyframe(REST_PHASE, 0.0f);
    }

    private UiPulse() {}

    public static void render(GuiGraphics graphics, float x, float y, float width, float height,
                              float phase, float cycles, float vigor, float thickness, int color, float alpha) {
        if (width <= 0.0f || height <= 0.0f || cycles <= 0.0f || alpha <= 0.004f) return;

        int count = plot(x, y, width, height, phase, cycles, UiAnim.clamp01(vigor), alpha);
        if (count < 2) return;

        UiRender.trace(graphics, POINT_X, POINT_Y, POINT_WEIGHT, count, thickness, color);
    }

    public static float spike(float phase) {
        float distance = Math.abs(fraction(phase) - R_PHASE) / SPIKE_WIDTH;
        return (float) Math.exp(-distance * distance);
    }

    public static float untilSpike(float phase) {
        return 1.0f - fraction(phase - R_PHASE);
    }

    public static long spikeIndex(float phase) {
        return (long) Math.floor(phase - R_PHASE) + 1L;
    }

    public static float rate(float ratio, float exertion, float calm, float racing, float winded) {
        float strain = 1.0f - UiAnim.clamp01(ratio);
        float wound = strain * strain;
        float fatigue = UiAnim.clamp01(exertion);
        return calm + Math.max((racing - calm) * wound, (winded - calm) * fatigue * fatigue);
    }

    private static int plot(float x, float y, float width, float height, float phase, float cycles,
                            float vigor, float alpha) {
        float left = phase - cycles;
        float baseline = y + height * BASELINE;
        float span = height * AMPLITUDE * vigor;
        int count = mark(0, x, baseline - span * level(left), alpha);

        for (int cycle = (int) Math.floor(left); cycle <= (int) Math.ceil(phase); cycle++) {
            for (int index = 0; index < shapeCount && count < MAX_POINTS - 1; index++) {
                float moment = cycle + SHAPE_PHASE[index];
                if (moment <= left || moment >= phase) continue;

                float travelled = (moment - left) / cycles;
                count = mark(count, x + width * travelled, baseline - span * SHAPE_LEVEL[index], alpha);
            }
        }
        return mark(count, x + width, baseline - span * level(phase), alpha);
    }

    private static int mark(int count, float pointX, float pointY, float weight) {
        if (count >= MAX_POINTS) return count;
        if (count > 0 && Math.abs(pointX - POINT_X[count - 1]) < MIN_STEP
                && Math.abs(pointY - POINT_Y[count - 1]) < MIN_STEP) {
            return count;
        }
        POINT_X[count] = pointX;
        POINT_Y[count] = pointY;
        POINT_WEIGHT[count] = weight;
        return count + 1;
    }

    private static float level(float moment) {
        float at = fraction(moment);
        int last = shapeCount - 1;
        if (at >= SHAPE_PHASE[last]) return closing(at, last);

        int index = 0;
        while (index + 1 < shapeCount && SHAPE_PHASE[index + 1] <= at) index++;
        float reach = SHAPE_PHASE[index + 1] - SHAPE_PHASE[index];
        return blend(SHAPE_LEVEL[index], SHAPE_LEVEL[index + 1], (at - SHAPE_PHASE[index]) / reach);
    }

    private static float closing(float at, int last) {
        float reach = 1.0f - SHAPE_PHASE[last] + SHAPE_PHASE[0];
        return blend(SHAPE_LEVEL[last], SHAPE_LEVEL[0], (at - SHAPE_PHASE[last]) / reach);
    }

    private static float blend(float from, float to, float travelled) {
        return from + (to - from) * UiAnim.clamp01(travelled);
    }

    private static void keyframe(float phase, float level) {
        if (shapeCount >= SHAPE_CAPACITY) return;
        SHAPE_PHASE[shapeCount] = phase;
        SHAPE_LEVEL[shapeCount] = level;
        shapeCount++;
    }

    private static void apex(float center, float halfWidth, float level, float rounding, int steps) {
        for (int step = 0; step <= steps; step++) {
            float offset = step * 2.0f / steps - 1.0f;
            keyframe(center + halfWidth * offset, level - rounding * offset * offset);
        }
    }

    private static void hump(float center, float halfWidth, float level, int steps) {
        for (int step = 0; step <= steps; step++) {
            float travelled = step / (float) steps;
            float wave = 0.5f - (float) Math.cos(travelled * Math.PI * 2.0) * 0.5f;
            keyframe(center - halfWidth + halfWidth * 2.0f * travelled, level * wave);
        }
    }

    private static float fraction(float value) {
        return value - (float) Math.floor(value);
    }
}
