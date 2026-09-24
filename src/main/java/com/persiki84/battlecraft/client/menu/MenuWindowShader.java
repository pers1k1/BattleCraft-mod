package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MenuWindowShader {
    private static final ResourceLocation SHADER = new ResourceLocation(BattleCraftMod.MOD_ID, "menu_window");

    private static ShaderInstance windowShader;

    private MenuWindowShader() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                    shader -> windowShader = shader);
        } catch (IOException error) {
            LogUtils.getLogger().warn("[battlecraft] menu window off: {}", String.valueOf(error));
        }
    }

    public static ShaderInstance shader() {
        return windowShader;
    }
}
