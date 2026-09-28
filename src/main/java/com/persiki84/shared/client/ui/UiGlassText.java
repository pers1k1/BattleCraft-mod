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
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

// WHY: цифры из стекла, как вариант «Стекло» экрана блокировки iOS 26. Форма - поле расстояний
// WHY: из UiDigitSheet: кромка ровно в пиксель на любом масштабе, скат и нормаль из того же поля.
// WHY: Кадр под цифрами копируется с мипмапами, и внутри глифа шейдер показывает его размытым,
// WHY: высветленным и сдвинутым по нормали у кромки; за кромкой кадр не трогается. Доли ниже - от
// WHY: высоты цифры, сняты стендом с рефа lockscreen_08 (фон восстановлен из кадров, где цифр нет):
// WHY: размытие с сигмой около 0.2 высоты цифры (диск радиуса 0.36 на обычной ступени), к фону добавлено 0.25 белого
// WHY: и 0.31 среднего цвета кадра, который даёт верхний уровень мипмапов
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiGlassText {
    public interface Painter {
        void paint(Glyphs glyphs);
    }

    // WHY: вид цифр задаётся долями высоты цифры, чтобы переход между ступенями шёл без
    // WHY: пересборки поля: жирность - сдвиг порога поля, размытость - радиус выборки кадра,
    // WHY: solid - доля сплошной заливки поверх стекла, soften - размытие самой формы
    public static final class Style {
        private float solid;
        private int solidColor;
        private float weight;
        private float frost;
        private float soften;

        public void set(float solid, int solidColor, float weight, float frost, float soften) {
            this.solid = solid;
            this.solidColor = solidColor;
            this.weight = weight;
            this.frost = frost;
            this.soften = soften;
        }
    }

    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_glasstext");
    private static final float BEVEL_SHARE = 0.07f;
    private static final float BEND_SHARE = 0.03f;
    private static final float RIM_SHARE = 0.008f;
    private static final float LIFT = 0.25f;
    private static final float BLUR_LEVEL_BIAS = 1.2f;
    private static final float AMBIENT = 0.31f;
    private static final int FRAME_LEVELS = 8;
    private static final float INVISIBLE = 0.004f;
    private static final float OPAQUE = 0.999f;
    private static final Glyphs GLYPHS = new Glyphs();

    private static ShaderInstance glassShader;
    private static TextureTarget frame;

    private UiGlassText() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        UiDigitAtlas.request(event.getResourceProvider());
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX_COLOR),
                shader -> glassShader = shader);
    }

    public static boolean ready() {
        return glassShader != null && UiDigitAtlas.sheet() != null;
    }

    public static boolean unavailable() {
        return UiDigitAtlas.failed();
    }

    public static float advance(char glyph, float weight) {
        UiDigitSheet sheet = UiDigitAtlas.sheet();
        int index = UiDigitSheet.indexOf(glyph);
        return sheet == null || index < 0 ? 0.0f : sheet.advance(index) + 2.0f * weight;
    }

    public static void draw(GuiGraphics graphics, float alpha, Style style, Painter painter) {
        UiDigitSheet sheet = UiDigitAtlas.sheet();
        if (glassShader == null || sheet == null || alpha <= INVISIBLE) return;
        graphics.flush();
        if (style.solid < OPAQUE) copyFrame();
        compose(graphics, sheet, alpha, style, painter);
    }

    private static void copyFrame() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        prepare(main.width, main.height);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawn);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, frame.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, frame.width, frame.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._bindTexture(frame.getColorTextureId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._bindTexture(0);
        rebind(drawn, main);
    }

    private static void prepare(int width, int height) {
        if (frame != null && frame.width == width && frame.height == height) return;
        if (frame != null) frame.destroyBuffers();
        frame = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        frame.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._bindTexture(frame.getColorTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, FRAME_LEVELS);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._bindTexture(0);
    }

    // WHY: копия кадра с мипмапами весит как сам кадр с третью сверху, а часы живут только на
    // WHY: рабочем столе: в игре её держать незачем, при возврате она соберётся заново. Экран может
    // WHY: уйти посреди своего кадра (запуск из Dock), а удаление буфера отвязывает запись в кадр
    public static void release() {
        if (frame == null || !RenderSystem.isOnRenderThread()) return;
        int drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        frame.destroyBuffers();
        frame = null;
        rebind(drawn, Minecraft.getInstance().getMainRenderTarget());
    }

    private static void rebind(int drawn, RenderTarget main) {
        if (drawn == main.frameBufferId) {
            main.bindWrite(true);
            return;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, drawn);
        GlStateManager._viewport(0, 0, main.width, main.height);
    }

    private static void compose(GuiGraphics graphics, UiDigitSheet sheet, float alpha, Style style, Painter painter) {
        dress(sheet, style);
        RenderSystem.setShaderTexture(0, UiDigitAtlas.texture());
        RenderSystem.setShaderTexture(1, frame == null ? 0 : frame.getColorTextureId());
        UiRender.standardBlend();
        UiRender.ignoreDepth();
        RenderSystem.setShader(() -> glassShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        GLYPHS.open(builder, graphics.pose().last().pose(), sheet, alpha, style.weight);
        painter.paint(GLYPHS);
        GLYPHS.close();
        Tesselator.getInstance().end();
        UiRender.resumeDepth();
        RenderSystem.setShaderTexture(1, 0);
    }

    private static void dress(UiDigitSheet sheet, Style style) {
        glassShader.safeGetUniform("Atlas").set(sheet.texelsPerFigure(), UiDigitSheet.SPREAD_TEXELS, style.weight,
                style.soften);
        glassShader.safeGetUniform("Relief").set(BEVEL_SHARE, BEND_SHARE, RIM_SHARE, 0.0f);
        glassShader.safeGetUniform("Frost").set(style.frost, LIFT, BLUR_LEVEL_BIAS, AMBIENT);
        glassShader.safeGetUniform("Solid").set(((style.solidColor >> 16) & 0xFF) / 255.0f,
                ((style.solidColor >> 8) & 0xFF) / 255.0f, (style.solidColor & 0xFF) / 255.0f, style.solid);
    }

    public static final class Glyphs {
        private BufferBuilder builder;
        private Matrix4f pose;
        private UiDigitSheet sheet;
        private float alpha;
        private float weight;

        private Glyphs() {}

        private void open(BufferBuilder builder, Matrix4f pose, UiDigitSheet sheet, float alpha, float weight) {
            this.builder = builder;
            this.pose = pose;
            this.sheet = sheet;
            this.alpha = alpha;
            this.weight = weight;
        }

        private void close() {
            builder = null;
            pose = null;
        }

        // WHY: blur - размытие формы этого глифа на смене цифры, едет в красном канале цвета вершины
        public void glyph(char glyph, float left, float top, float figure, float opacity, float blur) {
            int index = UiDigitSheet.indexOf(glyph);
            float shown = opacity * alpha;
            if (index < 0 || shown <= INVISIBLE) return;
            float x = left + (sheet.penOffset(index) + weight) * figure;
            float y = top + sheet.topOffset() * figure;
            float right = x + sheet.cellSpan(index) * figure;
            float bottom = y + sheet.rowSpan() * figure;
            float u0 = sheet.cellLeft(index) / (float) sheet.width();
            float u1 = (sheet.cellLeft(index) + sheet.cellWidth(index)) / (float) sheet.width();
            corner(x, bottom, u0, 1.0f, blur, shown);
            corner(right, bottom, u1, 1.0f, blur, shown);
            corner(right, y, u1, 0.0f, blur, shown);
            corner(x, y, u0, 0.0f, blur, shown);
        }

        private void corner(float x, float y, float u, float v, float blur, float shown) {
            builder.vertex(pose, x, y, 0.0f).uv(u, v).color(UiAnim.clamp01(blur), 1.0f, 1.0f, shown).endVertex();
        }
    }
}
