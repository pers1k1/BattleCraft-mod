package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;

public final class UiScale {
    private static final float REFERENCE_GUI_SCALE = 3.2f;
    private static final float REFERENCE_PIXELS = 3.25f;
    private static final float REFERENCE_WIDTH = 1920.0f;
    private static final float REFERENCE_HEIGHT = 1080.0f;
    private static final float FLOOR_PIXELS = 1.5f;

    private static float userScale = 1.0f;
    private static BooleanSupplier followScreen = () -> true;

    private UiScale() {}

    public static void setUserScale(float value) {
        userScale = Math.max(0.25f, value);
    }

    public static void followScreen(BooleanSupplier source) {
        followScreen = source;
    }

    public static float factor() {
        Window window = Minecraft.getInstance().getWindow();
        double gui = window.getGuiScale();
        if (gui <= 0.0) return userScale;
        double base = followScreen.getAsBoolean() ? screenShare(window, gui) : Math.max(1.0, REFERENCE_GUI_SCALE / gui);
        return (float) base * userScale;
    }

    // WHY: HUD занимает одну и ту же долю окна при любом разрешении и любом масштабе интерфейса: на
    // WHY: 1080p единица HUD это 3.25 пикселя, дальше пропорционально меньшей из сторон относительно
    // WHY: 1920x1080, иначе на узком окне HUD вылезал бы за края. Целый масштаб Minecraft прыгал
    // WHY: ступенями (1080p - 4, 1440p - 6), и HUD рос рывками. При 4 пикселях экран 1080p вмещал
    // WHY: всего 480x270 единиц, и элементы HUD наезжали друг на друга, а 3 владелец счёл мелковатым.
    // WHY: Ручка размера HUD у игрока множит это значение. Пол FLOOR_PIXELS держит текст
    // WHY: читаемым в крошечном окне
    private static double screenShare(Window window, double gui) {
        double share = Math.min(window.getWidth() / REFERENCE_WIDTH, window.getHeight() / REFERENCE_HEIGHT);
        double pixels = Math.max(FLOOR_PIXELS, REFERENCE_PIXELS * share);
        return pixels / gui;
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
