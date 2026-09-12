package com.persiki84.battlecraft.client.menu;

import com.persiki84.shared.client.menu.DimmedScreen;
import com.persiki84.shared.client.menu.ScreenVeil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

public class GlassPauseScreen extends PauseScreen implements DimmedScreen {
    private static final String MENU_FIELD = "f_96306_";

    private static boolean probed;
    private static Field menuField;

    public GlassPauseScreen(boolean showPauseMenu) {
        super(showPauseMenu);
    }

    public static GlassPauseScreen wrapping(PauseScreen screen) {
        Boolean withMenu = readMenuFlag(screen);
        return withMenu == null ? null : new GlassPauseScreen(withMenu);
    }

    private static Boolean readMenuFlag(PauseScreen screen) {
        if (!probed) {
            probed = true;
            try {
                menuField = ObfuscationReflectionHelper.findField(PauseScreen.class, MENU_FIELD);
            } catch (Throwable error) {
                menuField = null;
            }
        }
        if (menuField == null) return null;

        try {
            return menuField.getBoolean(screen);
        } catch (Throwable error) {
            menuField = null;
            return null;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        ScreenVeil.render(graphics, this);
    }
}
