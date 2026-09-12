package com.persiki84.shared.client.font;

import com.persiki84.shared.client.ui.UiGlassStyle;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MsdfShaders {
    private static final float DEFAULT_DISTANCE_RANGE = 6.0f;

    private static final ShaderInstance[] shaders = new ShaderInstance[FontShape.values().length];
    private static final float[] ranges = filledRanges();

    private static boolean overriding;
    private static float override;

    private MsdfShaders() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        MsdfFontSets.retry();
        for (FontShape shape : FontShape.values()) {
            int slot = shape.ordinal();
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), shape.shader(),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    shader -> shaders[slot] = shader);
        }
    }

    public static void useDistanceRange(FontShape shape, float range) {
        ranges[shape.ordinal()] = range;
    }

    public static Supplier<ShaderInstance> prepared(FontShape shape) {
        int slot = shape.ordinal();
        return () -> current(slot);
    }

    private static ShaderInstance current(int slot) {
        ShaderInstance shader = shaders[slot];
        if (shader != null) {
            shader.safeGetUniform("DistanceRange").set(ranges[slot]);
            shader.safeGetUniform("TextWeight").set(weight());
        }
        return shader;
    }

    // WHY: юниформ живёт до сброса батча, поэтому образец другой толщины обязан выдавить
    // WHY: свои буквы на экран сам: без flush всю страницу нарисует последняя выставленная толщина
    public static void pushWeight(float value) {
        overriding = true;
        override = value;
    }

    public static void popWeight() {
        overriding = false;
    }

    private static float weight() {
        return overriding ? override : UiGlassStyle.textWeight();
    }

    private static float[] filledRanges() {
        float[] prepared = new float[FontShape.values().length];
        Arrays.fill(prepared, DEFAULT_DISTANCE_RANGE);
        return prepared;
    }
}
