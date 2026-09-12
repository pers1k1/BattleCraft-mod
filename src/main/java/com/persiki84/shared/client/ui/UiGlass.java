package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class UiGlass {
    private static final float SHEEN_LIFT = 0.42f;
    private static final float BORDER_SHARE = 0.2f;
    private static final float THIN_BAR = 3.6f;

    private UiGlass() {}

    public static void panel(GuiGraphics graphics, float x, float y, float width, float height, float radius, float alpha) {
        surface(graphics, x, y, width, height, radius, alpha, 0.0f, 0);
    }

    public static void panel(GuiGraphics graphics, float x, float y, float width, float height, float radius, float alpha, float lift) {
        surface(graphics, x, y, width, height, radius, alpha, lift, 0);
    }

    public static void window(GuiGraphics graphics, float x, float y, float width, float height, float radius, float alpha) {
        window(graphics, x, y, width, height, radius, alpha, 0.0f);
    }

    public static void window(GuiGraphics graphics, float x, float y, float width, float height, float radius, float alpha, float lift) {
        surface(graphics, x, y, width, height, radius, alpha, lift, 0);
    }

    public static void tinted(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                              float alpha, float lift, int tint) {
        surface(graphics, x, y, width, height, radius, alpha, lift, tint);
    }

    public static void deep(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                            float refraction, float alpha, float lift, int tint) {
        if (width <= 0.0f || height <= 0.0f || refraction <= 0.004f) return;

        float border = borderFor(width, height);
        float insetX = x + border;
        float insetY = y + border;
        float insetWidth = width - border * 2.0f;
        float insetHeight = height - border * 2.0f;
        float insetRadius = Math.max(0.0f, radius - border);

        if (!pane(graphics, insetX, insetY, insetWidth, insetHeight, insetRadius, alpha, lift, tint)) {
            boolean previous = UiRender.dissolving(true);
            try {
                UiBackdrop.refract(graphics, insetX, insetY, insetWidth, insetHeight, insetRadius,
                        refraction * UiGlassStyle.surfaceAlpha());
                body(graphics, insetX, insetY, insetWidth, insetHeight, insetRadius, alpha, lift, tint);
            } finally {
                UiRender.dissolving(previous);
            }
        }
    }

    public static void layer(GuiGraphics graphics) {
        graphics.flush();
        UiBackdrop.restage();
    }

    private static boolean pane(GuiGraphics graphics, float x, float y, float width, float height,
                                float radius, float alpha, float lift, int tint) {
        if (!UiGlassStyle.liquid() || !UiPane.ready()) return false;
        if (!UiQuality.refracting() || !UiBackdrop.active()) return false;

        UiPane.draw(graphics, x, y, width, height, radius, alpha * UiGlassStyle.surfaceAlpha(),
                bodyTop(lift, tint), bodyBottom(lift, tint), UiBackdrop.texture());
        return true;
    }

    private static int bodyTop(float lift, int tint) {
        return tinted(UiTheme.mix(UiPalette.glassTop(), UiPalette.glassTopLit(), gained(lift)), tint, 1.0f);
    }

    private static int bodyBottom(float lift, int tint) {
        return tinted(UiTheme.mix(UiPalette.glassBottom(), UiPalette.glassBottomLit(), gained(lift)), tint, 0.7f);
    }

    private static float gained(float lift) {
        return Math.min(1.0f, lift * UiGlassStyle.liftGain());
    }

    private static int tinted(int base, int tint, float share) {
        if ((tint >>> 24) == 0) return base;

        float weight = (tint >>> 24) / 255.0f * share;
        return UiTheme.mix(base, UiTheme.withAlpha(tint, (base >>> 24) / 255.0f), weight);
    }

    private static float borderFor(float width, float height) {
        float wanted = UiGlassStyle.border();
        if (wanted <= 0.0f) return 0.0f;

        return Math.min(wanted, Math.min(width, height) * BORDER_SHARE);
    }

    public static void inner(GuiGraphics graphics, float x, float y, float width, float height, float radius, float alpha, float lift) {
        if (width <= 0.0f || height <= 0.0f || alpha <= 0.004f) return;
        UiRender.panelShaded(graphics, x, y, width, height, radius,
                UiTheme.alpha(UiTheme.mix(UiPalette.glassTopLit(), UiTheme.WHITE, 0.42f + 0.22f * lift), alpha),
                UiTheme.alpha(UiTheme.mix(UiPalette.glassBottomLit(), UiTheme.WHITE, 0.30f + 0.12f * lift), alpha));
    }

    public static void progress(GuiGraphics graphics, float x, float y, float width, float height,
                                float value, int color, float alpha) {
        float clamped = UiAnim.clamp01(value);
        if (clamped <= 0.0f || width <= 0.0f || height <= 0.0f || alpha <= 0.004f) return;

        float radius = height / 2.0f;
        float filled = width * clamped;
        if (filled < height) {
            float fade = alpha * (filled / height);
            UiRender.panelShaded(graphics, x, y, height, height, radius,
                    UiTheme.alpha(UiTheme.lighten(color, 0.28f), fade), UiTheme.alpha(color, fade));
            return;
        }

        UiRender.panelShaded(graphics, x, y, filled, height, radius,
                UiTheme.alpha(UiTheme.lighten(color, 0.30f), alpha), UiTheme.alpha(color, alpha));

        int sheen = UiTheme.lighten(color, SHEEN_LIFT) & 0xFFFFFF;
        if (height >= THIN_BAR) {
            float inset = 0.7f;
            UiRender.panelShaded(graphics, x + inset, y + inset, filled - inset * 2.0f, height * 0.45f,
                    Math.max(0.0f, radius - inset),
                    UiTheme.withAlpha(sheen, 0.24f * alpha), UiTheme.withAlpha(sheen, 0.0f));
            return;
        }

        UiRender.panelShaded(graphics, x, y, filled, height * 0.55f, radius,
                UiTheme.withAlpha(sheen, 0.20f * alpha), UiTheme.withAlpha(sheen, 0.0f));
    }

    public static void sunken(GuiGraphics graphics, float x, float y, float width, float height, float radius, float alpha) {
        if (width <= 0.0f || height <= 0.0f || alpha <= 0.004f) return;
        UiRender.panelShaded(graphics, x, y, width, height, radius,
                UiTheme.alpha(UiPalette.wellTop(), alpha),
                UiTheme.alpha(UiPalette.wellBottom(), alpha));
    }

    public static void sheet(GuiGraphics graphics, float x, float y, float width, float height, float radius, float alpha) {
        window(graphics, x, y, width, height, radius, alpha);
    }

    private static void surface(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                                float alpha, float lift, int tint) {
        deep(graphics, x, y, width, height, radius, alpha, alpha, lift, tint);
    }

    private static void body(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                             float alpha, float lift, int tint) {
        float gained = Math.min(1.0f, lift * UiGlassStyle.liftGain());
        int top = UiTheme.mix(UiPalette.glassTop(), UiPalette.glassTopLit(), gained);
        int bottom = UiTheme.mix(UiPalette.glassBottom(), UiPalette.glassBottomLit(), gained);
        if ((tint >>> 24) != 0) {
            float weight = (tint >>> 24) / 255.0f;
            top = UiTheme.mix(top, UiTheme.withAlpha(tint, (top >>> 24) / 255.0f), weight);
            bottom = UiTheme.mix(bottom, UiTheme.withAlpha(tint, (bottom >>> 24) / 255.0f), weight * 0.7f);
        }
        float density = UiBackdrop.active() ? UiGlassStyle.density() : 1.0f;
        float body = alpha * density * UiGlassStyle.surfaceAlpha();
        UiRender.panelShaded(graphics, x, y, width, height, radius,
                UiTheme.alpha(top, body), UiTheme.alpha(bottom, body));
    }

}
