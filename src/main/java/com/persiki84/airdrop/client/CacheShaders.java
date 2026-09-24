package com.persiki84.airdrop.client;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.persiki84.airdrop.AirDropMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = AirDropMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CacheShaders {
    public static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX;

    private static ShaderInstance shader;
    private static AbstractUniform origin;
    private static AbstractUniform scale;
    private static AbstractUniform color;
    private static AbstractUniform mode;
    private static AbstractUniform phase;
    private static AbstractUniform pulse;
    private static AbstractUniform fade;
    private static AbstractUniform spin;
    private static AbstractUniform drift;
    private static AbstractUniform time;

    private CacheShaders() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(AirDropMod.MOD_ID, "loot_cache"), FORMAT), CacheShaders::hold);
    }

    private static void hold(ShaderInstance loaded) {
        shader = loaded;
        origin = loaded.safeGetUniform("CacheOrigin");
        scale = loaded.safeGetUniform("CacheScale");
        color = loaded.safeGetUniform("CacheColor");
        mode = loaded.safeGetUniform("Mode");
        phase = loaded.safeGetUniform("Phase");
        pulse = loaded.safeGetUniform("Pulse");
        fade = loaded.safeGetUniform("Fade");
        spin = loaded.safeGetUniform("Spin");
        drift = loaded.safeGetUniform("Drift");
        time = loaded.safeGetUniform("Time");
    }

    public static ShaderInstance shader() {
        return shader;
    }

    public static boolean ready() {
        return shader != null;
    }

    public static void place(float x, float y, float z, float scaleX, float scaleY, float scaleZ) {
        origin.set(x, y, z);
        scale.set(scaleX, scaleY, scaleZ);
    }

    public static void paint(int argb, float alpha) {
        color.set((argb >> 16 & 0xFF) / 255.0f, (argb >> 8 & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f, alpha);
    }

    public static void shape(float modeValue, float phaseValue, float pulseValue) {
        mode.set(modeValue);
        phase.set(phaseValue);
        pulse.set(pulseValue);
    }

    public static void motion(float fadeValue, float spinValue, float driftValue, float timeValue) {
        fade.set(fadeValue);
        spin.set(spinValue);
        drift.set(driftValue);
        time.set(timeValue);
    }
}
