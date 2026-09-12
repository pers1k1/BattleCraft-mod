package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class SharpHud {
    private static final List<Layer> layers = new ArrayList<>();

    private SharpHud() {}

    public interface Layer {
        void draw(GuiGraphics graphics);
    }

    public static void add(Layer layer) {
        layers.add(layer);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGuiRendered(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || layers.isEmpty()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.flush();
        for (Layer layer : layers) {
            layer.draw(graphics);
        }
        graphics.flush();
    }
}
