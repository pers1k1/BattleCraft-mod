package com.persiki84.shared.client.ui;

import com.persiki84.shared.client.font.MsdfShaders;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

public final class UiMarquee {
    private static final float UNITS_PER_SECOND = 9.0f;
    private static final float DWELL_SECONDS = 1.8f;
    private static final float MIN_TRAVEL = 0.5f;
    private static final long FORGET_MS = 400L;
    private static final int SLOTS = 128;

    private static final float REVEAL_LINES = 1.8f;
    private static final float REVEAL_BOX_SHARE = 0.26f;
    private static final float REVEAL_GROWTH = 3.0f;
    private static final float MARGIN_LINES = 0.4f;
    private static final float MARGIN_BOX_SHARE = 0.08f;
    private static final float LINE_UNITS = 9.0f;
    private static final float SHADE_LIGHTNESS = 0.22f;
    private static final float SHADE_STRENGTH = 0.6f;

    private static final int[] KEYS = new int[SLOTS];
    private static final long[] STARTED = new long[SLOTS];
    private static final long[] SEEN = new long[SLOTS];

    private static int shadeSource = 0;
    private static int shade = 0xFF000000;

    private UiMarquee() {}

    public static float shift(String text, float travel) {
        if (travel <= MIN_TRAVEL) return 0.0f;

        long now = Util.getMillis();
        float seconds = (now - startOf(text.hashCode(), now)) / 1000.0f;
        float run = travel / UNITS_PER_SECOND;
        float phase = seconds % ((DWELL_SECONDS + run) * 2.0f);

        if (phase < DWELL_SECONDS) return 0.0f;
        if (phase < DWELL_SECONDS + run) return travel * UiAnim.smoothstep(0.0f, run, phase - DWELL_SECONDS);
        if (phase < DWELL_SECONDS * 2.0f + run) return travel;
        return travel * (1.0f - UiAnim.smoothstep(0.0f, run, phase - DWELL_SECONDS * 2.0f - run));
    }

    // WHY: время строки идёт от её появления, а не от общих часов: иначе имя, открытое
    // WHY: посреди хода, приходило уже уехавшим и начало слова игрок не видел вовсе
    private static long startOf(int key, long now) {
        int stale = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (KEYS[i] == key && SEEN[i] != 0L && now - SEEN[i] <= FORGET_MS) {
                SEEN[i] = now;
                return STARTED[i];
            }
            if (SEEN[i] < SEEN[stale]) stale = i;
        }
        KEYS[stale] = key;
        STARTED[stale] = now;
        SEEN[stale] = now;
        return now;
    }

    public static float margin(float boxWidth, float scale) {
        return Math.min(LINE_UNITS * scale * MARGIN_LINES, boxWidth * MARGIN_BOX_SHARE);
    }

    // WHY: полоса стоит на запасе за краем коробки и на покое укрывает только выступ первой буквы;
    // WHY: с началом хода она растёт втрое быстрее уходящего текста, поэтому ни одна буква
    // WHY: не выходит из стенки, а в конце хода не теряет тень раньше, чем докатится строка
    public static void reveal(GuiGraphics graphics, float boxLeft, float boxWidth, float textLeft, float span,
                              float scale) {
        float margin = margin(boxWidth, scale);
        float fade = Math.max(margin, Math.min(LINE_UNITS * scale * REVEAL_LINES, boxWidth * REVEAL_BOX_SHARE));
        float leftWidth = ramp(boxLeft - textLeft, margin, fade);
        float rightWidth = ramp(textLeft + span - boxLeft - boxWidth, margin, fade);

        Matrix4f pose = graphics.pose().last().pose();
        float unit = Math.abs(pose.m00());
        float left = (boxLeft - margin) * pose.m00() + pose.m30();
        float right = (boxLeft + boxWidth + margin) * pose.m00() + pose.m30();
        boolean mirrored = pose.m00() < 0.0f;
        MsdfShaders.reveal(Math.min(left, right), Math.max(left, right),
                (mirrored ? rightWidth : leftWidth) * unit, (mirrored ? leftWidth : rightWidth) * unit,
                shade(), SHADE_STRENGTH);
    }

    private static float ramp(float hidden, float margin, float fade) {
        return Math.min(fade, margin + Math.max(0.0f, hidden) * REVEAL_GROWTH);
    }

    public static void conceal() {
        MsdfShaders.conceal();
    }

    private static int shade() {
        int accent = UiAccent.color();
        if (accent != shadeSource) {
            shadeSource = accent;
            shade = UiOklab.withLightness(accent, SHADE_LIGHTNESS);
        }
        return shade;
    }
}
