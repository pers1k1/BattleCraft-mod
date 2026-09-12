package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;

import java.io.IOException;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiLens {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_lens");
    private static final Matrix3f FLAT = new Matrix3f();

    private static ShaderInstance lensShader;
    private static float dispersion;
    private static float brightness = 1.0f;

    private UiLens() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX_COLOR),
                shader -> lensShader = shader);
    }

    public static void useDispersion(float value) {
        dispersion = Math.max(0.0f, value);
    }

    public static void useBrightness(float value) {
        brightness = Math.max(0.0f, value);
    }

    public static boolean splits() {
        return lensShader != null && (dispersion > 0.0005f || Math.abs(brightness - 1.0f) > 0.002f);
    }

    public static Supplier<ShaderInstance> prepared() {
        return UiLens::current;
    }

    private static ShaderInstance current() {
        lensShader.safeGetUniform("Dispersion").set(dispersion);
        lensShader.safeGetUniform("Brightness").set(brightness);
        UiPlane plane = UiBackdrop.carrier();
        lensShader.safeGetUniform("LensWarp").set(plane == null ? FLAT : plane.warp());
        return lensShader;
    }
}
