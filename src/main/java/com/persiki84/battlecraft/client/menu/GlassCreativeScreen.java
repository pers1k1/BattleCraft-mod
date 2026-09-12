package com.persiki84.battlecraft.client.menu;

import com.persiki84.shared.client.menu.DimmedScreen;
import com.persiki84.shared.client.menu.ScreenVeil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;

public class GlassCreativeScreen extends CreativeModeInventoryScreen implements DimmedScreen {

    public GlassCreativeScreen(Player player, FeatureFlagSet features, boolean operatorTab) {
        super(player, features, operatorTab);
    }

    public static Screen wrapping(Screen screen) {
        if (!(screen instanceof CreativeModeInventoryScreen) || screen instanceof GlassCreativeScreen) return null;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;

        return new GlassCreativeScreen(minecraft.player, minecraft.level.enabledFeatures(),
                minecraft.options.operatorItemsTab().get());
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        ScreenVeil.render(graphics, this);
    }
}
