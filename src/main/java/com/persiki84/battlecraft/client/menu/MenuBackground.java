package com.persiki84.battlecraft.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAmbience;
import com.persiki84.shared.client.ui.UiAurora;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class MenuBackground {
    private static final float POINTER_SPEED = 1.9f;
    private static final float SCREEN_CENTER = 0.5f;

    private static final MenuBackground SHARED = new MenuBackground();

    private final Smooth pointerX = new Smooth(SCREEN_CENTER, POINTER_SPEED);
    private final Smooth pointerY = new Smooth(SCREEN_CENTER, POINTER_SPEED);

    public static MenuBackground shared() {
        return SHARED;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return;

        UiAmbience.advance();
        float delta = UiFrame.delta();
        float aimX = pointerX.to(pointerFraction(true), delta);
        float aimY = pointerY.to(pointerFraction(false), delta);

        int backdrop = UiPalette.backdrop();
        if (UiAurora.field(graphics, screenWidth, screenHeight, backdrop, aimX, aimY)) return;

        UiRender.rect(graphics, 0.0f, 0.0f, screenWidth, screenHeight, backdrop);
    }

    private static float pointerFraction(boolean horizontal) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null) return SCREEN_CENTER;

        double scale = client.getWindow().getGuiScale();
        float size = horizontal ? (float) (client.getWindow().getScreenWidth() / scale)
                : (float) (client.getWindow().getScreenHeight() / scale);
        if (size <= 0.0f) return SCREEN_CENTER;

        float position = horizontal ? (float) (client.mouseHandler.xpos() / scale)
                : (float) (client.mouseHandler.ypos() / scale);
        return Mth.clamp(position / size, 0.0f, 1.0f);
    }
}
