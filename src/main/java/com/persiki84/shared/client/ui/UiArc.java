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
public final class UiArc {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_arc");
    private static final float EDGE_SOFT_PIXELS = 1.15f;

    private static ShaderInstance arcShader;

    private UiArc() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> arcShader = shader);
    }

    public static boolean draw(GuiGraphics graphics, float centerX, float centerY, float radius, float thickness,
                               float swept, int color) {
        if (arcShader == null) return false;

        float soft = EDGE_SOFT_PIXELS / Math.max(0.05f, UiRender.pixels(graphics));
        float half = radius + thickness * 0.5f + soft * 2.0f;
        arcShader.safeGetUniform("ArcColor").set(((color >> 16) & 0xFF) / 255.0f, ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f, ((color >>> 24) & 0xFF) / 255.0f);
        arcShader.safeGetUniform("Half").set(half, half);
        arcShader.safeGetUniform("Arc").set(radius, thickness, swept, soft);
        UiRender.standardBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> arcShader);
        quad(graphics.pose().last().pose(), centerX - half, centerY - half, half * 2.0f);
        RenderSystem.enableCull();
        return true;
    }

    private static void quad(Matrix4f matrix, float x, float y, float size) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, x, y + size, 0.0f).uv(0.0f, 1.0f).endVertex();
        builder.vertex(matrix, x + size, y + size, 0.0f).uv(1.0f, 1.0f).endVertex();
        builder.vertex(matrix, x + size, y, 0.0f).uv(1.0f, 0.0f).endVertex();
        builder.vertex(matrix, x, y, 0.0f).uv(0.0f, 0.0f).endVertex();
        Tesselator.getInstance().end();
    }
}
