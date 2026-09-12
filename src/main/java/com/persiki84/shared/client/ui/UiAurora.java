package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiAurora {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_aurora");

    private static final float BRIGHT_STRENGTH = 0.30f;
    private static final float MID_STRENGTH = 0.21f;
    private static final float DEEP_STRENGTH = 0.16f;

    private static ShaderInstance auroraShader;
    private static long lastFrame = -1L;
    private static float elapsed;

    private UiAurora() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> auroraShader = shader);
    }

    public static boolean field(GuiGraphics graphics, float width, float height, int backdrop,
                                float pointerX, float pointerY) {
        if (auroraShader == null || width <= 0.0f || height <= 0.0f) return false;

        graphics.flush();
        arm(backdrop, pointerX, pointerY);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        UiRender.standardBlend();
        RenderSystem.setShader(() -> auroraShader);

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, 0.0f, height, 0.0f).uv(0.0f, 1.0f).endVertex();
        builder.vertex(matrix, width, height, 0.0f).uv(1.0f, 1.0f).endVertex();
        builder.vertex(matrix, width, 0.0f, 0.0f).uv(1.0f, 0.0f).endVertex();
        builder.vertex(matrix, 0.0f, 0.0f, 0.0f).uv(0.0f, 0.0f).endVertex();
        Tesselator.getInstance().end();
        return true;
    }

    private static void arm(int backdrop, float pointerX, float pointerY) {
        auroraShader.safeGetUniform("Time").set(clock());
        auroraShader.safeGetUniform("Pointer").set(pointerX, pointerY);
        auroraShader.safeGetUniform("Zoom").set(UiAmbience.zoom());
        auroraShader.safeGetUniform("Haze").set(UiAmbience.haze());
        auroraShader.safeGetUniform("Veil").set(UiAmbience.veil());
        tint("TintA", UiAccent.color(), BRIGHT_STRENGTH);
        tint("TintB", UiTheme.mix(UiAccent.dim(), UiPalette.glassTopLit(), 0.55f), MID_STRENGTH);
        tint("TintC", UiTheme.mix(UiAccent.faint(), UiPalette.panelRaised(), 0.7f), DEEP_STRENGTH);
        tint("Backdrop", backdrop, 1.0f);
    }

    private static void tint(String name, int color, float strength) {
        auroraShader.safeGetUniform(name).set(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                strength);
    }

    private static float clock() {
        long frame = UiFrame.frame();
        if (frame != lastFrame) {
            lastFrame = frame;
            elapsed += UiFrame.delta();
        }
        return elapsed;
    }
}
