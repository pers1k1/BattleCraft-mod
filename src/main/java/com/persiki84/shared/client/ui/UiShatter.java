package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;

// WHY: порядок стадий задан владельцем: сначала раскол при целой панели, дальше осколки расходятся,
// WHY: по ним нарастает расфокус, и гаснут они вразнобой, а не все разом
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiShatter {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_shatter");

    private static final float FADE_FROM = 0.30f;
    private static final float FADE_STAGGER = 0.34f;
    private static final float FLY_BLUR = 7.0f;
    private static final float GLOW_WIDTH = 4.5f;
    private static final float CRACK_PEAK = 0.55f;
    private static final float RIM_ALPHA = 0.42f;
    private static final float RIM_KEEP = 0.16f;
    private static final float RIM_ACCENT = 0.12f;
    private static final float GONE = 0.02f;

    private static ShaderInstance shatterShader;

    private UiShatter() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> shatterShader = shader);
        } catch (IOException error) {
            LogUtils.getLogger().warn("[battlecraft] menu shatter off: {}", String.valueOf(error));
        }
    }

    public static boolean ready() {
        return shatterShader != null;
    }

    public static void draw(GuiGraphics graphics, float screenWidth, float screenHeight, int texture,
                            UiShards shards, float phase) {
        if (shatterShader == null || shards == null || texture == 0) return;
        if (screenWidth <= 0.0f || screenHeight <= 0.0f) return;

        float alive = fade(phase, 1.0f);
        float rim = rim(phase);
        if (alive <= GONE && rim <= 0.0f) return;

        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        if (alive > GONE) {
            arm(texture, 0.0f, blur(phase));
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int index = 0; index < shards.count(); index++) {
                shard(builder, matrix, screenWidth, screenHeight, shards, index, phase);
            }
            Tesselator.getInstance().end();
        }
        if (rim > 0.0f) {
            arm(texture, 1.0f, 0.0f);
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            seams(builder, matrix, screenWidth, screenHeight, shards, phase, rim);
            Tesselator.getInstance().end();
        }
        release();
    }

    // WHY: отсечение граней снимается на время прохода: контур ячейки Вороного идёт по часовой,
    // WHY: то есть обратно ванильному кваду интерфейса, и все треугольники срезались бы как задние
    private static void arm(int texture, float rim, float blur) {
        RenderSystem.disableCull();
        UiRender.ignoreDepth();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(() -> shatterShader);
        RenderSystem.setShaderTexture(0, texture);
        shatterShader.safeGetUniform("Rim").set(rim);
        shatterShader.safeGetUniform("Blur").set(blur);
    }

    private static void release() {
        RenderSystem.enableCull();
        UiRender.resumeDepth();
        UiRender.standardBlend();
    }

    private static void shard(BufferBuilder builder, Matrix4f matrix, float screenWidth, float screenHeight,
                              UiShards shards, int index, float phase) {
        int corners = shards.corners(index);
        float alpha = fade(phase, shards.lag(index));
        if (corners < 3 || alpha <= GONE) return;

        UiShards.Flight place = shards.flight(index, phase);
        for (int corner = 1; corner < corners - 1; corner++) {
            vertex(builder, matrix, screenWidth, screenHeight, shards, index, 0, place, alpha);
            vertex(builder, matrix, screenWidth, screenHeight, shards, index, corner, place, alpha);
            vertex(builder, matrix, screenWidth, screenHeight, shards, index, corner + 1, place, alpha);
        }
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, float screenWidth, float screenHeight,
                               UiShards shards, int index, int corner, UiShards.Flight place, float alpha) {
        float sourceX = shards.cornerX(index, corner);
        float sourceY = shards.cornerY(index, corner);
        float shade = shards.shade(index);
        builder.vertex(matrix, place.x(sourceX, sourceY), place.y(sourceX, sourceY), 0.0f)
                .uv(sourceX / screenWidth, 1.0f - sourceY / screenHeight)
                .color(shade, shade, shade, alpha)
                .endVertex();
    }

    private record Tint(float red, float green, float blue) {
        private static Tint of(int color) {
            return new Tint(((color >> 16) & 0xFF) / 255.0f, ((color >> 8) & 0xFF) / 255.0f,
                    (color & 0xFF) / 255.0f);
        }
    }

    // WHY: расфокус нарастает вместе с разлётом, поэтому осколок уходит не просто в прозрачность,
    // WHY: а теряет резкость по дороге, как отлетевший кусок стекла в кадре с малой глубиной резкости
    private static float blur(float phase) {
        return FLY_BLUR * UiShards.thrown(phase);
    }

    // WHY: осколки гаснут вразнобой: у каждого своя задержка от его же разгона, поэтому панель
    // WHY: тает понемногу, а не исчезает одним кадром
    private static float fade(float phase, float lag) {
        float start = FADE_FROM + lag * FADE_STAGGER;
        if (phase <= start) return 1.0f;
        return 1.0f - UiAnim.smoothstep(start, 1.0f, phase);
    }

    private static void seams(BufferBuilder builder, Matrix4f matrix, float screenWidth, float screenHeight,
                             UiShards shards, float phase, float light) {
        Tint tint = Tint.of(UiTheme.mix(UiAccent.color(), UiTheme.WHITE, RIM_ACCENT));
        for (int index = 0; index < shards.count(); index++) {
            outline(builder, matrix, screenWidth, screenHeight, shards, index, phase, tint, light);
        }
    }

    private static void outline(BufferBuilder builder, Matrix4f matrix, float screenWidth, float screenHeight,
                                UiShards shards, int index, float phase, Tint tint, float light) {
        int corners = shards.corners(index);
        if (corners < 3) return;

        UiShards.Flight place = shards.flight(index, phase);
        float shown = light * fade(phase, shards.lag(index));
        if (shown <= 0.0f) return;

        for (int corner = 0; corner < corners; corner++) {
            int next = (corner + 1) % corners;
            hairline(builder, matrix, screenWidth, screenHeight, place,
                    shards.cornerX(index, corner), shards.cornerY(index, corner),
                    shards.cornerX(index, next), shards.cornerY(index, next), tint, shown);
        }
    }

    private static void hairline(BufferBuilder builder, Matrix4f matrix, float screenWidth, float screenHeight,
                                 UiShards.Flight place, float fromX, float fromY, float toX, float toY,
                                 Tint tint, float light) {
        float runX = toX - fromX;
        float runY = toY - fromY;
        float length = (float) Math.sqrt(runX * runX + runY * runY);
        if (length < 0.001f) return;

        float offX = -runY / length * GLOW_WIDTH;
        float offY = runX / length * GLOW_WIDTH;
        band(builder, matrix, screenWidth, screenHeight, place, fromX, fromY, toX, toY, -offX, -offY, tint, light);
        band(builder, matrix, screenWidth, screenHeight, place, fromX, fromY, toX, toY, offX, offY, tint, light);
    }

    // WHY: полоса идёт от кромки к вынесенному краю с нулевой яркостью, поэтому свечение мягкое:
    // WHY: спад даёт сама интерполяция вершинного цвета, без ступеньки на границе кванта
    private static void band(BufferBuilder builder, Matrix4f matrix, float screenWidth, float screenHeight,
                             UiShards.Flight place, float fromX, float fromY, float toX, float toY,
                             float offX, float offY, Tint tint, float light) {
        glint(builder, matrix, screenWidth, screenHeight, place, fromX, fromY, tint, light);
        glint(builder, matrix, screenWidth, screenHeight, place, toX, toY, tint, light);
        glint(builder, matrix, screenWidth, screenHeight, place, toX + offX, toY + offY, tint, 0.0f);
        glint(builder, matrix, screenWidth, screenHeight, place, fromX, fromY, tint, light);
        glint(builder, matrix, screenWidth, screenHeight, place, toX + offX, toY + offY, tint, 0.0f);
        glint(builder, matrix, screenWidth, screenHeight, place, fromX + offX, fromY + offY, tint, 0.0f);
    }

    // WHY: положение берётся у сдвинутого осколка, а координата снимка у исходного: покрытие обязано
    // WHY: читаться там, где кромка была до разлёта, иначе кант гаснет, уехав за край панели
    private static void glint(BufferBuilder builder, Matrix4f matrix, float screenWidth, float screenHeight,
                              UiShards.Flight place, float sourceX, float sourceY, Tint tint, float light) {
        builder.vertex(matrix, place.x(sourceX, sourceY), place.y(sourceX, sourceY), 0.0f)
                .uv(sourceX / screenWidth, 1.0f - sourceY / screenHeight)
                .color(tint.red(), tint.green(), tint.blue(), light)
                .endVertex();
    }

    // WHY: у канта две составляющие: яркая вспышка на самом расколе и ровный слабый свет, который
    // WHY: держится, пока осколок виден, и гаснет вместе с ним
    private static float rim(float phase) {
        float flash = UiShards.crackUntil() * CRACK_PEAK;
        float rise = UiAnim.clamp01(phase / flash);
        float drop = 1.0f - UiAnim.clamp01((phase - flash) / (UiShards.seamUntil() - flash));
        return Math.max(Math.min(rise, drop) * RIM_ALPHA, RIM_KEEP);
    }
}
