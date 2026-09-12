package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiPane {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_pane");
    private static final float DRIFT_SECONDS = 1000.0f;
    private static final float EDGE_SLACK = 2.0f;
    private static final float BAND_SHARE = 0.26f;
    private static final float REFRACTION_SHARE = 0.1f;
    private static final Matrix3f FLAT = new Matrix3f();

    private static ShaderInstance paneShader;

    private UiPane() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX_COLOR),
                shader -> paneShader = shader);
    }

    public static boolean ready() {
        return paneShader != null;
    }

    public static void draw(GuiGraphics graphics, float x, float y, float width, float height,
                            float radius, float alpha, int top, int bottom, int texture) {
        if (paneShader == null || texture == 0 || width <= 0.0f || height <= 0.0f) return;

        float pixels = UiRender.pixelsPerUnit(graphics);
        float margin = flare() + EDGE_SLACK;
        bind(pixels, width, height, radius, top, bottom);
        centre(graphics, x + width / 2.0f, y + height / 2.0f);
        carry();
        quad(graphics.pose().last().pose(), x, y, width, height, margin, pixels, alpha, texture);
    }

    // WHY: центр панели раньше восстанавливался из gl_FragCoord и локальных координат, что верно только
    // WHY: при масштабе один к одному без поворота; в мировой позе он приходит уже спроецированным
    private static void centre(GuiGraphics graphics, float x, float y) {
        Window window = Minecraft.getInstance().getWindow();
        double gui = Math.max(1.0, window.getGuiScale());
        float deviceX = (float) (UiRender.screenX(graphics, x, y) * gui);
        float deviceY = (float) (window.getHeight() - UiRender.screenY(graphics, x, y) * gui);
        paneShader.safeGetUniform("PaneCentre").set(deviceX, deviceY);
    }

    // WHY: панель догорает в мировой позе, а сэмплит по координате снятия; без переноса стекло
    // WHY: показывает кусок кадра, прибитый к экрану, и рука игрока из него никуда не девается
    private static void carry() {
        UiPlane plane = UiBackdrop.carrier();
        paneShader.safeGetUniform("PaneWarp").set(plane == null ? FLAT : plane.warp());
    }

    private static float flare() {
        return UiGlassStyle.dissolve();
    }

    private static void bind(float pixels, float width, float height, float radius, int top, int bottom) {
        float corner = Math.min(radius, Math.min(width, height) * 0.5f);
        paneShader.safeGetUniform("PaneTop").set(red(top), green(top), blue(top), opacity(top));
        paneShader.safeGetUniform("PaneBottom").set(red(bottom), green(bottom), blue(bottom), opacity(bottom));
        paneShader.safeGetUniform("PaneShape").set(width * pixels * 0.5f, height * pixels * 0.5f,
                corner * pixels, density());
        float smallest = Math.min(width, height) * pixels;
        float thickness = Math.min(UiGlassStyle.band() * pixels, smallest * BAND_SHARE);
        paneShader.safeGetUniform("PaneLens").set(thickness, refraction(),
                UiGlassStyle.zoom(), UiGlassStyle.softness() * pixels);
        paneShader.safeGetUniform("PaneEdge").set(flare() * pixels,
                UiGlassStyle.dissolveChaos(), drift(), UiGlassStyle.shapePower(width, height, corner));
        paneShader.safeGetUniform("PaneGrade").set(UiGlassStyle.dispersion(),
                UiGlassStyle.saturation(), UiGlassStyle.brightness(), UiGlassStyle.bendProfile());
        paneShader.safeGetUniform("PaneFlow").set(UiGlassStyle.fresnelRange(),
                UiGlassStyle.fresnelHard(), UiGlassStyle.fresnelGlow(), UiGlassProbe.mode());
        paneShader.safeGetUniform("PaneBend").set(UiGlassStyle.bendAim(),
                UiGlassStyle.bendReach(), 0.0f, 0.0f);
    }

    private static float refraction() {
        return 1.0f + UiGlassStyle.pull() * REFRACTION_SHARE;
    }

    private static float density() {
        return UiBackdrop.active() ? UiGlassStyle.density() : 1.0f;
    }

    private static float drift() {
        return Util.getMillis() / DRIFT_SECONDS * UiGlassStyle.dissolveDrift();
    }

    private static void quad(Matrix4f matrix, float x, float y, float width, float height,
                             float margin, float pixels, float alpha, int texture) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> paneShader);
        RenderSystem.setShaderTexture(0, texture);

        float left = x - margin;
        float top = y - margin;
        float right = x + width + margin;
        float bottom = y + height + margin;
        float halfWidth = (width * 0.5f + margin) * pixels;
        float halfHeight = (height * 0.5f + margin) * pixels;

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(builder, matrix, left, bottom, -halfWidth, halfHeight, alpha);
        vertex(builder, matrix, right, bottom, halfWidth, halfHeight, alpha);
        vertex(builder, matrix, right, top, halfWidth, -halfHeight, alpha);
        vertex(builder, matrix, left, top, -halfWidth, -halfHeight, alpha);
        Tesselator.getInstance().end();

        RenderSystem.enableCull();
        UiRender.standardBlend();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, float x, float y,
                               float localX, float localY, float alpha) {
        builder.vertex(matrix, x, y, 0.0f).uv(localX, localY).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0f;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0f;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0f;
    }

    private static float opacity(int color) {
        return ((color >>> 24) & 0xFF) / 255.0f;
    }
}
