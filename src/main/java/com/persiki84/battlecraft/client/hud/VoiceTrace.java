package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.voice.VoiceTape;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: лента диктофона рисуется как одна дорожка: точка это тишина, штрих это голос. Поэтому
// WHY: шаг у точек и у штрихов общий, а бежит всё одним смещением - иначе тишина и речь идут
// WHY: с разной скоростью, и лента перестаёт читаться записью
public final class VoiceTrace {
    private static final float STEP = 2.4f;

    private static final float BAR_WIDTH = 1.1f;
    private static final float DOT_HEIGHT = 1.2f;
    private static final float MIN_VOICE = 2.6f;
    private static final float FADE_MARKS = 7.0f;
    private static final float QUIET_ALPHA = 0.32f;

    private VoiceTrace() {}

    public static void paint(GuiGraphics graphics, float left, float centerY, float height,
                             VoiceTape tape, long now, int color, float alpha) {
        paint(graphics, left, centerY, height, tape, now, color, alpha, VoiceTape.MARKS);
    }

    public static void paint(GuiGraphics graphics, float left, float centerY, float height,
                             VoiceTape tape, long now, int color, float alpha, int marks) {
        if (alpha <= 0.01f) return;

        float slide = tape.step(now);
        int written = tape.written();
        int from = Math.max(0, VoiceTape.MARKS - marks);
        boolean sharp = UiCrisp.ready();
        if (sharp) graphics.flush();

        for (int index = from; index < VoiceTape.MARKS; index++) {
            float level = index >= VoiceTape.MARKS - written ? tape.mark(index) : 0.0f;
            float x = left + (index - from - slide) * STEP;
            paintMark(graphics, sharp, x, centerY, height, level, color,
                    alpha * edge(index - from, marks, slide) * (level > 0.0f ? 1.0f : QUIET_ALPHA));
        }
    }

    public static float width(int marks) {
        return marks * STEP;
    }

    // WHY: и слева, и справа лента гаснет: слева штрих уползает за край, справа въезжает новый,
    // WHY: и без гашения оба конца моргают целой полоской на каждом шаге
    private static float edge(int index, int marks, float slide) {
        float from = UiAnim.clamp01((index - slide) / FADE_MARKS);
        float to = UiAnim.clamp01((marks - 1 - index + slide) / 1.6f);
        return from * to;
    }

    private static void paintMark(GuiGraphics graphics, boolean sharp, float x, float centerY, float height,
                                  float level, int color, float alpha) {
        if (alpha <= 0.004f) return;

        float tall = level <= 0.0f ? DOT_HEIGHT : Math.max(MIN_VOICE, height * level);
        int tint = UiTheme.alpha(color, alpha);
        if (sharp) {
            UiCrisp.panel(graphics, x, centerY - tall / 2.0f, BAR_WIDTH, tall, BAR_WIDTH / 2.0f, tint);
            return;
        }
        UiRender.panel(graphics, x, centerY - tall / 2.0f, BAR_WIDTH, tall, BAR_WIDTH / 2.0f, tint);
    }
}
