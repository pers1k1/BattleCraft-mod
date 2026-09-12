package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class UiWorldTag {
    public static final float IDLE_ALPHA = 0.78f;
    public static final float FOCUS_ALPHA = 1.0f;

    private static final float FOCUS_LIFT = 0.45f;
    private static final float PADDING = UiMetrics.PAD;
    private static final float GAP = UiMetrics.GAP;
    private static final float RANGE_FLOOR = 0.02f;
    private static final float SINK = 5.0f;

    private UiWorldTag() {}

    public static float height(Font font, float scale) {
        return UiMetrics.snap(font.lineHeight * scale + UiMetrics.GAP);
    }

    // WHY: сдвиг ухода прибавляется после подъёма стека, а не до: стек считает подъём от переданного
    // WHY: верха и сглаженно гасил бы сдвиг обратно, отчего метка опускалась и тут же всплывала
    public static void render(GuiGraphics graphics, Font font, Component name, Component range, float ranged,
                              float centerX, float topY, float height, float scale, int color,
                              float alpha, float focus, float presence, UiTagStack.Slot stack) {
        float shown = alpha * presence;
        float nameWidth = UiRender.measure(graphics, font, name, scale);
        float rangeWidth = range == null || ranged <= RANGE_FLOOR
                ? 0.0f
                : (UiRender.measure(graphics, font, range, scale) + GAP) * ranged;

        float width = nameWidth + rangeWidth + PADDING * 2.0f;
        float left = centerX - width / 2.0f;
        topY += UiTagStack.lift(stack, centerX, topY, width, height) + (1.0f - presence) * SINK;
        float textY = UiRender.centerY(topY, height, scale);

        UiVital.card(graphics, left, topY, width, height, UiMetrics.radius(height), shown, focus * FOCUS_LIFT);
        UiRender.labelScaled(graphics, font, name, left + PADDING, textY, scale, UiTheme.withAlpha(color, shown));

        if (rangeWidth <= RANGE_FLOOR) return;

        UiRender.labelScaled(graphics, font, range, left + PADDING + nameWidth + GAP * ranged, textY, scale,
                UiTheme.withAlpha(color, shown * ranged));
    }
}
