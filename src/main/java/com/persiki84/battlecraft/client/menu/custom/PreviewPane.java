package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiCorner;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiPulse;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSkin;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: образец материала: карточка здоровья, кнопка, строка, полоса, тост и три тона текста. Всё
// WHY: рисуется теми же примитивами, что и настоящий интерфейс, поэтому ручка стекла или цвета видна
// WHY: на образце сразу, как только её тронули
final class PreviewPane {
    private static final float GAP = 11.0f;
    private static final float CARD_HEIGHT = 58.0f;
    private static final float VITAL_ROW = 20.0f;
    private static final float DIAL_RADIUS = 6.4f;
    private static final float DIAL_STEP = 24.0f;
    private static final float DIAL_LABEL_SCALE = 0.62f;
    private static final float BUTTON_HEIGHT = 20.0f;
    private static final float ROW_HEIGHT = 20.0f;
    private static final float BAR_HEIGHT = 4.0f;
    private static final float TOAST_HEIGHT = 30.0f;
    private static final float HEALTH_SCALE = 1.8f;
    private static final float PULSE_CYCLES = 1.7f;
    private static final float PULSE_WIDTH = 1.05f;
    private static final long HEALTH_LOWEST = 14L;
    private static final long HEALTH_SPAN = 7L;
    private static final long HEALTH_TICK_MS = 1600L;
    private static final float TITLE_SCALE = 1.35f;
    private static final float POPUP_SHARE = 0.62f;
    private static final float POPUP_HEIGHT = 46.0f;
    private static final String[] DIAL_READINGS = {"8", "63%", "40%"};
    private static final String HEALTH_UNIT = "/20";
    private static final String ROW_VALUE = "42";
    private static final Component BUTTON = Component.translatable("battlecraft.custom.preview.button");
    private static final Component ROW = Component.translatable("battlecraft.custom.preview.row");
    private static final Component TOAST = Component.translatable("battlecraft.custom.preview.toast");
    private static final Component TEXT = Component.translatable("battlecraft.custom.preview.text");
    private static final Component TEXT_DIM = Component.translatable("battlecraft.custom.preview.text.dim");
    private static final Component TEXT_FAINT = Component.translatable("battlecraft.custom.preview.text.faint");

    private final UiTicker health = new UiTicker().align(UiTicker.LEFT);
    private float phase;
    private float sweep;

    public void advance(float delta) {
        phase += delta * 1.35f;
        if (phase > 1024.0f) phase -= 1024.0f;
        sweep += delta * 0.36f;
        if (sweep > 1.0f) sweep -= 1.0f;
        health.set(HEALTH_LOWEST + (System.currentTimeMillis() / HEALTH_TICK_MS) % HEALTH_SPAN);
    }

    float glass(GuiGraphics graphics, float x, float y, float width, PreviewHits hits) {
        Font font = Minecraft.getInstance().font;
        float cursor = drawVitalCard(graphics, font, x, y, width);
        cursor = drawButton(graphics, x, cursor, width);
        cursor = drawRow(graphics, font, x, cursor, width, hits);
        cursor = drawBar(graphics, x, cursor, width, hits);
        cursor = drawToast(graphics, font, x, cursor, width, hits);
        return drawText(graphics, font, x, cursor, width, 1.0f, hits);
    }

    // WHY: образец текста крупнее обычного: у шрифта, толщины и ореола видна сама буква, а не строка
    float text(GuiGraphics graphics, float x, float y, float width, PreviewHits hits) {
        Font font = Minecraft.getInstance().font;
        float cursor = drawText(graphics, font, x, y, width, TITLE_SCALE, hits);
        cursor = drawButton(graphics, x, cursor + GAP, width);
        cursor = drawRow(graphics, font, x, cursor, width, hits);
        return drawToast(graphics, font, x, cursor, width, hits);
    }

    // WHY: ореол виден только вокруг всплывающего над другим стеклом: образец кладёт окошко поверх
    // WHY: обычного набора и гасит мир вокруг него той же ручкой, что гасит его в игре
    float depth(GuiGraphics graphics, float x, float y, float width, PreviewHits hits) {
        float bottom = glass(graphics, x, y, width, hits);
        float popupWidth = width * POPUP_SHARE;
        float popupX = x + (width - popupWidth) / 2.0f;
        float popupY = y + CARD_HEIGHT + GAP + BUTTON_HEIGHT / 2.0f;
        float radius = UiMetrics.radius(POPUP_HEIGHT * 0.5f);
        UiGlass.above(graphics, popupX, popupY, popupWidth, POPUP_HEIGHT);
        UiGlass.hush(graphics, popupX, popupY, popupWidth, POPUP_HEIGHT, radius, 1.0f);
        UiGlass.panel(graphics, popupX, popupY, popupWidth, POPUP_HEIGHT, radius, 1.0f, 0.4f);
        UiRender.textCentered(graphics, Minecraft.getInstance().font, TOAST, popupX + popupWidth / 2.0f,
                UiRender.centerY(popupY, POPUP_HEIGHT, 0.9f), 0.9f, UiAccent.text(), false);
        return bottom;
    }

    // WHY: карточка здоровья и тост это образцы HUD, поэтому рисуются формой угла HUD, а не меню,
    // WHY: в котором стоит экран кастомизации: иначе превью слушало бы не ту ручку
    private float drawVitalCard(GuiGraphics graphics, Font font, float x, float y, float width) {
        boolean previous = UiCorner.hud(true);
        try {
            UiVital.card(graphics, x, y, width, CARD_HEIGHT, UiMetrics.radius(CARD_HEIGHT), 1.0f);
            drawVitals(graphics, font, x, y + 7.2f, width);
            drawDials(graphics, font, x + width / 2.0f, y + 7.2f + VITAL_ROW + 8.0f);
        } finally {
            UiCorner.hud(previous);
        }
        return y + CARD_HEIGHT + GAP;
    }

    private void drawVitals(GuiGraphics graphics, Font font, float x, float top, float width) {
        int lively = UiAccent.color();
        float numberY = UiRender.centerY(top, VITAL_ROW, HEALTH_SCALE) - 1.4f;
        health.draw(graphics, font, x + 9.0f, numberY, HEALTH_SCALE, lively);
        float unitX = x + 9.0f + health.steadyWidth(graphics, font, HEALTH_SCALE) + 2.5f;
        UiRender.labelScaled(graphics, font, HEALTH_UNIT, unitX, UiRender.centerY(top, VITAL_ROW * 0.86f, 0.8f),
                0.8f, UiAccent.textDim());
        float traceX = unitX + UiRender.width(font, HEALTH_UNIT) * 0.8f + 6.0f;
        UiPulse.render(graphics, traceX, top, x + width - 9.0f - traceX, VITAL_ROW, phase, PULSE_CYCLES,
                1.0f, PULSE_WIDTH, lively, 1.0f);
    }

    private void drawDials(GuiGraphics graphics, Font font, float centerX, float top) {
        float first = centerX - DIAL_STEP;
        for (int dial = 0; dial < DIAL_READINGS.length; dial++) {
            float dialX = first + dial * DIAL_STEP;
            UiRender.ring(graphics, dialX, top, DIAL_RADIUS, 2.3f, 1.0f, UiTheme.withAlpha(UiTheme.WHITE, 0.13f));
            UiRender.ring(graphics, dialX, top, DIAL_RADIUS, 2.3f, 0.35f + dial * 0.22f,
                    UiTheme.alpha(UiAccent.color(), 1.0f));
            UiRender.labelCentered(graphics, font, DIAL_READINGS[dial], dialX, top + DIAL_RADIUS + 4.0f,
                    DIAL_LABEL_SCALE, UiAccent.text());
        }
    }

    private float drawButton(GuiGraphics graphics, float x, float y, float width) {
        float focus = UiAnim.clamp01((float) Math.sin(sweep * Math.PI * 2.0) * 0.5f + 0.5f);
        UiSkin.button(graphics, x, y, width, BUTTON_HEIGHT, BUTTON, true, focus, 0.0f);
        return y + BUTTON_HEIGHT + GAP;
    }

    private float drawRow(GuiGraphics graphics, Font font, float x, float y, float width, PreviewHits hits) {
        UiGlass.panel(graphics, x, y, width, ROW_HEIGHT, UiMetrics.radius(ROW_HEIGHT), 1.0f, 0.2f);
        UiRender.panel(graphics, x + 3.5f, y + 3.5f, 2.2f, ROW_HEIGHT - 7.0f, 1.1f,
                UiTheme.alpha(UiAccent.color(), 0.85f));
        UiRender.textTrackedLeft(graphics, font, ROW, x + 10.0f, UiRender.centerY(y, ROW_HEIGHT, 0.95f), 0.95f, 0.3f,
                UiAccent.text());
        UiRender.textRight(graphics, font, ROW_VALUE, x + width - 8.0f, UiRender.centerY(y, ROW_HEIGHT, 0.95f), 0.95f,
                UiAccent.textDim(), false);
        hits.add(x, y, width, ROW_HEIGHT, PaletteKey.PANEL);
        return y + ROW_HEIGHT + GAP;
    }

    private float drawBar(GuiGraphics graphics, float x, float y, float width, PreviewHits hits) {
        UiGlass.sunken(graphics, x, y, width, BAR_HEIGHT, BAR_HEIGHT / 2.0f, 1.0f);
        UiGlass.progress(graphics, x, y, width, BAR_HEIGHT, 0.62f, UiAccent.color(), 1.0f);
        hits.add(x, y - 2.0f, width, BAR_HEIGHT + 4.0f, PaletteKey.ACCENT);
        return y + BAR_HEIGHT + GAP;
    }

    private float drawToast(GuiGraphics graphics, Font font, float x, float y, float width, PreviewHits hits) {
        boolean previous = UiCorner.hud(true);
        try {
            UiVital.card(graphics, x, y, width, TOAST_HEIGHT, 1.0f);
            UiRender.panel(graphics, x + 4.0f, y + 4.0f, 2.4f, TOAST_HEIGHT - 8.0f, 1.2f,
                    UiTheme.alpha(UiPalette.alert(), 0.9f));
            UiRender.textTrackedLeft(graphics, font, TOAST, x + 12.0f, y + 7.0f, 0.9f, 0.2f, UiAccent.text());
            UiGlass.progress(graphics, x + 12.0f, y + TOAST_HEIGHT - 8.0f, width - 20.0f, 2.6f, 0.45f,
                    UiPalette.alert(), 0.9f);
        } finally {
            UiCorner.hud(previous);
        }
        hits.add(x, y, width, TOAST_HEIGHT, PaletteKey.ALERT);
        return y + TOAST_HEIGHT + GAP;
    }

    private float drawText(GuiGraphics graphics, Font font, float x, float y, float width, float scale,
                           PreviewHits hits) {
        float line = font.lineHeight * scale + 2.0f;
        drawLine(graphics, font, TEXT, x, y, width, scale, UiAccent.text(), PaletteKey.TEXT, hits);
        drawLine(graphics, font, TEXT_DIM, x, y + line, width, scale * 0.9f, UiAccent.textDim(),
                PaletteKey.TEXT_DIM, hits);
        drawLine(graphics, font, TEXT_FAINT, x, y + line * 1.95f, width, scale * 0.85f, UiAccent.textFaint(),
                PaletteKey.TEXT_FAINT, hits);
        float dotY = y + line * 2.9f + 3.0f;
        UiRender.dot(graphics, x + 4.0f, dotY, 3.0f, UiGlassStyle.dispersion() > 0.0f
                ? UiAccent.color() : UiAccent.faint());
        return dotY + 6.0f;
    }

    private static void drawLine(GuiGraphics graphics, Font font, Component text, float x, float y, float width,
                                 float scale, int color, PaletteKey key, PreviewHits hits) {
        UiRender.textTrackedLeft(graphics, font, text, x, y, scale, 0.3f, color);
        hits.add(x, y - 1.0f, width, font.lineHeight * scale + 1.0f, key);
    }
}
