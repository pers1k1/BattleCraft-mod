package com.persiki84.battlecraft.client.roster;

import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: пять столбиков растущей высоты вместо ванильной картинки из icons.png; пороги те же,
// WHY: что у ванильного списка (150, 300, 600, 1000 мс), чтобы игрок читал их по привычке
final class RosterPing {
    static final float WIDTH = 10.2f;

    private static final int BARS = 5;
    private static final int[] LIMITS = {150, 300, 600, 1000};
    private static final float BAR_WIDTH = 1.4f;
    private static final float BAR_GAP = 0.8f;
    private static final float LOWEST = 2.4f;
    private static final float GROWTH = 1.15f;
    private static final float UNLIT_ALPHA = 0.22f;

    private RosterPing() {}

    public static void draw(GuiGraphics graphics, float left, float centerY, int latency, float alpha) {
        int lit = barsOf(latency);
        int tone = lit >= BARS - 1 ? HudInk.text() : lit >= BARS - 2 ? RosterTone.WARN : RosterTone.BAD;
        float bottom = centerY + (LOWEST + GROWTH * (BARS - 1)) / 2.0f;
        for (int bar = 0; bar < BARS; bar++) {
            float tall = LOWEST + GROWTH * bar;
            float x = left + bar * (BAR_WIDTH + BAR_GAP);
            int color = bar < lit ? UiTheme.alpha(tone, alpha) : UiTheme.alpha(HudInk.textFaint(), UNLIT_ALPHA * alpha);
            stroke(graphics, x, bottom - tall, tall, color);
        }
    }

    private static int barsOf(int latency) {
        if (latency < 0) return 0;
        int bars = BARS;
        for (int limit : LIMITS) {
            if (latency < limit) return bars;
            bars--;
        }
        return bars;
    }

    // WHY: столбик шириной в пару пикселей полигоном идёт ступенькой по кромке, точная форма ровная
    private static void stroke(GuiGraphics graphics, float x, float y, float tall, int color) {
        if (UiCrisp.ready()) {
            UiCrisp.panel(graphics, x, y, BAR_WIDTH, tall, BAR_WIDTH / 2.0f, color);
            return;
        }
        UiRender.panel(graphics, x, y, BAR_WIDTH, tall, BAR_WIDTH / 2.0f, color);
    }
}
