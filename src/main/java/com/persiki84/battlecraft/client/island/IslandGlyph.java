package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

public final class IslandGlyph {
    private static final int BARS = MediaWatch.BANDS;
    private static final float GAP_SHARE = 0.69f;
    private static final float REST_SHARE = 0.078f;
    private static final float TICK_SECONDS = 1.0f / 15.0f;
    private static final float GLIDE_SECONDS = 0.115f;
    private static final float[] BAND_DECIBELS = {-1.277f, -3.687f, 1.353f, -0.083f, -1.338f, -3.379f};
    private static final float LEVEL_POWER = 0.759f;
    private static final float LEVEL_FLOOR = 0.002f;

    private static final float[] start = new float[BARS];
    private static final float[] goal = new float[BARS];
    private static final float[] reach = new float[BARS];
    private static final float[] bandGain = new float[BARS];
    private static final int[] tints = new int[UiCrisp.STOPS];
    private static final float floorPower = (float) Math.pow(LEVEL_FLOOR, LEVEL_POWER);

    private static float since;
    private static long stamp = -1L;

    static {
        for (int index = 0; index < BARS; index++) {
            bandGain[index] = (float) Math.pow(10.0, BAND_DECIBELS[index] / 20.0);
        }
    }

    private IslandGlyph() {}

    static float along(int column) {
        return (column * (1.0f + GAP_SHARE) + 0.5f) / (BARS + GAP_SHARE * (BARS - 1));
    }

    public static void visualizer(GuiGraphics graphics, float centerX, float centerY, float width, float height,
                                  float fade) {
        float bar = width / (BARS + GAP_SHARE * (BARS - 1));
        float gap = bar * GAP_SHARE;
        float left = centerX - width / 2.0f;
        boolean sharp = UiCrisp.ready();
        if (sharp) graphics.flush();

        for (int index = 0; index < BARS; index++) {
            float tall = Math.max(bar, height * (REST_SHARE + (1.0f - REST_SHARE) * reach[index]));
            float spread = tall / (2.0f * height);
            float x = left + index * (bar + gap);
            float y = centerY - tall / 2.0f;
            if (sharp) {
                UiCrisp.panelRamp(graphics, x, y, bar, tall, bar / 2.0f, tinted(index, spread, fade));
                continue;
            }
            UiRender.panelShaded(graphics, x, y, bar, tall, bar / 2.0f,
                    UiTheme.alpha(IslandTone.barAt(index, 0.5f - spread), fade),
                    UiTheme.alpha(IslandTone.barAt(index, 0.5f + spread), fade));
        }
    }

    private static int[] tinted(int index, float spread, float fade) {
        for (int stop = 0; stop < tints.length; stop++) {
            float share = 0.5f - spread + 2.0f * spread * stop / (tints.length - 1);
            tints[stop] = UiTheme.alpha(IslandTone.barAt(index, share), fade);
        }
        return tints;
    }

    public static void pulse(float energy) {
        long frame = UiFrame.frame();
        if (frame == stamp) return;

        stamp = frame;
        since += UiFrame.delta() * HudConfig.visualizerSpeed();
        if (since >= TICK_SECONDS) {
            since %= TICK_SECONDS;
            retarget(energy);
        }
        glide();
    }

    private static void retarget(float energy) {
        float gain = HudConfig.visualizerGain();
        for (int index = 0; index < BARS; index++) {
            start[index] = goal[index];
            goal[index] = level(MediaWatch.band(index) * bandGain[index] * gain) * energy;
        }
    }

    private static float level(float amplitude) {
        float lifted = (float) Math.pow(Math.max(0.0f, amplitude), LEVEL_POWER);
        return UiAnim.clamp01((lifted - floorPower) / (1.0f - floorPower));
    }

    private static void glide() {
        float rise = HudConfig.visualizerAttack();
        for (int index = 0; index < BARS; index++) {
            float span = goal[index] > start[index] ? GLIDE_SECONDS / rise : GLIDE_SECONDS;
            reach[index] = start[index] + (goal[index] - start[index]) * UiAnim.smoothstep(0.0f, span, since);
        }
    }
}
