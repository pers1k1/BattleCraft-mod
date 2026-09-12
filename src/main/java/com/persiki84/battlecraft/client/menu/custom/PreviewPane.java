package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiPulse;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSkin;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PreviewPane {
    public static final int WIDTH = 190;

    private static final float PAD = 12.0f;
    private static final float GAP = 11.0f;
    private static final float TITLE_SCALE = 0.9f;
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
    private static final float SAMPLE_HEALTH = 17.0f;

    private float phase;
    private float sweep;

    public void advance(float delta) {
        phase += delta * 1.35f;
        if (phase > 1024.0f) phase -= 1024.0f;
        sweep += delta * 0.36f;
        if (sweep > 1.0f) sweep -= 1.0f;
    }

    public void render(GuiGraphics graphics, float x, float y, float height) {
        advance(UiFrame.delta());

        Font font = Minecraft.getInstance().font;
        UiGlass.window(graphics, x, y, WIDTH, height, 9.0f, 1.0f);
        UiRender.textCentered(graphics, font, Component.translatable("battlecraft.custom.preview"),
                x + WIDTH / 2.0f, y + 6.0f, TITLE_SCALE, UiAccent.textDim(), false);

        float inner = x + PAD;
        float innerWidth = WIDTH - PAD * 2.0f;
        float cursor = y + 22.0f;

        cursor = drawVitalCard(graphics, font, inner, cursor, innerWidth);
        cursor = drawButton(graphics, inner, cursor, innerWidth);
        cursor = drawRow(graphics, font, inner, cursor, innerWidth);
        cursor = drawBar(graphics, inner, cursor, innerWidth);
        cursor = drawToast(graphics, font, inner, cursor, innerWidth);
        drawText(graphics, font, inner, cursor);
    }

    private float drawVitalCard(GuiGraphics graphics, Font font, float x, float y, float width) {
        UiVital.card(graphics, x, y, width, CARD_HEIGHT, UiMetrics.radius(CARD_HEIGHT), 1.0f);
        drawVitals(graphics, font, x, y + PAD * 0.6f, width);
        drawDials(graphics, font, x + width / 2.0f, y + PAD * 0.6f + VITAL_ROW + 8.0f);
        return y + CARD_HEIGHT + GAP;
    }

    private void drawVitals(GuiGraphics graphics, Font font, float x, float top, float width) {
        int lively = UiAccent.color();
        String reading = String.valueOf((int) SAMPLE_HEALTH);

        UiRender.labelScaled(graphics, font, reading, x + 9.0f,
                UiRender.centerY(top, VITAL_ROW, HEALTH_SCALE) - 1.4f, HEALTH_SCALE, lively);
        float unitX = x + 9.0f + UiRender.widthLabel(font, reading) * HEALTH_SCALE + 2.5f;
        UiRender.labelScaled(graphics, font, "/20", unitX,
                UiRender.centerY(top, VITAL_ROW * 0.86f, 0.8f), 0.8f, UiAccent.textDim());

        float traceX = unitX + UiRender.width(font, "/20") * 0.8f + 6.0f;
        UiPulse.render(graphics, traceX, top, x + width - 9.0f - traceX, VITAL_ROW, phase, PULSE_CYCLES,
                1.0f, PULSE_WIDTH, lively, 1.0f);
    }

    private void drawDials(GuiGraphics graphics, Font font, float centerX, float top) {
        float span = DIAL_STEP * 2.0f;
        float first = centerX - span / 2.0f;
        String[] readings = {"8", "63%", "40%"};

        for (int dial = 0; dial < readings.length; dial++) {
            float dialX = first + dial * DIAL_STEP;
            UiRender.ring(graphics, dialX, top, DIAL_RADIUS, 2.3f, 1.0f,
                    UiTheme.withAlpha(UiTheme.WHITE, 0.13f));
            UiRender.ring(graphics, dialX, top, DIAL_RADIUS, 2.3f, 0.35f + dial * 0.22f,
                    UiTheme.alpha(UiAccent.color(), 1.0f));
            UiRender.labelCentered(graphics, font, readings[dial], dialX, top + DIAL_RADIUS + 4.0f,
                    DIAL_LABEL_SCALE, UiAccent.text());
        }
    }

    private float drawButton(GuiGraphics graphics, float x, float y, float width) {
        float focus = UiAnim.clamp01((float) Math.sin(sweep * Math.PI * 2.0) * 0.5f + 0.5f);
        UiSkin.button(graphics, x, y, width, BUTTON_HEIGHT,
                Component.translatable("battlecraft.custom.preview.button"), true, focus, 0.0f);
        return y + BUTTON_HEIGHT + GAP;
    }

    private float drawRow(GuiGraphics graphics, Font font, float x, float y, float width) {
        UiGlass.panel(graphics, x, y, width, ROW_HEIGHT, UiMetrics.radius(ROW_HEIGHT), 1.0f, 0.2f);
        UiRender.panel(graphics, x + 3.5f, y + 3.5f, 2.2f, ROW_HEIGHT - 7.0f, 1.1f,
                UiTheme.alpha(UiAccent.color(), 0.85f));
        UiRender.textTrackedLeft(graphics, font, Component.translatable("battlecraft.custom.preview.row"),
                x + 10.0f, UiRender.centerY(y, ROW_HEIGHT, 0.95f), 0.95f, 0.3f, UiAccent.text());
        UiRender.textRight(graphics, font, "42", x + width - 8.0f,
                UiRender.centerY(y, ROW_HEIGHT, 0.95f), 0.95f, UiAccent.textDim(), false);
        return y + ROW_HEIGHT + GAP;
    }

    private float drawBar(GuiGraphics graphics, float x, float y, float width) {
        UiGlass.sunken(graphics, x, y, width, BAR_HEIGHT, BAR_HEIGHT / 2.0f, 1.0f);
        UiGlass.progress(graphics, x, y, width, BAR_HEIGHT, 0.62f, UiAccent.color(), 1.0f);
        return y + BAR_HEIGHT + GAP;
    }

    private float drawToast(GuiGraphics graphics, Font font, float x, float y, float width) {
        UiVital.card(graphics, x, y, width, TOAST_HEIGHT, 1.0f);
        UiRender.panel(graphics, x + 4.0f, y + 4.0f, 2.4f, TOAST_HEIGHT - 8.0f, 1.2f,
                UiTheme.alpha(UiPalette.alert(), 0.9f));
        UiRender.textTrackedLeft(graphics, font, Component.translatable("battlecraft.custom.preview.toast"),
                x + 12.0f, y + 7.0f, 0.9f, 0.2f, UiAccent.text());
        UiGlass.progress(graphics, x + 12.0f, y + TOAST_HEIGHT - 8.0f, width - 20.0f, 2.6f, 0.45f,
                UiPalette.alert(), 0.9f);
        return y + TOAST_HEIGHT + GAP;
    }

    private void drawText(GuiGraphics graphics, Font font, float x, float y) {
        UiRender.textTrackedLeft(graphics, font, Component.translatable("battlecraft.custom.preview.text"),
                x, y, 1.0f, 0.3f, UiAccent.text());
        UiRender.textTrackedLeft(graphics, font, Component.translatable("battlecraft.custom.preview.text.dim"),
                x, y + 11.0f, 0.9f, 0.3f, UiAccent.textDim());
        UiRender.textTrackedLeft(graphics, font, Component.translatable("battlecraft.custom.preview.text.faint"),
                x, y + 21.0f, 0.85f, 0.3f, UiAccent.textFaint());
        UiRender.dot(graphics, x + 4.0f, y + 36.0f, 3.0f, UiGlassStyle.dispersion() > 0.0f
                ? UiAccent.color() : UiAccent.faint());
    }
}
