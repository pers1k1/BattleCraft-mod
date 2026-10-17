package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
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

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiAssemble {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_assemble");

    private static final float CENTRE = 0.5f;
    private static final float ARRIVED = 1.0f;

    private static ShaderInstance assembleShader;
    private static AbstractUniform phaseUniform;
    private static AbstractUniform timeUniform;
    private static AbstractUniform modeUniform;
    private static AbstractUniform sweepUniform;
    private static AbstractUniform originUniform;
    private static AbstractUniform arrivalUniform;
    private static AbstractUniform sparkUniform;
    private static AbstractUniform frostSpan;
    private static AbstractUniform frostShare;

    private UiAssemble() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                    UiAssemble::adopt);
        } catch (IOException error) {
            LogUtils.getLogger().warn("[battlecraft] menu intro off: {}", String.valueOf(error));
        }
    }

    private static void adopt(ShaderInstance shader) {
        assembleShader = shader;
        phaseUniform = shader.safeGetUniform("Phase");
        timeUniform = shader.safeGetUniform("Time");
        modeUniform = shader.safeGetUniform("Mode");
        sweepUniform = shader.safeGetUniform("Sweep");
        originUniform = shader.safeGetUniform("Origin");
        arrivalUniform = shader.safeGetUniform("Arrival");
        sparkUniform = shader.safeGetUniform("Spark");
        frostSpan = shader.safeGetUniform("FrostSpan");
        frostShare = shader.safeGetUniform("FrostShare");
    }

    public static boolean ready() {
        return assembleShader != null;
    }

    public static void draw(GuiGraphics graphics, float width, float height, int texture,
                            float phase, float seconds, float mode, float sweepTop, float sweepSpan, float charge) {
        draw(graphics, width, height, texture, phase, seconds, mode, sweepTop, sweepSpan,
                CENTRE, CENTRE, charge);
    }

    public static void draw(GuiGraphics graphics, float width, float height, int texture,
                            float phase, float seconds, float mode, float sweepTop, float sweepSpan,
                            float originX, float originY, float charge) {
        paint(graphics, texture, 0.0f, 0.0f, width, height, width, height,
                phase, seconds, mode, sweepTop, sweepSpan, originX, originY, charge, ARRIVED);
    }

    // WHY: уход, начатый посреди входа, продолжает его с той доли, на которой вход застали: иней иначе
    // WHY: на первом кадре ухода прыгал к полной непрозрачности и к масштабу покоя
    public static void leave(GuiGraphics graphics, float width, float height, int texture,
                             float phase, float seconds, float mode, float sweepTop, float sweepSpan,
                             float originX, float originY, float charge, float arrived) {
        paint(graphics, texture, 0.0f, 0.0f, width, height, width, height,
                phase, seconds, mode, sweepTop, sweepSpan, originX, originY, charge, arrived);
    }

    // WHY: шейдер сэмплит по gl_FragCoord, поэтому квад можно ужать до окна и не платить за полноэкранный проход
    public static void window(GuiGraphics graphics, float screenWidth, float screenHeight, int texture,
                              float left, float top, float width, float height, float margin,
                              float phase, float seconds, float mode, float charge, float arrived) {
        float quadLeft = Math.max(0.0f, left - margin);
        float quadTop = Math.max(0.0f, top - margin);
        float quadRight = Math.min(screenWidth, left + width + margin);
        float quadBottom = Math.min(screenHeight, top + height + margin);

        paint(graphics, texture, quadLeft, quadTop, quadRight - quadLeft, quadBottom - quadTop,
                screenWidth, screenHeight, phase, seconds, mode, top, height,
                (left + width / 2.0f) / screenWidth, 1.0f - (top + height / 2.0f) / screenHeight, charge, arrived);
    }

    private static void paint(GuiGraphics graphics, int texture, float left, float top, float width, float height,
                              float screenWidth, float screenHeight, float phase, float seconds, float mode,
                              float sweepTop, float sweepSpan, float originX, float originY, float charge,
                              float arrived) {
        if (assembleShader == null || texture == 0 || width <= 0.0f || height <= 0.0f) return;

        graphics.flush();
        if (mode == UiReveal.FROST_LEAVE) UiFrost.soften(texture, phase);
        arm(phase, seconds, mode, sweepTop / screenHeight, Math.max(0.02f, sweepSpan / screenHeight),
                originX, originY, charge, arrived);
        UiFrost.aim(frostSpan, frostShare);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        // WHY: догорающая панель висит в мире, и к ней можно зайти со спины: с отсечением задних
        // WHY: граней её квад со спины срезался целиком, и панель пропадала посреди горения
        RenderSystem.disableCull();
        UiRender.ignoreDepth();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(() -> assembleShader);
        RenderSystem.setShaderTexture(0, texture);

        float leftU = left / screenWidth;
        float rightU = (left + width) / screenWidth;
        float topV = 1.0f - top / screenHeight;
        float bottomV = 1.0f - (top + height) / screenHeight;

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, left, top + height, 0.0f).uv(leftU, bottomV).endVertex();
        builder.vertex(matrix, left + width, top + height, 0.0f).uv(rightU, bottomV).endVertex();
        builder.vertex(matrix, left + width, top, 0.0f).uv(rightU, topV).endVertex();
        builder.vertex(matrix, left, top, 0.0f).uv(leftU, topV).endVertex();
        Tesselator.getInstance().end();

        RenderSystem.enableCull();
        UiRender.resumeDepth();
        UiRender.standardBlend();
        UiFrost.release();
    }

    private static void arm(float phase, float seconds, float mode, float sweepTop, float sweepSpan,
                            float originX, float originY, float charge, float arrived) {
        phaseUniform.set(phase);
        timeUniform.set(seconds);
        modeUniform.set(mode);
        sweepUniform.set(sweepTop, sweepSpan);
        originUniform.set(originX, originY);
        arrivalUniform.set(arrived);

        int accent = UiAccent.color();
        sparkUniform.set(
                ((accent >> 16) & 0xFF) / 255.0f,
                ((accent >> 8) & 0xFF) / 255.0f,
                (accent & 0xFF) / 255.0f,
                charge);
    }
}
