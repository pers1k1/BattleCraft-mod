package com.persiki84.battlecraft.client.menu;

import com.persiki84.shared.client.ui.UiGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

public final class ForeignPanel {
    private static final String TEXTURE_OWNER = "voicechat";
    private static final String SCREEN_OWNER = "de.maxhenkel.voicechat.gui";
    private static final int MIN_WIDTH = 64;
    private static final int MIN_HEIGHT = 32;
    private static final float RADIUS = 9.0f;
    private static final float ALPHA = 1.0f;

    private ForeignPanel() {}

    public static boolean replace(GuiGraphics graphics, ResourceLocation texture,
                                  int left, int top, int width, int height) {
        if (width < MIN_WIDTH || height < MIN_HEIGHT) return false;
        if (!TEXTURE_OWNER.equals(texture.getNamespace())) return false;

        Screen screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.getClass().getName().startsWith(SCREEN_OWNER)) return false;

        graphics.flush();
        UiGlass.window(graphics, left, top, width, height, RADIUS, ALPHA);
        return true;
    }
}
