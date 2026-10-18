package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.ByteBuffer;

// WHY: уход инея размывал стадию диском из сорока одного отсчёта по полному кадру, каждый со своим
// WHY: поворотом и своими log, sqrt, sin и cos: на 2560x1440 это 151 млн чтений шестнадцатибитной
// WHY: текстуры вразброс за кадр, на стенде с GTX 1660 Super 2.2 мс на первых кадрах и до 21 мс к концу
// WHY: ухода (на 3840x2160 до 72 мс), когда сигма дорастает до 61 пикселя и отсчёты перестают попадать
// WHY: в кэш. Здесь тот же гаусс собирается на уменьшенной копии: точный интеграл по квадрату два на
// WHY: два пикселя, сжатия биномом до уровня, где сигма от двух до четырёх текселей, и раздельный гаусс;
// WHY: композит читает итог одним билинейным отсчётом. Дисперсия ящика, сжатий и билинейного чтения
// WHY: вычитается из заданной, поэтому итоговая сигма та же, что снята с образца. Вместе с композитом
// WHY: это 0.4-0.7 мс за кадр на 2560x1440 и 0.9-1.5 мс на 3840x2160, от сигмы почти не зависит; первые
// WHY: кадры с сигмой меньше 1.6 пикселя идут по полному кадру и стоят 1.0-1.2 и 2.2-2.6 мс соответственно
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiFrost {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_frost");

    private static final float SIGMA_SHARE = 0.0237f;
    private static final float DRIFT = 1.4f;
    private static final float SHARP = 0.35f;
    private static final float TENT = 1.0f / 6.0f;
    private static final float BOX_VARIANCE = 1.0f / 3.0f;
    private static final float PIXEL_VARIANCE = 1.0f / 12.0f;
    private static final float SHRINK_VARIANCE = 0.25f;
    private static final float LEVEL_SPREAD = 2.0f;
    private static final float REACH_SIGMAS = 3.0f;
    private static final int MAX_REACH = 16;
    private static final int MAX_LEVEL = 7;
    private static final float LEVEL_ONE_FROM = 1.6f;
    private static final int MATCH_STEPS = 20;

    private static final float PASS_STAGE = 0.0f;
    private static final float PASS_LEVEL = 1.0f;
    private static final float PASS_LINE = 2.0f;
    private static final float PASS_PIXEL = 3.0f;
    private static final long IDLE_NANOS = 20_000_000_000L;
    private static final int ERROR_DRAIN = 16;

    private static final Level FIRST = new Level();
    private static final Level SECOND = new Level();

    private static ShaderInstance frostShader;
    private static AbstractUniform sourceUniform;
    private static AbstractUniform passUniform;
    private static AbstractUniform stepUniform;
    private static AbstractUniform spreadUniform;

    private static final int[] VIEWPORT = new int[4];

    private static boolean failed;
    private static long usedAt;
    private static int drawn;
    private static int read;
    private static int unpack;
    private static int sampled;
    private static ShaderInstance shaderBefore;
    private static boolean scissored;
    private static boolean blended;
    private static boolean culled;
    private static boolean depthTested;
    private static boolean depthWritten;

    private static int levelWidth;
    private static int levelHeight;
    private static int scale;
    private static float spread;
    private static int reach;
    private static float share;
    private static Level result;
    private static int heldSecond;

    private UiFrost() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION),
                    UiFrost::adopt);
        } catch (IOException error) {
            LogUtils.getLogger().warn("[battlecraft] frost blur off: {}", String.valueOf(error));
        }
    }

    private static void adopt(ShaderInstance shader) {
        frostShader = shader;
        sourceUniform = shader.safeGetUniform("FrostSource");
        passUniform = shader.safeGetUniform("FrostPass");
        stepUniform = shader.safeGetUniform("FrostStep");
        spreadUniform = shader.safeGetUniform("FrostSpread");
    }

    private static float sigma(float phase, int width) {
        return SIGMA_SHARE * width * (float) Math.pow(UiAnim.clamp01(phase), DRIFT);
    }

    // WHY: стадия читается до композита, поэтому привязка кадра и окно вывода возвращаются ровно
    // WHY: такими, какими были: уход в мировой позе и окно палитры рисуются не обязательно в главный кадр
    static boolean soften(int stage, float phase) {
        result = null;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (frostShader == null || failed || stage == 0 || main.width <= 0 || main.height <= 0) return false;

        float sigma = sigma(phase, main.width);
        if (sigma < SHARP) return false;

        plan(sigma, main.width, main.height);
        drainErrors();
        remember();
        try {
            run(stage, main.width, main.height);
        } catch (Throwable error) {
            fail(String.valueOf(error));
        } finally {
            restore();
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) fail("GL error 0x" + Integer.toHexString(error));
        usedAt = System.nanoTime();
        return result != null;
    }

    // WHY: чужие ошибки GL, оставленные до прохода, иначе приписались бы ему и выключили бы размытие
    private static void drainErrors() {
        for (int i = 0; i < ERROR_DRAIN; i++) {
            if (GL11.glGetError() == GL11.GL_NO_ERROR) return;
        }
    }

    static void aim(AbstractUniform span, AbstractUniform mixed) {
        if (result == null) {
            span.set(0.0f, 0.0f, 0.0f, 0.0f);
            mixed.set(1.0f);
            return;
        }
        heldSecond = RenderSystem.getShaderTexture(1);
        RenderSystem.setShaderTexture(1, result.target.getColorTextureId());
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        span.set((float) main.width / scale, (float) main.height / scale, (float) levelWidth, (float) levelHeight);
        mixed.set(share);
    }

    // WHY: второй слот у ванили занят текстурой наложения, и чужой проход после композита ждёт её там
    static void release() {
        if (result != null) RenderSystem.setShaderTexture(1, heldSecond);
        result = null;
    }

    // WHY: две цели размером в половину кадра весят у 2560x1440 почти пятнадцать мегабайт, в полный кадр
    // WHY: шестьдесят, а нужны только на время ухода; пересоздание на первом кадре следующего ухода
    // WHY: дешевле кадра
    public static void tidy() {
        if (FIRST.target == null || System.nanoTime() - usedAt < IDLE_NANOS) return;
        if (!RenderSystem.isOnRenderThread()) return;

        int boundDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int boundRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        FIRST.release();
        SECOND.release();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, boundDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, boundRead);
        GlStateManager._bindTexture(texture);
    }

    // WHY: уровень берётся самый мелкий, где на сигму ещё приходится не меньше двух текселей: при одном
    // WHY: расхождение с плотным диском на кромке доходило до 12.6 уровня против 7.1. Первый уровень это
    // WHY: ящик два на два пикселя, дисперсия 1/3; каждое следующее сжатие добавляет 0.75 текселя
    // WHY: источника, то есть (4^L - 4) / 4 пикселя к уровню L, и ещё 1/6 текселя даёт билинейное чтение.
    // WHY: Если и этого больше заданного, недостачу добирает смесь с резким средним по пикселю (1/12)
    private static void plan(float sigma, int width, int height) {
        int level = levelFor(sigma);
        scale = 1 << level;
        float area = (float) scale * scale;
        float wanted = sigma * sigma;
        float inherent = level == 0 ? PIXEL_VARIANCE : BOX_VARIANCE + SHRINK_VARIANCE * (area - 4.0f) + area * TENT;
        float left = (wanted - inherent) / area;
        float exact = (float) Math.sqrt(Math.max(0.0f, left));
        reach = Math.min(MAX_REACH, (int) Math.ceil(REACH_SIGMAS * exact));
        spread = matched(exact, reach);
        share = left >= 0.0f ? 1.0f : Math.max(0.0f, (wanted - PIXEL_VARIANCE) / (inherent - PIXEL_VARIANCE));

        levelWidth = width;
        levelHeight = height;
        for (int i = 0; i < level; i++) {
            levelWidth = (levelWidth + 1) / 2;
            levelHeight = (levelHeight + 1) / 2;
        }
    }

    // WHY: у сигмы около пикселя половина кадра сама размывает на пиксель и кладёт штрих на сетку два на
    // WHY: два: два штриха буквы через пиксель сливались в плашку или нет смотря по тому, где легла строка,
    // WHY: и на стенде расхождение с плотным диском доходило до 54 уровней против 17 у прежнего диска.
    // WHY: Поэтому до LEVEL_ONE_FROM размытие идёт по самому кадру: точный интеграл по пикселю
    // WHY: (дисперсия 1/12) и раздельный гаусс. Увеличение на этих кадрах не больше 1.0005, чтение итога
    // WHY: идёт почти в центрах текселей, и поправка на билинейное чтение (1/6) на стенде только
    // WHY: ухудшала совпадение: при сигме 0.67 пикселя 55 уровней против 26 без неё
    private static int levelFor(float sigma) {
        if (sigma < LEVEL_ONE_FROM) return 0;
        int level = 1;
        while (level < MAX_LEVEL && sigma / (1 << (level + 1)) >= LEVEL_SPREAD) level++;
        return level;
    }

    // WHY: веса гаусса берутся в целых отсчётах, и у узкого ядра их дисперсия меньше заданной: при
    // WHY: сигме 0.45 текселя на треть. Параметр подбирается делением пополам так, чтобы дисперсия
    // WHY: самих дискретных весов, с тем же радиусом, что и в шейдере, была ровно заданной
    private static float matched(float exact, int taps) {
        if (taps <= 0) return exact;
        float wanted = exact * exact;
        float low = 0.0f;
        float high = exact * 2.0f + 1.0f;
        for (int step = 0; step < MATCH_STEPS; step++) {
            float middle = (low + high) * 0.5f;
            if (discreteVariance(middle, taps) < wanted) low = middle;
            else high = middle;
        }
        return (low + high) * 0.5f;
    }

    private static float discreteVariance(float width, int taps) {
        double fall = -0.5 / Math.max(1.0e-4, width * width);
        double total = 0.0;
        double moment = 0.0;
        for (int tap = -taps; tap <= taps; tap++) {
            double weight = Math.exp(fall * tap * tap);
            total += weight;
            moment += weight * tap * tap;
        }
        return (float) (moment / total);
    }

    private static void run(int stage, int width, int height) {
        int halfWidth = (width + 1) / 2;
        int halfHeight = (height + 1) / 2;
        int needWidth = scale == 1 ? width : halfWidth;
        int needHeight = scale == 1 ? height : halfHeight;
        FIRST.build(needWidth, needHeight, width, height);
        SECOND.build(needWidth, needHeight, width, height);
        prepareState();

        Level here = scale == 1 ? integrate(stage, width, height) : shrink(stage, width, height, halfWidth, halfHeight);
        if (reach > 0) {
            Level there = here == FIRST ? SECOND : FIRST;
            pass(there, levelWidth, levelHeight, here, levelWidth, levelHeight, PASS_LINE, 1.0f, 0.0f);
            pass(here, levelWidth, levelHeight, there, levelWidth, levelHeight, PASS_LINE, 0.0f, 1.0f);
        }
        result = here;
    }

    private static Level integrate(int stage, int width, int height) {
        draw(FIRST, width, height, stage, width, height, PASS_PIXEL, 0.0f, 0.0f);
        return FIRST;
    }

    private static Level shrink(int stage, int width, int height, int halfWidth, int halfHeight) {
        draw(FIRST, halfWidth, halfHeight, stage, width, height, PASS_STAGE, 0.0f, 0.0f);
        Level here = FIRST;
        int sourceWidth = halfWidth;
        int sourceHeight = halfHeight;
        while (sourceWidth > levelWidth || sourceHeight > levelHeight) {
            int nextWidth = (sourceWidth + 1) / 2;
            int nextHeight = (sourceHeight + 1) / 2;
            Level next = here == FIRST ? SECOND : FIRST;
            pass(next, nextWidth, nextHeight, here, sourceWidth, sourceHeight, PASS_LEVEL, 0.0f, 0.0f);
            here = next;
            sourceWidth = nextWidth;
            sourceHeight = nextHeight;
        }
        return here;
    }

    private static void pass(Level into, int width, int height, Level from, int sourceWidth, int sourceHeight,
                             float kind, float stepX, float stepY) {
        draw(into, width, height, from.target.getColorTextureId(), sourceWidth, sourceHeight, kind, stepX, stepY);
    }

    private static void draw(Level into, int width, int height, int source, int sourceWidth, int sourceHeight,
                             float kind, float stepX, float stepY) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, into.target.frameBufferId);
        GlStateManager._viewport(0, 0, width, height);
        RenderSystem.setShaderTexture(0, source);
        sourceUniform.set((float) sourceWidth, (float) sourceHeight);
        passUniform.set(kind);
        stepUniform.set(stepX, stepY);
        spreadUniform.set(spread, (float) reach);
        RenderSystem.setShader(() -> frostShader);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(-1.0, -1.0, 0.0).endVertex();
        builder.vertex(1.0, -1.0, 0.0).endVertex();
        builder.vertex(1.0, 1.0, 0.0).endVertex();
        builder.vertex(-1.0, 1.0, 0.0).endVertex();
        Tesselator.getInstance().end();
    }

    // WHY: проходы пишут цель целиком, без смешивания: ванильный BlendMode.apply пропускает смену по
    // WHY: устаревшему lastApplied, поэтому смешивание снимается явно, как и отсечение граней. Ножницы
    // WHY: снимаются и ставятся прямым вызовом, как в UiStage: их включают и мимо кеша GlStateManager
    private static void prepareState() {
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        UiRender.ignoreDepth();
        if (scissored) GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // WHY: состояние снимается с самого GL, а не с кеша GlStateManager: чужие моды (Distant Horizons,
    // WHY: Embeddium) ставят его мимо кеша, и возврат по кешу оставлял бы им не ту привязку кадра,
    // WHY: не то окно вывода и выключенную глубину. Проход обязан вернуть всё ровно как было
    private static void remember() {
        drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);
        scissored = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        blended = GL11.glIsEnabled(GL11.GL_BLEND);
        culled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        depthTested = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthWritten = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        sampled = RenderSystem.getShaderTexture(0);
        shaderBefore = RenderSystem.getShader();
        if (unpack != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
    }

    private static void restore() {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawn);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
        if (unpack != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
        if (scissored) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        restoreSwitches();
        RenderSystem.setShaderTexture(0, sampled);
        RenderSystem.setShader(UiFrost::previousShader);
        shaderBefore = null;
    }

    private static void restoreSwitches() {
        if (blended) RenderSystem.enableBlend();
        else RenderSystem.disableBlend();
        if (culled) RenderSystem.enableCull();
        else RenderSystem.disableCull();
        if (depthTested) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthWritten);
    }

    private static ShaderInstance previousShader() {
        return shaderBefore;
    }

    private static void fail(String reason) {
        failed = true;
        result = null;
        FIRST.release();
        SECOND.release();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawn);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        LogUtils.getLogger().warn("[battlecraft] frost blur off: {}", reason);
    }

    // WHY: цель растёт до полного кадра, только когда сигма потребовала уровня по самому кадру (первые
    // WHY: кадры ухода на частоте выше 60 Гц), и уже не сжимается обратно до простоя: уменьшенные уровни
    // WHY: лежат в её углу, а пересоздание посреди ухода стоило бы кадра
    private static final class Level {
        private TextureTarget target;
        private int frameWidth;
        private int frameHeight;

        private void build(int width, int height, int frameWide, int frameTall) {
            if (target != null && frameWidth == frameWide && frameHeight == frameTall
                    && target.width >= width && target.height >= height) return;

            release();
            frameWidth = frameWide;
            frameHeight = frameTall;
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            deepen(width, height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GlStateManager._bindTexture(texture);
        }

        // WHY: в восьми битах размытая кромка стекла с альфой 0.06 шла бы полосами, поэтому уровни
        // WHY: хранятся с той же глубиной, что и стадия
        private void deepen(int width, int height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) return;

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }

        private void release() {
            if (target == null) return;
            try {
                target.destroyBuffers();
            } catch (Throwable ignored) {
            }
            target = null;
        }
    }
}
