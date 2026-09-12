package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public final class UiStage {
    private static final Surface SCREEN = new Surface();
    private static final Surface WINDOW = new Surface();

    private static boolean reported;

    private UiStage() {}

    // WHY: сцена одна на всех, но каждый её хозяин заполняет её заново прямо перед композитом,
    // WHY: поэтому проявление нового экрана и горение закрытых делят её последовательно в одном кадре
    public static boolean begin() {
        return SCREEN.begin(null, null, 0.0f, 0.0f, 0.0f);
    }

    // WHY: живому проходу горения нужна та же сцена каждый кадр, но с переносом кадра на плоскость,
    // WHY: а расколу - ещё и через поле осколков: под улетевшей ячейкой лежит уже её новое место
    public static boolean beginBurn(UiPlane carrier, UiShards shards, float phase,
                                    float width, float height) {
        return SCREEN.begin(carrier, shards, phase, width, height);
    }

    public static void end() {
        SCREEN.end();
    }

    public static int texture() {
        return SCREEN.texture();
    }

    // WHY: вложить один офскрин в другой нельзя, поэтому окно рисуется плоско, пока идёт проход экрана
    public static boolean beginWindow(UiShards shards, float phase, float width, float height) {
        if (SCREEN.held) return false;
        return WINDOW.begin(null, shards, phase, width, height);
    }

    public static void endWindow() {
        WINDOW.end();
    }

    public static int windowTexture() {
        return WINDOW.texture();
    }

    private static final class Surface {
        private TextureTarget target;
        private boolean failed;
        private boolean held;

        private boolean begin(UiPlane carrier, UiShards shards, float phase,
                              float width, float height) {
            if (failed) return false;

            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (main.width <= 0 || main.height <= 0) return false;

            try {
                build(main.width, main.height);
                target.bindWrite(true);
                fill(main, carrier, shards, phase, width, height);
                clearCoverage();
            } catch (Throwable error) {
                failed = true;
                release();
                report(error);
                return false;
            }
            held = true;
            return true;
        }

        private void end() {
            if (!held) return;
            held = false;
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }

        private int texture() {
            return target == null ? 0 : target.getColorTextureId();
        }

        private void release() {
            held = false;
            if (target == null) return;
            try {
                target.destroyBuffers();
            } catch (Throwable ignored) {
            }
            target = null;
        }

        private void build(int width, int height) {
            if (target != null && target.width == width && target.height == height) return;

            release();
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
            GlStateManager._bindTexture(target.getColorTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            deepen(width, height);
            GlStateManager._bindTexture(0);
        }

        // WHY: интерфейс пишет в стадию квадрат своей альфы, и у стекла с альфой 0.06 он в восьми битах обнуляется
        private void deepen(int width, int height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) return;

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            LogUtils.getLogger().warn("[battlecraft] menu stage kept 8 bit: 16 bit target incomplete");
        }

        private void fill(RenderTarget main, UiPlane carrier, UiShards shards, float phase,
                          float width, float height) {
            if (UiCarry.paint(main.getColorTextureId(), carrier, shards, phase, width, height)) return;
            copy(main);
        }

        private void copy(RenderTarget main) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, target.width, target.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            target.bindWrite(true);
        }

        // WHY: без своей глубины ItemRenderer.renderStatic кладёт грани в порядке отправки и модель
        // WHY: товара разваливается; главный таргет глубину имеет, стадия обязана быть такой же
        private void clearCoverage() {
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager._colorMask(false, false, false, true);
            GlStateManager._depthMask(true);
            GlStateManager._clearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GlStateManager._clearDepth(1.0);
            GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            GlStateManager._colorMask(true, true, true, true);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void report(Throwable error) {
        if (reported) return;
        reported = true;
        LogUtils.getLogger().warn("[battlecraft] menu stage off: {}", String.valueOf(error));
    }
}
