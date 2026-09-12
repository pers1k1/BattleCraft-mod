package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiGlow {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_glow");
    private static final Matrix4f FLAT = new Matrix4f();

    private static final int LEVELS = 3;
    private static final int TOP_SHRINK = 2;
    private static final float DOWN_REACH = 1.5f;
    private static final float UP_REACH = 1.15f;
    private static final float FAINT = 0.01f;

    private static ShaderInstance glowShader;
    private static TextureTarget canvas;
    private static TextureTarget[] pyramid;
    private static boolean failed;
    private static boolean reported;

    private UiGlow() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> glowShader = shader);
    }

    public static boolean ready() {
        return glowShader != null && UiBlur.ready() && !failed;
    }

    // WHY: восемь смещённых копий глифа дают ступенчатый ореол на крупном кегле, поэтому свет
    // WHY: снимается отдельным полотном, размывается пирамидой и кладётся под текст сложением
    public static void halo(GuiGraphics graphics, float strength, Runnable content) {
        if (!ready()) return;

        float[] tone = RenderSystem.getShaderColor().clone();
        float force = strength * tone[3];
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (force <= FAINT || main.width <= 0 || main.height <= 0) return;

        graphics.flush();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int host = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            build(main.width, main.height);
            trace(graphics, content);
            spread();
            project(host, main, force);
        } catch (Throwable error) {
            failed = true;
            release();
            report(error);
        } finally {
            rebind(host, main);
            restore(depth, tone);
        }
    }

    // WHY: свет может сниматься и внутри офскрина проявления, поэтому кадр возвращается тому
    // WHY: буферу, что был занят до прохода, а не главному таргету
    private static void rebind(int host, RenderTarget main) {
        if (host == main.frameBufferId) {
            main.bindWrite(true);
            return;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, host);
        GlStateManager._viewport(0, 0, main.width, main.height);
    }

    // WHY: слой мог гаснуть общим цветом шейдера, и без возврата этого цвета всё, что рисуется
    // WHY: после свечения, остаётся непрозрачным: кнопки мастера доживали до конца анимации ухода
    private static void restore(boolean depth, float[] tone) {
        UiRender.standardBlend();
        RenderSystem.setShaderColor(tone[0], tone[1], tone[2], tone[3]);
        if (depth) {
            RenderSystem.enableDepthTest();
            return;
        }
        RenderSystem.disableDepthTest();
    }

    // WHY: полотно снимает полную яркость букв, а прозрачность слоя уже учтена силой композита
    private static void trace(GuiGraphics graphics, Runnable content) {
        canvas.bindWrite(true);
        wipe();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        content.run();
        graphics.flush();
    }

    private static void spread() {
        reduce(canvas, pyramid[0]);
        for (int level = 1; level < pyramid.length; level++) {
            sweep(pyramid[level - 1], pyramid[level], UiBlur.shrinking(), DOWN_REACH);
        }
        for (int level = pyramid.length - 1; level > 0; level--) {
            sweep(pyramid[level], pyramid[level - 1], UiBlur.growing(), UP_REACH);
        }
    }

    // WHY: ShaderInstance.apply перед отрисовкой сам кладёт в ColorModulator текущий цвет шейдера,
    // WHY: поэтому сила света задаётся только им: выставленный вручную юниформ затирается,
    // WHY: и ореол складывается с кадром на полную, сколько бы ни просили
    private static void project(int host, RenderTarget main, float strength) {
        rebind(host, main);

        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        flatten();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, strength);
        RenderSystem.setShader(() -> glowShader);
        RenderSystem.setShaderTexture(0, pyramid[0].getColorTextureId());
        UiQuad.screen();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
    }

    private static void sweep(TextureTarget from, TextureTarget to, Supplier<ShaderInstance> kernel, float reach) {
        to.bindWrite(true);

        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        UiBlur.useOffset(reach / from.width, reach / from.height);
        flatten();
        UiRender.standardBlend();
        RenderSystem.setShader(kernel);
        RenderSystem.setShaderTexture(0, from.getColorTextureId());
        UiQuad.screen();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
    }

    private static void flatten() {
        RenderSystem.setProjectionMatrix(FLAT, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void reduce(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }

    // WHY: полотно свечения непрозрачно и чёрно, поэтому размытие не тянет в ореол пустую альфу,
    // WHY: а сложение с кадром берёт ровно ту яркость, что дали буквы
    private static void wipe() {
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager._depthMask(true);
        GlStateManager._clearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GlStateManager._clearDepth(1.0);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    private static void build(int width, int height) {
        int topWidth = Math.max(1, width / TOP_SHRINK);
        int topHeight = Math.max(1, height / TOP_SHRINK);
        if (canvas != null && canvas.width == width && canvas.height == height
                && pyramid != null && pyramid[0].width == topWidth) return;

        release();
        canvas = surface(width, height, true);
        pyramid = new TextureTarget[LEVELS];
        for (int level = 0; level < LEVELS; level++) {
            int shrink = 1 << level;
            pyramid[level] = surface(Math.max(1, topWidth / shrink), Math.max(1, topHeight / shrink), false);
        }
    }

    private static TextureTarget surface(int width, int height, boolean depth) {
        TextureTarget created = new TextureTarget(width, height, depth, Minecraft.ON_OSX);
        created.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._bindTexture(created.getColorTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        return created;
    }

    private static void release() {
        canvas = discard(canvas);
        if (pyramid == null) return;

        for (int level = 0; level < pyramid.length; level++) {
            pyramid[level] = discard(pyramid[level]);
        }
        pyramid = null;
    }

    private static TextureTarget discard(TextureTarget owner) {
        if (owner == null) return null;

        try {
            owner.destroyBuffers();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void report(Throwable error) {
        if (reported) return;
        reported = true;
        LogUtils.getLogger().warn("[battlecraft] text glow off: {}", String.valueOf(error));
    }
}
