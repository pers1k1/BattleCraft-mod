package com.persiki84.battlecraft.client.setup;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class InkSample {
    private static final float HEAD_SCALE = 1.1f;
    private static final float STATS_SCALE = 0.86f;
    private static final float BODY_SCALE = 0.72f;
    private static final int STATS_TOP = 21;
    private static final int BODY_TOP = 38;

    private InkSample() {}

    // WHY: три строки рисуются тремя уровнями одной тройки, потому что вопрос страницы не в том,
    // WHY: какого цвета заголовок, а в том, различимы ли после перекраски приглушённый и тусклый
    public static void paint(GuiGraphics graphics, Font font, boolean accent, float centerX, int top) {
        UiRender.labelCentered(graphics, font, Component.translatable("battlecraft.font.sample.head"),
                centerX, top, HEAD_SCALE, accent ? UiAccent.accentText() : UiAccent.plainText());
        UiRender.textCentered(graphics, font, Component.translatable("battlecraft.font.sample.stats"),
                centerX, top + STATS_TOP, STATS_SCALE,
                accent ? UiAccent.accentTextDim() : UiAccent.plainTextDim(), false);
        UiRender.textCentered(graphics, font, Component.translatable("battlecraft.font.sample.body"),
                centerX, top + BODY_TOP, BODY_SCALE,
                accent ? UiAccent.accentTextFaint() : UiAccent.plainTextFaint(), false);
    }
}
