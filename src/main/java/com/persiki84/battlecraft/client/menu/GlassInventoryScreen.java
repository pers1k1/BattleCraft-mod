package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.shared.client.menu.DimmedScreen;
import com.persiki84.shared.client.menu.ScreenVeil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.opengl.GL11;

public class GlassInventoryScreen extends InventoryScreen implements DimmedScreen {

    public GlassInventoryScreen(Player player) {
        super(player);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        if ((com.persiki84.battlecraft.client.hud.HudConfig.plainScreens() & 2) != 0) {
            super.renderBackground(graphics);
            return;
        }
        ScreenVeil.render(graphics, this);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.flush();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        super.renderBg(graphics, partialTick, mouseX, mouseY);
    }
}
