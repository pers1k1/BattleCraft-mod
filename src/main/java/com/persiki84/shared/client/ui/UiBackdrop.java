package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.function.Supplier;

public final class UiBackdrop {
    private static final int MAX_LEVELS = 6;
    private static final int MAX_SHRINK = 4;
    private static final float MAX_DOWN_REACH = 2.0f;
    private static final float MAX_UP_REACH = 1.2f;
    private static final float PASS_EPSILON = 0.001f;
    private static final int MAX_LAYERS = 3;

    private static final Matrix4f FLAT = new Matrix4f();

    private static TextureTarget[] pyramid;
    private static TextureTarget polish;
    private static TextureTarget blurred;
    private static TextureTarget scattered;
    private static TextureTarget kept;

    private static boolean enabled = true;
    private static boolean failed;
    private static boolean captured;
    private static boolean holding;
    private static int heldLayers;
    private static boolean reported;
    private static long stamp = -1L;
    private static int layers;
    private static int substitute;

    private UiBackdrop() {}

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            suspend();
        }
    }

    public static boolean active() {
        return captured;
    }

    public static void suspend() {
        captured = false;
        stamp = -1L;
        layers = 0;
        substitute = 0;
    }

    public static void capture() {
        if (captured && stamp == UiFrame.frame()) return;

        captured = false;
        layers = 0;
        substitute = 0;
        if (!enabled || failed || !wanted()) return;

        take(Minecraft.getInstance().getMainRenderTarget().frameBufferId);
        stamp = UiFrame.frame();
        if (!captured && !reported) {
            report("capture produced no texture");
        }
    }

    // WHY: снимок один на кадр, поэтому стекло поверх стекла преломляло бы кадр без нижней панели
    public static void restage() {
        if (!enabled || failed || !captured || layers >= MAX_LAYERS) return;

        substitute = 0;
        layers++;
        take(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
    }

    // WHY: снимок со сцены уже лежит в координатах плоской отрисовки, и переносить сэмпл по мировой
    // WHY: позе для него значит читать чужое место кадра; перенос нужен только снимку с самого кадра
    public static UiPlane carrier() {
        return layers > 0 || substitute != 0 ? null : UiPlane.sampling();
    }

    // WHY: снимок для раскола уже перенесён через поле осколков, то есть лежит в координатах плоской
    // WHY: отрисовки: второй раз переносить его гомографией значит читать чужое место кадра
    public static boolean scatter(UiPlane carrier, UiShards shards, float phase,
                                  float width, float height) {
        substitute = 0;
        if (!enabled || failed || !captured || blurred == null || shards == null) return false;

        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            TextureTarget into = spare();
            into.bindWrite(true);
            if (UiCarry.paint(blurred.getColorTextureId(), carrier, shards, phase, width, height)) {
                substitute = into.getColorTextureId();
            }
        } catch (Throwable error) {
            failed = true;
            release();
            report("scatter failed: " + error);
        } finally {
            rebind(bound);
        }
        return substitute != 0;
    }

    public static void plain() {
        substitute = 0;
    }

    // WHY: снимок один на кадр и общий с открытым экраном, а закрытый перерисовывается живьём: его
    // WHY: restage переписывает пирамиду кадром офскрин-стадии, и открытый преломлял бы догорающего,
    // WHY: то есть темнел. Корень откладывается в свою цель одним блитом и возвращается после прохода
    public static void hold() {
        holding = false;
        if (!enabled || failed || !captured || blurred == null) return;
        if (blurred == kept) {
            holding = true;
            return;
        }

        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            kept = keeper();
            reduce(blurred.frameBufferId, blurred.width, blurred.height, kept);
            heldLayers = layers;
            holding = true;
        } catch (Throwable error) {
            failed = true;
            release();
            report("hold failed: " + error);
        } finally {
            rebind(bound);
        }
    }

    public static void resume() {
        if (!holding) return;

        holding = false;
        if (failed || kept == null) return;
        blurred = kept;
        layers = heldLayers;
        captured = true;
    }

    private static TextureTarget keeper() {
        if (kept != null && kept.width == blurred.width && kept.height == blurred.height) return kept;

        kept = discard(kept);
        return surface(blurred.width, blurred.height);
    }

    private static TextureTarget spare() {
        if (scattered != null && scattered.width == blurred.width && scattered.height == blurred.height) {
            return scattered;
        }
        scattered = discard(scattered);
        scattered = surface(blurred.width, blurred.height);
        return scattered;
    }

    private static void take(int source) {
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        float[] tone = RenderSystem.getShaderColor().clone();
        try {
            snapshot(source);
        } catch (Throwable error) {
            failed = true;
            release();
            report("capture failed: " + error);
        } finally {
            restoreState(blending, depth, tone);
            rebind(source);
        }
    }

    private static void rebind(int target) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (target == main.frameBufferId) {
            main.bindWrite(true);
            return;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
        GlStateManager._viewport(0, 0, main.width, main.height);
    }

    // WHY: проходы блюра гасят блендинг, включают тест глубины и правят цвет шейдера, и без возврата
    // WHY: следующий слой либо чёрный, либо не подхватывает прозрачность уходящего экрана
    private static void restoreState(boolean blending, boolean depth, float[] tone) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(tone[0], tone[1], tone[2], tone[3]);
        if (blending) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        if (depth) {
            RenderSystem.enableDepthTest();
            return;
        }
        RenderSystem.disableDepthTest();
    }

    private static boolean wanted() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return true;
        return !mc.options.hideGui && mc.player != null;
    }

    public static String diagnostics() {
        return "enabled=" + enabled + " failed=" + failed + " captured=" + captured
                + " levels=" + levels()
                + " blur=" + (blurred == null ? "null" : blurred.width + "x" + blurred.height);
    }

    private static void report(String message) {
        if (reported) return;
        reported = true;
        LogUtils.getLogger().warn("[battlecraft] glass refraction off: {} ({})", message, diagnostics());
    }

    // WHY: подменный снимок собран из того же blurred, что стоял бы здесь, только перенесённый через
    // WHY: поле осколков, поэтому он главнее и при поднятых слоях; снимает его сам restage
    public static int texture() {
        if (substitute != 0) return substitute;
        return captured && blurred != null ? blurred.getColorTextureId() : 0;
    }

    public static void refract(GuiGraphics graphics, float x, float y, float width, float height, float radius, float alpha) {
        int glass = texture();
        if (glass == 0 || alpha <= 0.02f) return;
        UiRender.refracted(graphics, x, y, width, height, radius, glass, glass, alpha);
    }

    private static void snapshot(int source) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0 || !UiBlur.ready()) return;

        buildPyramid(main.width, main.height, levels());
        reduce(source, main.width, main.height, pyramid[0]);
        opaque(pyramid[0]);

        for (int level = 1; level < pyramid.length; level++) {
            sweep(pyramid[level - 1], pyramid[level], UiBlur.shrinking(),
                    Math.min(MAX_DOWN_REACH, UiGlassStyle.blurDown()));
        }
        for (int level = pyramid.length - 1; level > 0; level--) {
            sweep(pyramid[level], pyramid[level - 1], UiBlur.growing(),
                    Math.min(MAX_UP_REACH, UiGlassStyle.blurUp()));
        }
        blurred = polished();
        captured = true;
    }

    private static TextureTarget polished() {
        float reach = UiGlassStyle.blurPolish();
        if (reach <= PASS_EPSILON) return pyramid[0];

        sweep(pyramid[0], polish, UiBlur.shrinking(), Math.min(MAX_DOWN_REACH, reach));
        return polish;
    }

    private static int shrink() {
        return Math.max(1, Math.min(MAX_SHRINK, UiGlassStyle.blurShrink()));
    }

    private static int levels() {
        return Math.max(1, Math.min(MAX_LEVELS, UiGlassStyle.blurLevels()));
    }

    private static void buildPyramid(int width, int height, int levels) {
        int topWidth = Math.max(1, width / shrink());
        int topHeight = Math.max(1, height / shrink());
        if (pyramid != null && pyramid.length == levels
                && pyramid[0].width == topWidth && pyramid[0].height == topHeight) return;

        release();
        pyramid = new TextureTarget[levels];
        for (int level = 0; level < levels; level++) {
            int shrink = 1 << level;
            pyramid[level] = surface(Math.max(1, topWidth / shrink), Math.max(1, topHeight / shrink));
        }
        polish = surface(topWidth, topHeight);
    }

    private static TextureTarget surface(int width, int height) {
        TextureTarget created = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        created.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._bindTexture(created.getColorTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        opaque(created);
        return created;
    }

    private static void reduce(int source, int width, int height, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, to.width, to.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }

    private static void opaque(RenderTarget owner) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, owner.frameBufferId);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager._colorMask(false, false, false, true);
        GlStateManager._clearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        GlStateManager._colorMask(true, true, true, true);
        if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    private static void sweep(TextureTarget from, TextureTarget to, Supplier<ShaderInstance> kernel, float reach) {
        to.bindWrite(true);

        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();

        UiBlur.useOffset(reach / from.width, reach / from.height);
        beginSweep(from, kernel);
        UiQuad.screen();
        endSweep(projection, sorting);
    }

    private static void beginSweep(TextureTarget from, Supplier<ShaderInstance> kernel) {
        RenderSystem.setProjectionMatrix(FLAT, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableDepthTest();
        UiRender.standardBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(kernel);
        RenderSystem.setShaderTexture(0, from.getColorTextureId());
    }

    private static void endSweep(Matrix4f projection, VertexSorting sorting) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
    }

    private static void release() {
        captured = false;
        holding = false;
        substitute = 0;
        blurred = null;
        kept = discard(kept);
        scattered = discard(scattered);
        polish = discard(polish);
        if (pyramid == null) return;
        for (int level = 0; level < pyramid.length; level++) {
            pyramid[level] = discard(pyramid[level]);
        }
        pyramid = null;
    }

    private static TextureTarget discard(TextureTarget owner) {
        if (owner != null) {
            try {
                owner.destroyBuffers();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
