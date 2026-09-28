package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.UiMarquee;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

// WHY: строка плитки меряется только на смене текста, языка или плотности пикселей покоя: панель
// WHY: растёт из 0.9 масштаба, и плотность текущего кадра менялась бы весь ход открытия. Не
// WHY: влезающая строка не режется многоточием, а идёт бегущей строкой, как в «Сейчас играет»
final class CenterLabel {
    private static final float LINE_UNITS = 10.0f;
    private static final float CLIP_SLACK = 2.0f;
    private static final float PIXEL_SLACK = 0.01f;

    private final float scale;
    private Component measured = Component.empty();
    private Language measuredLanguage;
    private String raw = "";
    private float measuredPixels = -1.0f;
    private float span;

    CenterLabel(float scale) {
        this.scale = scale;
    }

    float span(GuiGraphics graphics, Font font, Component value) {
        remeasure(graphics, font, value);
        return span;
    }

    void paint(GuiGraphics graphics, Font font, Component value, float x, float y, float slot, int color) {
        if ((color >>> 24) < 3 || slot <= 2.0f) return;
        remeasure(graphics, font, value);
        if (span <= slot) {
            UiRender.labelScaled(graphics, font, value, x, y, scale, color);
            return;
        }
        float margin = UiMarquee.margin(slot, scale);
        float start = x - UiRender.marqueeShift(graphics, raw, span - slot);
        UiRender.clip(graphics, x - margin, y - CLIP_SLACK, slot + margin * 2.0f, LINE_UNITS * scale + CLIP_SLACK * 2.0f);
        UiRender.marqueeFrom(graphics, x, slot, start, span, scale);
        try {
            UiRender.labelScaled(graphics, font, value, start, y, scale, color);
        } finally {
            UiRender.marqueeDone();
            graphics.disableScissor();
        }
    }

    private void remeasure(GuiGraphics graphics, Font font, Component value) {
        float pixels = UiRender.pixels(graphics) / UiRestFrame.stretchX();
        Language language = Language.getInstance();
        if (value == measured && Math.abs(pixels - measuredPixels) < PIXEL_SLACK && language == measuredLanguage) return;
        measured = value;
        measuredPixels = pixels;
        measuredLanguage = language;
        raw = value.getString();
        span = UiRender.measure(graphics, font, value, scale);
    }
}
