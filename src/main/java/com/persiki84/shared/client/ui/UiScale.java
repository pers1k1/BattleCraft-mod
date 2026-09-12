package com.persiki84.shared.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class UiScale {
    private static final float REFERENCE_GUI_SCALE = 3.2f;

    private static float userScale = 1.0f;

    private UiScale() {}

    public static void setUserScale(float value) {
        userScale = Math.max(0.25f, value);
    }

    public static float factor() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        if (gui <= 0.0) return userScale;
        return (float) Math.max(1.0, REFERENCE_GUI_SCALE / gui) * userScale;
    }

    public static float push(GuiGraphics graphics) {
        float scale = factor();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        return scale;
    }

    public static void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
    }
}
