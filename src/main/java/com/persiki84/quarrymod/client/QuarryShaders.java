package com.persiki84.quarrymod.client;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.persiki84.quarrymod.QuarryMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

// WHY: юниформы берутся один раз на сборку шейдера, а не по имени в кадре: у выработки их
// WHY: десяток на каждый из трёх проходов, и поиск по строке шёл бы сотни раз за кадр
@Mod.EventBusSubscriber(modid = QuarryMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QuarryShaders {
    public static final VertexFormat FIELD_FORMAT = DefaultVertexFormat.POSITION_TEX;

    private static ShaderInstance fieldShader;
    private static AbstractUniform origin;
    private static AbstractUniform scale;
    private static AbstractUniform color;
    private static AbstractUniform mode;
    private static AbstractUniform phase;
    private static AbstractUniform pulse;
    private static AbstractUniform fade;
    private static AbstractUniform spin;
    private static AbstractUniform time;

    private QuarryShaders() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(QuarryMod.MODID, "quarry_field"), FIELD_FORMAT),
                QuarryShaders::hold);
    }

    private static void hold(ShaderInstance shader) {
        fieldShader = shader;
        origin = shader.safeGetUniform("QuarryOrigin");
        scale = shader.safeGetUniform("QuarryScale");
        color = shader.safeGetUniform("QuarryColor");
        mode = shader.safeGetUniform("Mode");
        phase = shader.safeGetUniform("Phase");
        pulse = shader.safeGetUniform("Pulse");
        fade = shader.safeGetUniform("Fade");
        spin = shader.safeGetUniform("Spin");
        time = shader.safeGetUniform("Time");
    }

    public static ShaderInstance field() {
        return fieldShader;
    }

    public static boolean ready() {
        return fieldShader != null;
    }

    public static void place(float originX, float originY, float originZ,
                             float scaleX, float scaleY, float scaleZ) {
        origin.set(originX, originY, originZ);
        scale.set(scaleX, scaleY, scaleZ);
    }

    public static void paint(float red, float green, float blue, float alpha) {
        color.set(red, green, blue, alpha);
    }

    public static void shape(float modeValue, float phaseValue, float pulseValue) {
        mode.set(modeValue);
        phase.set(phaseValue);
        pulse.set(pulseValue);
    }

    public static void motion(float fadeValue, float spinValue, float timeValue) {
        fade.set(fadeValue);
        spin.set(spinValue);
        time.set(timeValue);
    }
}
