package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import org.lwjgl.opengl.GL30;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiDots {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_dots");
    private static final float DOT_UNITS = 3.0f;

    private static ShaderInstance dotsShader;
    private static TextureTarget stale;
    private static long lastFrame = -1L;
    private static float elapsed;

    private UiDots() {}

    public static final class Scene {
        private int picture;
        private float pictureWidth;
        private float pictureHeight;
        private int staleTexture;
        private float swap = 1.0f;
        private float assemble = 1.0f;
        private boolean dotted = true;
        private int ink = UiTheme.WHITE;
        private float whiten;
        private int backdrop = UiTheme.BLACK;
        private float pointerX = 0.5f;
        private float pointerY = 0.5f;
        private float zoom = 1.0f;
        private float haze;
        private float veil;
        private float dim;

        public void picture(int texture, float width, float height) {
            picture = texture;
            pictureWidth = width;
            pictureHeight = height;
        }

        public void stale(int texture) {
            staleTexture = texture;
        }

        public void progress(float swapShare, float assembleShare) {
            swap = UiAnim.clamp01(swapShare);
            assemble = UiAnim.clamp01(assembleShare);
        }

        public void look(boolean dots, int inkColor, float whitening, int backdropColor) {
            dotted = dots;
            ink = inkColor;
            whiten = whitening;
            backdrop = backdropColor;
        }

        public void pointer(float x, float y) {
            pointerX = x;
            pointerY = y;
        }

        public void mood(float zoomFactor, float hazeShare, float veilShare, float dimShare) {
            zoom = Math.max(1.0f, zoomFactor);
            haze = UiAnim.clamp01(hazeShare);
            veil = UiAnim.clamp01(veilShare);
            dim = UiAnim.clamp01(dimShare);
        }

        public boolean dotted() {
            return dotted;
        }
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> dotsShader = shader);
    }

    public static boolean ready() {
        return dotsShader != null;
    }

    public static boolean paint(GuiGraphics graphics, float width, float height, Scene scene) {
        if (dotsShader == null || width <= 0.0f || height <= 0.0f) return false;

        graphics.flush();
        arm(scene, width / height);
        RenderSystem.setShaderTexture(0, scene.picture);
        RenderSystem.setShaderTexture(1, scene.staleTexture);
        UiRender.standardBlend();
        RenderSystem.setShader(() -> dotsShader);
        quad(graphics.pose().last().pose(), width, height);
        RenderSystem.setShaderTexture(1, 0);
        return true;
    }

    private static void arm(Scene scene, float aspect) {
        dotsShader.safeGetUniform("Time").set(clock());
        dotsShader.safeGetUniform("Pointer").set(scene.pointerX, 1.0f - scene.pointerY);
        dotsShader.safeGetUniform("Pitch").set(pitch());
        dotsShader.safeGetUniform("Dotted").set(scene.dotted ? 1.0f : 0.0f);
        dotsShader.safeGetUniform("Source").set(scene.picture == 0 ? 0.0f : 1.0f, 1.0f, scene.swap, scene.assemble);
        cover("CoverA", scene.pictureWidth, scene.pictureHeight, aspect);
        dotsShader.safeGetUniform("CoverB").set(1.0f, -1.0f, 0.0f, 1.0f);
        dotsShader.safeGetUniform("Mood").set(scene.zoom, scene.haze, scene.veil, scene.dim);
        color("Ink", scene.ink, scene.whiten);
        color("Backdrop", scene.backdrop, 1.0f);
    }

    // WHY: обои кладутся «по заполнению» под настоящий экран при отрисовке, а не при импорте:
    // WHY: кеш хранит родные пропорции, поэтому 16:10 и 21:9 ничего не теряют
    private static void cover(String name, float pictureWidth, float pictureHeight, float screenAspect) {
        if (pictureWidth <= 0.0f || pictureHeight <= 0.0f) {
            dotsShader.safeGetUniform(name).set(1.0f, 1.0f, 0.0f, 0.0f);
            return;
        }
        float pictureAspect = pictureWidth / pictureHeight;
        float scaleX = pictureAspect > screenAspect ? screenAspect / pictureAspect : 1.0f;
        float scaleY = pictureAspect > screenAspect ? 1.0f : pictureAspect / screenAspect;
        dotsShader.safeGetUniform(name).set(scaleX, scaleY, (1.0f - scaleX) / 2.0f, (1.0f - scaleY) / 2.0f);
    }

    private static float pitch() {
        return (float) (DOT_UNITS * Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale()));
    }

    private static void color(String name, int value, float fourth) {
        dotsShader.safeGetUniform(name).set(((value >> 16) & 0xFF) / 255.0f, ((value >> 8) & 0xFF) / 255.0f,
                (value & 0xFF) / 255.0f, fourth);
    }

    private static void quad(Matrix4f matrix, float width, float height) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, 0.0f, height, 0.0f).uv(0.0f, 1.0f).endVertex();
        builder.vertex(matrix, width, height, 0.0f).uv(1.0f, 1.0f).endVertex();
        builder.vertex(matrix, width, 0.0f, 0.0f).uv(1.0f, 0.0f).endVertex();
        builder.vertex(matrix, 0.0f, 0.0f, 0.0f).uv(0.0f, 0.0f).endVertex();
        Tesselator.getInstance().end();
    }

    // WHY: смена обоев любых видов идёт от снимка прошлого кадра, а не от прошлого источника:
    // WHY: так одинаково уходят планета, свои обои, смена тона и выключение точек. Привязка
    // WHY: возвращается та, что была: смена может прийти, пока экран рисует в офскрин стадии.
    // WHY: Её надо снять до создания цели: TextureTarget при создании чистит себя и отвязывает
    // WHY: кадровый буфер в ноль, и остаток кадра уходил мимо главной цели. Снимок берётся с той
    // WHY: цели, куда фон только что лёг: под проявлением экрана это офскрин стадии размером с
    // WHY: главную, а главная в этот момент ещё пуста, и смена начиналась бы с чёрного кадра
    public static int freeze() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return 0;

        int drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int painted = drawn != 0 ? drawn : main.frameBufferId;
        if (stale == null || stale.width != main.width || stale.height != main.height) {
            if (stale != null) stale.destroyBuffers();
            stale = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            stale.setFilterMode(GL11.GL_NEAREST);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, painted);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, stale.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, stale.width, stale.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawn);
        return stale.getColorTextureId();
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
