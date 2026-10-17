package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiQuality;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: миниатюра пресета рисуется из его же чисел: светлота, плотность, линза, радуга и рамка стоят на
// WHY: карточке в той доле, в какой они есть у пресета. Подменить глобальное стекло ради плитки нельзя:
// WHY: стиль стекла общий на весь кадр, и подмена на время отрисовки перекрашивала бы весь экран
final class PresetGlyph {
    private static final float INSET = 0.18f;
    private static final float BLOB = 0.34f;
    private static final float DISPERSION_SHIFT = 0.7f;
    private static final float LENS_SHARE = 0.26f;
    private static final float PULL_FULL = 24.0f;
    private static final float BORDER_FULL = 6.0f;
    private static final float RADIUS_FULL = 20.0f;
    private static final int RED = 0xFFFF4D4D;
    private static final int BLUE = 0xFF4D7BFF;

    private PresetGlyph() {
    }

    static void paint(GuiGraphics graphics, PresetCard card, float x, float y, float width, float height,
                      float alpha) {
        int accent = card.accent() == 0 ? UiAccent.color() : card.accent();
        float radius = Math.min(width, height) * 0.16f;
        UiGlass.sunken(graphics, x, y, width, height, radius, alpha);
        paintBlobs(graphics, x, y, width, height, accent, alpha);
        float insetX = width * INSET;
        float insetY = height * INSET;
        paintCard(graphics, card, x + insetX, y + insetY, width - insetX * 2.0f, height - insetY * 2.0f, alpha);
    }

    private static void paintBlobs(GuiGraphics graphics, float x, float y, float width, float height, int accent,
                                   float alpha) {
        float blob = Math.min(width, height) * BLOB;
        UiRender.dot(graphics, x + width * 0.3f, y + height * 0.42f, blob, UiTheme.alpha(accent, 0.75f * alpha));
        UiRender.dot(graphics, x + width * 0.7f, y + height * 0.62f, blob * 0.8f,
                UiTheme.alpha(UiTheme.mix(accent, UiTheme.WHITE, 0.55f), 0.55f * alpha));
    }

    private static void paintCard(GuiGraphics graphics, PresetCard card, float x, float y, float width, float height,
                                  float alpha) {
        float radius = Math.min(height / 2.0f, 2.0f + card.value(GlassKey.RADIUS_PANEL) / RADIUS_FULL * height * 0.4f);
        paintSurface(graphics, card, x, y, width, height, radius, alpha);
        paintLight(graphics, card, x, y, width, height, radius, alpha);
        paintLens(graphics, card, x, y, width, height, alpha);
        paintEdges(graphics, card, x, y, width, height, radius, alpha);
    }

    private static void paintSurface(GuiGraphics graphics, PresetCard card, float x, float y, float width,
                                     float height, float radius, float alpha) {
        float surface = card.value(GlassKey.SURFACE_ALPHA);
        if (card.base().mode() == UiQuality.Mode.PLAIN) {
            UiRender.panel(graphics, x, y, width, height, radius, UiTheme.alpha(UiTheme.PANEL, surface * alpha));
            return;
        }
        float lift = Math.min(1.0f, card.value(GlassKey.LIFT) / 2.5f);
        UiGlass.panel(graphics, x, y, width, height, radius, alpha, lift);
        float frost = card.base().mode() == UiQuality.Mode.BLUR ? 0.22f : card.value(GlassKey.DENSITY) * 0.18f;
        if (frost > 0.01f) UiRender.panel(graphics, x, y, width, height, radius, UiTheme.withAlpha(UiTheme.WHITE,
                frost * alpha));
    }

    private static void paintLight(GuiGraphics graphics, PresetCard card, float x, float y, float width,
                                   float height, float radius, float alpha) {
        float brightness = card.value(GlassKey.BRIGHTNESS);
        if (brightness < 1.0f) {
            UiRender.panel(graphics, x, y, width, height, radius,
                    UiTheme.withAlpha(UiTheme.BLACK, (1.0f - brightness) * 0.7f * alpha));
        } else if (brightness > 1.0f) {
            UiRender.panel(graphics, x, y, width, height, radius,
                    UiTheme.withAlpha(UiTheme.WHITE, Math.min(0.3f, (brightness - 1.0f) * 0.3f) * alpha));
        }
        if (!card.base().liquid()) return;
        float sheen = height * 0.16f;
        UiRender.panel(graphics, x + width * 0.12f, y + sheen * 0.6f, width * 0.5f, sheen, sheen / 2.0f,
                UiTheme.withAlpha(UiTheme.WHITE, 0.22f * alpha));
    }

    private static void paintLens(GuiGraphics graphics, PresetCard card, float x, float y, float width,
                                  float height, float alpha) {
        float pull = Math.min(1.0f, card.value(GlassKey.PULL) / PULL_FULL + card.value(GlassKey.ZOOM) * 6.0f);
        if (pull <= 0.02f) return;
        float radius = Math.min(width, height) * LENS_SHARE;
        UiRender.ring(graphics, x + width * 0.62f, y + height * 0.55f, radius, 1.0f + pull, 1.0f,
                UiTheme.withAlpha(UiTheme.WHITE, (0.18f + 0.4f * pull) * alpha));
    }

    private static void paintEdges(GuiGraphics graphics, PresetCard card, float x, float y, float width,
                                   float height, float radius, float alpha) {
        float dispersion = Math.min(1.0f, card.value(GlassKey.DISPERSION));
        if (dispersion > 0.02f) {
            float shift = DISPERSION_SHIFT;
            UiRender.rim(graphics, x - shift, y, width, height, radius, 1.0f, UiTheme.alpha(RED, 0.6f * dispersion * alpha));
            UiRender.rim(graphics, x + shift, y, width, height, radius, 1.0f, UiTheme.alpha(BLUE, 0.6f * dispersion * alpha));
        }
        float border = Math.min(1.0f, card.value(GlassKey.BORDER) / BORDER_FULL);
        UiRender.rim(graphics, x, y, width, height, radius, 0.6f + border,
                UiTheme.withAlpha(UiTheme.WHITE, (0.12f + 0.4f * border) * alpha));
    }
}
