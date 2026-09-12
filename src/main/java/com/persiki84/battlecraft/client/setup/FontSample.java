package com.persiki84.battlecraft.client.setup;

import com.persiki84.shared.client.font.FontShape;
import com.persiki84.shared.client.font.MsdfFontSets;
import com.persiki84.shared.client.font.MsdfShaders;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class FontSample {
    private static final float HEAD_SCALE = 1.4f;
    private static final float STATS_SCALE = 0.9f;
    private static final float BODY_SCALE = 0.72f;
    private static final int STATS_TOP = 26;
    private static final int BODY_TOP = 48;
    private static final Component HEAD = Component.translatable("battlecraft.font.sample.head");
    private static final Component STATS = Component.translatable("battlecraft.font.sample.stats");
    private static final Component BODY = Component.translatable("battlecraft.font.sample.body");

    private FontSample() {}

    // WHY: соседние карточки набраны разной толщиной, а юниформ живёт до сброса батча:
    // WHY: без пары flush вся страница нарисуется толщиной последней карточки
    public static void paintWeighted(GuiGraphics graphics, Font font, float weight, float centerX, int top, int width) {
        graphics.flush();
        MsdfShaders.pushWeight(weight);
        try {
            paintLines(graphics, font, centerX, top, width);
            graphics.flush();
        } finally {
            MsdfShaders.popWeight();
        }
    }

    public static void paint(GuiGraphics graphics, Font font, FontShape shape, float centerX, int top, int width) {
        if (!MsdfFontSets.available(shape)) {
            UiRender.textCentered(graphics, font, Component.translatable("battlecraft.font.missing"),
                    centerX, top + STATS_TOP, BODY_SCALE, UiAccent.textFaint(), false);
            return;
        }

        MsdfFontSets.push(shape);
        try {
            paintLines(graphics, font, centerX, top, width);
        } finally {
            MsdfFontSets.pop();
        }
    }

    private static void paintLines(GuiGraphics graphics, Font font, float centerX, int top, int width) {
        paintLine(graphics, font, HEAD, centerX, top, width, HEAD_SCALE, UiAccent.text());
        paintLine(graphics, font, STATS, centerX, top + STATS_TOP, width, STATS_SCALE, UiAccent.text());
        paintLine(graphics, font, BODY, centerX, top + BODY_TOP, width, BODY_SCALE, UiAccent.textDim());
    }

    private static void paintLine(GuiGraphics graphics, Font font, Component label, float centerX,
                                  int top, int width, float scale, int color) {
        float available = Math.max(1.0f, width - 12.0f);
        float fitted = scale;
        while (fitted > 0.1f && UiRender.measure(graphics, font, label, fitted) > available) {
            fitted *= 0.95f;
        }
        UiRender.labelCentered(graphics, font, label, centerX, top, fitted, color);
    }
}
