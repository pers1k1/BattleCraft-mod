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

// WHY: значки в духе SF Symbols считаются в шейдере неявными формами: чёткие на любом размере,
// WHY: сглаживание ровно в пиксель, а уровень (громкость, яркость) меняет форму плавно, без кадров
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiIcon {
    public enum Kind {
        PERSON, GLOBE, GEAR, SLIDERS, PHOTO, POWER, SEARCH, TOGGLES, SPEAKER, SUN, NOTE, FULLSCREEN,
        SPARKLE, DROP, PLUS, PLAY, PAUSE, FORWARD, BACKWARD, CHEVRON_LEFT, CHEVRON_RIGHT, CALENDAR, CHAT,
        MOTION
    }

    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_icon");
    private static final float STROKE = 0.1f;
    private static final float MARGIN = 1.15f;

    private static ShaderInstance iconShader;

    private UiIcon() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> iconShader = shader);
    }

    public static void draw(GuiGraphics graphics, Kind kind, float centerX, float centerY, float size, int color) {
        draw(graphics, kind, centerX, centerY, size, color, 1.0f);
    }

    public static void draw(GuiGraphics graphics, Kind kind, float centerX, float centerY, float size, int color,
                            float level) {
        if (iconShader == null || size <= 0.0f || (color >>> 24) == 0) return;

        graphics.flush();
        iconShader.safeGetUniform("IconColor").set(((color >> 16) & 0xFF) / 255.0f, ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f, ((color >>> 24) & 0xFF) / 255.0f);
        iconShader.safeGetUniform("IconShape").set(kind.ordinal(), UiAnim.clamp01(level), STROKE, 0.0f);
        UiRender.standardBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> iconShader);
        float half = size / 2.0f * MARGIN;
        quad(graphics.pose().last().pose(), centerX - half, centerY - half, half * 2.0f);
        RenderSystem.enableCull();
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
