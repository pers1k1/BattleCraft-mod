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
public final class UiFlip {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_flip");

    private static final int STRIPS = 16;
    private static final float DEPTH_SHARE = 3.0f;
    private static final float SHADE_FLOOR = 0.62f;
    private static final float SHADE_TURN = 0.38f;
    private static final float EDGE_SOFT_PIXELS = 1.2f;

    private static final float[] stripX = new float[STRIPS + 1];
    private static final float[] stripTop = new float[STRIPS + 1];
    private static final float[] stripBottom = new float[STRIPS + 1];

    private static ShaderInstance flipShader;

    private UiFlip() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> flipShader = shader);
    }

    public static boolean card(GuiGraphics graphics, ResourceLocation texture, int source,
                               float centerX, float centerY, float size, float angle,
                               float mash, float corner, float seed, float alpha) {
        if (flipShader == null || size <= 1.0f || alpha <= 0.01f) return false;

        graphics.flush();
        arm(mash, corner, seed, alpha, softness(graphics, size), shadeAt(angle), source);

        UiRender.standardBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> flipShader);
        RenderSystem.setShaderTexture(0, texture);
        weave(graphics.pose().last().pose(), centerX, centerY, size, angle);
        RenderSystem.enableCull();
        UiRender.standardBlend();
        return true;
    }

    private static void arm(float mash, float corner, float seed, float alpha, float soft,
                            float shade, int source) {
        flipShader.safeGetUniform("Mash").set(mash);
        flipShader.safeGetUniform("Corner").set(corner);
        flipShader.safeGetUniform("Soft").set(soft);
        flipShader.safeGetUniform("Inset").set(0.5f / Math.max(1, source));
        flipShader.safeGetUniform("Shade").set(shade);
        flipShader.safeGetUniform("Fade").set(alpha);
        flipShader.safeGetUniform("Seed").set(seed);
    }

    // WHY: свет скользит по повёрнутой поверхности, и без этой потери яркости ребро карточки
    // WHY: читается как обрыв картинки, а не как поворот
    private static float shadeAt(float angle) {
        return SHADE_FLOOR + SHADE_TURN * Math.abs((float) Math.cos(angle));
    }

    private static float softness(GuiGraphics graphics, float size) {
        return EDGE_SOFT_PIXELS / Math.max(1.0f, size * UiRender.pixels(graphics));
    }

    // WHY: у интерфейса ортогональная проекция, на ней поворот вокруг вертикали вырождается
    // WHY: в сжатие по ширине и разворот вправо не отличить от разворота влево. Поэтому карточка
    // WHY: режется на вертикальные полосы, и каждая ставится по своей глубине: ближняя половина
    // WHY: растёт, дальняя сжимается, и сторона поворота видна
    private static void weave(Matrix4f matrix, float centerX, float centerY, float size, float angle) {
        lay(centerX, centerY, size, angle);

        boolean back = Math.cos(angle) < 0.0;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int strip = 0; strip < STRIPS; strip++) {
            float left = along(strip, back);
            float right = along(strip + 1, back);
            corner(builder, matrix, stripX[strip], stripBottom[strip], left, 1.0f);
            corner(builder, matrix, stripX[strip + 1], stripBottom[strip + 1], right, 1.0f);
            corner(builder, matrix, stripX[strip + 1], stripTop[strip + 1], right, 0.0f);
            corner(builder, matrix, stripX[strip], stripTop[strip], left, 0.0f);
        }
        Tesselator.getInstance().end();
    }

    // WHY: за прямым углом полосы идут справа налево, и обратная сторона выходила зеркальной,
    // WHY: а в конце хода щёлкала в правильную. У настоящей карточки обратная сторона напечатана
    // WHY: зеркально, поэтому её развёртка тоже разворачивается
    private static float along(int stop, boolean back) {
        float share = stop / (float) STRIPS;
        return back ? 1.0f - share : share;
    }

    private static void lay(float centerX, float centerY, float size, float angle) {
        float half = size / 2.0f;
        float depth = size * DEPTH_SHARE;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        for (int stop = 0; stop <= STRIPS; stop++) {
            float along = (stop / (float) STRIPS - 0.5f) * size;
            float near = depth / (depth + along * sin);
            stripX[stop] = centerX + along * cos * near;
            stripTop[stop] = centerY - half * near;
            stripBottom[stop] = centerY + half * near;
        }
    }

    private static void corner(BufferBuilder builder, Matrix4f matrix, float x, float y, float u, float v) {
        builder.vertex(matrix, x, y, 0.0f).uv(u, v).endVertex();
    }
}
