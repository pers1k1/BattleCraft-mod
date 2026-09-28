package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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

// WHY: размытие на переходах настоящее: содержимое рисуется в свой офскрин на прозрачном поле,
// WHY: а на место кладётся шейдером, который усредняет его по диску с мипмапов и режет по форме
// WHY: хозяина. В покое хозяин рисует мимо офскрина, и слой стоит что-то только на время анимации
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiVeil {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_veil");
    private static final int LEVELS = 6;
    private static final float MARGIN = 1.0f;
    private static final long IDLE_NANOS = 5_000_000_000L;

    private static ShaderInstance veilShader;
    private static TextureTarget layer;
    private static boolean held;
    private static boolean failed;
    private static int drawn;
    private static long usedAt;

    private UiVeil() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> veilShader = shader);
    }

    public static boolean begin(GuiGraphics graphics) {
        if (veilShader == null || held || failed) return false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return false;

        graphics.flush();
        drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            prepare(main.width, main.height);
            layer.bindWrite(true);
            clear();
        } catch (Throwable error) {
            failed = true;
            rebind(main);
            LogUtils.getLogger().warn("[battlecraft] veil off: {}", String.valueOf(error));
            return false;
        }
        held = true;
        usedAt = System.nanoTime();
        return true;
    }

    public static void end(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                           float blur, float alpha) {
        if (!held) return;

        graphics.flush();
        held = false;
        rebind(Minecraft.getInstance().getMainRenderTarget());
        GlStateManager._bindTexture(layer.getColorTextureId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._bindTexture(0);
        arm(graphics, x, y, width, height, Math.min(radius, Math.min(width, height) * 0.5f), blur, alpha);
        compose(graphics.pose().last().pose(), x - MARGIN, y - MARGIN, width + MARGIN * 2.0f,
                height + MARGIN * 2.0f);
    }

    // WHY: полноэкранный слой с мипмапами весит десяток мегабайт, а нужен только на время перехода
    public static void tidy() {
        if (layer == null || held || System.nanoTime() - usedAt < IDLE_NANOS) return;
        if (!RenderSystem.isOnRenderThread()) return;

        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        layer.destroyBuffers();
        layer = null;
        drawn = bound;
        rebind(Minecraft.getInstance().getMainRenderTarget());
    }

    private static void prepare(int width, int height) {
        if (layer != null && layer.width == width && layer.height == height) return;
        if (layer != null) layer.destroyBuffers();

        layer = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        layer.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._bindTexture(layer.getColorTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, LEVELS);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
    }

    private static void clear() {
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager._clearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    private static void rebind(RenderTarget main) {
        if (drawn == main.frameBufferId) {
            main.bindWrite(true);
            return;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, drawn);
        GlStateManager._viewport(0, 0, main.width, main.height);
    }

    private static void arm(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                            float blur, float alpha) {
        Window window = Minecraft.getInstance().getWindow();
        double gui = Math.max(1.0, window.getGuiScale());
        float pixels = UiRender.pixels(graphics);
        float middleX = x + width / 2.0f;
        float middleY = y + height / 2.0f;
        float centreX = (float) (UiRender.screenX(graphics, middleX, middleY) * gui);
        float centreY = (float) (window.getHeight() - UiRender.screenY(graphics, middleX, middleY) * gui);
        veilShader.safeGetUniform("VeilShape").set(centreX, centreY, width * pixels / 2.0f, height * pixels / 2.0f);
        veilShader.safeGetUniform("VeilCorner").set(radius * pixels, UiGlassStyle.shapePower(width, height, radius));
        veilShader.safeGetUniform("VeilBlur").set(blur * pixels, UiAnim.clamp01(alpha));
    }

    private static void compose(Matrix4f matrix, float x, float y, float width, float height) {
        RenderSystem.setShaderTexture(0, layer.getColorTextureId());
        UiRender.standardBlend();
        UiRender.ignoreDepth();
        RenderSystem.setShader(() -> veilShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, x, y + height, 0.0f).uv(0.0f, 1.0f).endVertex();
        builder.vertex(matrix, x + width, y + height, 0.0f).uv(1.0f, 1.0f).endVertex();
        builder.vertex(matrix, x + width, y, 0.0f).uv(1.0f, 0.0f).endVertex();
        builder.vertex(matrix, x, y, 0.0f).uv(0.0f, 0.0f).endVertex();
        Tesselator.getInstance().end();
        UiRender.resumeDepth();
        UiRender.standardBlend();
    }
}
