package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class UiTitle {
    private static final float PAD = 13.0f;
    private static final float PAD_Y = 6.0f;
    private static final float PLATE_ALPHA = 1.0f;

    private UiTitle() {}

    public static void render(GuiGraphics graphics, Font font, Component title, float centerX, float top,
                              float scale, float tracking, int color) {
        plate(graphics, font, UiRender.measureTracked(graphics, font, title, scale, tracking), centerX, top, scale);
        UiRender.textTracked(graphics, font, title, centerX, top, scale, tracking, color);
    }

    public static void title(GuiGraphics graphics, Font font, Component title, float centerX, float top,
                             float scale, float tracking, int color) {
        plate(graphics, font, UiRender.measureTitle(graphics, font, title, scale, tracking), centerX, top, scale);
        UiRender.textTitle(graphics, font, title, centerX, top, scale, tracking, color);
    }

    private static void plate(GuiGraphics graphics, Font font, float span, float centerX, float top, float scale) {
        if (span <= 0.0f) return;

        float textHeight = font.lineHeight * scale;
        float height = textHeight + PAD_Y * 2.0f;
        float width = span + PAD * 2.0f;

        UiGlass.panel(graphics, centerX - width / 2.0f, top - PAD_Y, width, height,
                UiMetrics.radius(height), PLATE_ALPHA);
    }
}
