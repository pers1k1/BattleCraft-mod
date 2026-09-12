package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.menu.ScreenVeil;
import net.minecraft.client.gui.GuiGraphics;

public final class VeilOverlay {

    private VeilOverlay() {}

    public static void render(GuiGraphics graphics, int width, int height) {
        if (!ScreenVeil.lingering()) return;

        ScreenVeil.linger(graphics, width, height);
        graphics.flush();
    }
}
