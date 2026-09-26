package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.media.MediaGain;
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
    // WHY: подъём 40 мс и спад 60 мс подобраны на живом захвате Spotify: рывок между кадрами на 39 %
    // WHY: мягче, чем при 30 мс в обе стороны, а отставание от звука 37 мс вместо 29
    private static final float RISE_SECONDS = 0.04f;
    private static final float FALL_SECONDS = 0.06f;
    private static final float ONSET_RISE = 0.17f;
    private static final float ONSET_SPACING = 0.025f;
    private static final float[] BAND_DECIBELS = {-1.277f, -3.687f, 1.353f, -0.083f, -1.338f, -3.379f};
    private static final float LEVEL_POWER = 0.759f;
    private static final float LEVEL_FLOOR = 0.002f;
    // WHY: у айфона высота срезается о край, и громкий кусок (дроп) ставил полоски столбом в
    // WHY: потолок. Выше 0.6 высота сжимается плавно и не доходит до края, полоски продолжают плясать
    private static final float SOFT_TOP = 0.6f;

    private static final float[] weighted = new float[BARS];
    private static final float[] start = new float[BARS];
    private static final float[] goal = new float[BARS];
    private static final float[] reach = new float[BARS];
    private static final float[] heard = new float[BARS];
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

    // WHY: сетка 15 Гц держит шаг айфона, но полоса больше не отстаёт на такт: по записям она
    // WHY: прыгала к уровню прошлого такта и лишь потом ехала к новому, и свежий уровень виден был
    // WHY: только через 66 мс. Теперь на такте она едет от того места, где стоит, к свежему уровню,
    // WHY: а резкий подъём (удар) не ждёт такта вовсе: сетка сдвигается под удар
    public static void pulse(float energy) {
        long frame = UiFrame.frame();
        if (frame == stamp) return;

        stamp = frame;
        since += UiFrame.delta() * HudConfig.visualizerSpeed();
        listen(energy);
        if (since >= TICK_SECONDS || struck()) {
            since = since >= TICK_SECONDS ? since % TICK_SECONDS : 0.0f;
            retarget();
        }
        glide();
    }

    private static void listen(float energy) {
        float loudest = 0.0f;
        for (int index = 0; index < BARS; index++) {
            weighted[index] = MediaWatch.band(index) * bandGain[index];
            loudest = Math.max(loudest, weighted[index]);
        }
        float scale = MediaGain.scale(loudest) * HudConfig.visualizerGain();
        for (int index = 0; index < BARS; index++) {
            heard[index] = level(weighted[index] * scale) * energy;
        }
    }

    private static boolean struck() {
        if (since < ONSET_SPACING) return false;

        for (int index = 0; index < BARS; index++) {
            if (heard[index] - goal[index] > ONSET_RISE) return true;
        }
        return false;
    }

    private static void retarget() {
        for (int index = 0; index < BARS; index++) {
            start[index] = reach[index];
            goal[index] = heard[index];
        }
    }

    private static float level(float amplitude) {
        float lifted = (float) Math.pow(Math.max(0.0f, amplitude), LEVEL_POWER);
        float height = Math.max(0.0f, (lifted - floorPower) / (1.0f - floorPower));
        if (height <= SOFT_TOP) return height;

        float room = 1.0f - SOFT_TOP;
        return SOFT_TOP + room * (float) Math.tanh((height - SOFT_TOP) / room);
    }

    private static void glide() {
        float rise = HudConfig.visualizerAttack();
        for (int index = 0; index < BARS; index++) {
            float span = goal[index] > start[index] ? RISE_SECONDS / rise : FALL_SECONDS;
            reach[index] = start[index] + (goal[index] - start[index]) * UiAnim.smoothstep(0.0f, span, since);
        }
    }
}
