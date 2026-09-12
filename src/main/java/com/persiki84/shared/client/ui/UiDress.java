package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class UiDress {
    public interface Wardrobe {
        void begin(GuiGraphics graphics, Screen screen);

        void end(GuiGraphics graphics, Screen screen);
    }

    private static Wardrobe wardrobe;

    private UiDress() {}

    public static void wardrobe(Wardrobe value) {
        wardrobe = value;
    }

    public static void begin(GuiGraphics graphics, Screen screen) {
        if (wardrobe != null) wardrobe.begin(graphics, screen);
    }

    public static void end(GuiGraphics graphics, Screen screen) {
        if (wardrobe != null) wardrobe.end(graphics, screen);
    }
}
