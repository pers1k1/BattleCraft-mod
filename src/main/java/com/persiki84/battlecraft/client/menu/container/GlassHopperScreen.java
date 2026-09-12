package com.persiki84.battlecraft.client.menu.container;

import com.persiki84.shared.client.menu.DimmedScreen;
import com.persiki84.shared.client.menu.ScreenVeil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.HopperMenu;

public class GlassHopperScreen extends HopperScreen implements DimmedScreen {

    public GlassHopperScreen(HopperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        ScreenVeil.render(graphics, this);
    }
}
