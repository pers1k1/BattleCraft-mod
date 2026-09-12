package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiBlur {
    private static final ResourceLocation DOWN = new ResourceLocation("battlecraft", "ui_blur_down");
    private static final ResourceLocation UP = new ResourceLocation("battlecraft", "ui_blur_up");

    private static ShaderInstance downShader;
    private static ShaderInstance upShader;
    private static float offsetU;
    private static float offsetV;

    private UiBlur() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), DOWN, DefaultVertexFormat.POSITION_TEX),
                shader -> downShader = shader);
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), UP, DefaultVertexFormat.POSITION_TEX),
                shader -> upShader = shader);
    }

    public static boolean ready() {
        return downShader != null && upShader != null;
    }

    public static void useOffset(float u, float v) {
        offsetU = u;
        offsetV = v;
    }

    public static Supplier<ShaderInstance> shrinking() {
        return () -> armed(downShader);
    }

    public static Supplier<ShaderInstance> growing() {
        return () -> armed(upShader);
    }

    private static ShaderInstance armed(ShaderInstance shader) {
        shader.safeGetUniform("Offset").set(offsetU, offsetV);
        return shader;
    }
}
