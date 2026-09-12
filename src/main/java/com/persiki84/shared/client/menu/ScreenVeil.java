package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenVeil {
    private static final float IN_SPEED = 19.0f;
    private static final float OUT_SPEED = 27.0f;
    private static final float GONE = 0.004f;

    private static final long MARK_LIFETIME = 2L;

    private static final Smooth veil = new Smooth(0.0f, IN_SPEED);

    private static Screen marked;
    private static long markedFrame = -1L;

    private ScreenVeil() {}

    public static void advance(float delta) {
        boolean wanted = wanted();
        veil.to(wanted ? 1.0f : 0.0f, wanted ? IN_SPEED : OUT_SPEED, delta);
    }

    public static void mark(Screen screen) {
        marked = screen;
        markedFrame = UiFrame.frame();
    }

    private static boolean wanted() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        if (screen instanceof GlassScreen) return false;
        if (screen instanceof DimmedScreen) return true;
        return screen == marked && UiFrame.frame() - markedFrame <= MARK_LIFETIME;
    }

    public static float value() {
        return UiAnim.easeOut(veil.get());
    }

    public static boolean lingering() {
        return Minecraft.getInstance().screen == null && value() > GONE;
    }

    public static void render(GuiGraphics graphics, Screen screen) {
        ScreenDim.render(graphics, screen.width, screen.height, value());
        graphics.flush();
        UiBackdrop.capture();
    }

    public static void linger(GuiGraphics graphics, int width, int height) {
        ScreenDim.render(graphics, width, height, value());
    }
}
