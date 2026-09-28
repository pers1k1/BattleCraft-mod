package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.AbstractUniform;
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

// WHY: ореол гасит мир вокруг всплывающего, а не рисует ему тень: под самой панелью он сплошной
// WHY: и спадает только наружу, поэтому стекло поверх читает чистый снимок. Шейдер знает ореолы,
// WHY: уже положенные в этом кадре, и докладывает только то, чего им не хватает до нового
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiHush {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_hush");
    private static final float GONE = 0.004f;
    private static final String[] PRIOR_BOXES = {"PriorBox0", "PriorBox1", "PriorBox2", "PriorBox3"};
    private static final String[] PRIOR_FORMS = {"PriorForm0", "PriorForm1", "PriorForm2", "PriorForm3"};

    private static final UiHushSpot spot = new UiHushSpot();
    private static final UiHushSpot[] priors = UiHushSpot.pool(UiHushLedger.PRIORS);
    private static final AbstractUniform[] priorBoxes = new AbstractUniform[UiHushLedger.PRIORS];
    private static final AbstractUniform[] priorForms = new AbstractUniform[UiHushLedger.PRIORS];

    private static ShaderInstance hushShader;
    private static AbstractUniform shapeUniform;
    private static AbstractUniform formUniform;

    private UiHush() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                UiHush::hold);
    }

    // WHY: юниформы берутся один раз на сборку шейдера, а не по имени в кадре: ореол кладётся под каждое
    // WHY: всплывающее, и десять поисков по строке на каждое шли бы десятками за кадр
    private static void hold(ShaderInstance shader) {
        shapeUniform = shader.safeGetUniform("Shape");
        formUniform = shader.safeGetUniform("Form");
        for (int index = 0; index < UiHushLedger.PRIORS; index++) {
            priorBoxes[index] = shader.safeGetUniform(PRIOR_BOXES[index]);
            priorForms[index] = shader.safeGetUniform(PRIOR_FORMS[index]);
        }
        hushShader = shader;
    }

    public static void draw(GuiGraphics graphics, float x, float y, float width, float height,
                            float radius, float reach, float strength, float alpha) {
        float faded = alpha * RenderSystem.getShaderColor()[3];
        if (hushShader == null || strength * faded <= GONE || width <= 0.0f || height <= 0.0f) return;

        place(graphics, x, y, width, height, radius, reach);
        spot.strength = strength * faded;
        spot.cover = faded;
        spot.target = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        Matrix4f matrix = graphics.pose().last().pose();
        lay(matrix, x - reach, y - reach, width + reach * 2.0f, height + reach * 2.0f);
        mirror(matrix, x - reach, y - reach, width + reach * 2.0f, height + reach * 2.0f);
    }

    private static void lay(Matrix4f matrix, float x, float y, float width, float height) {
        UiHushLedger.gather(spot, priors);
        arm();
        paint(matrix, x, y, width, height);
        UiHushLedger.record(spot);
    }

    // WHY: кольцо вокруг панели в стадии экрана лежит там, где у стадии нет покрытия, и композит
    // WHY: берёт этот пиксель из главного кадра: кольцо появлялось только на посадке входа и пропадало
    // WHY: на первом кадре ухода. Поэтому оно повторяется и на главном кадре с долей появления стадии:
    // WHY: там, где маска ещё не открыта, видно это кольцо, а открытая маска показывает то, что в стадии
    private static void mirror(Matrix4f matrix, float x, float y, float width, float height) {
        int stage = spot.target;
        int frame = UiStage.mirroredFrame(stage);
        float share = UiGlassStyle.presence();
        if (frame == UiHushLedger.NONE || spot.strength * share <= GONE) return;

        spot.strength *= share;
        spot.cover *= share;
        spot.target = frame;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        try {
            lay(matrix, x, y, width, height);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, stage);
        }
    }

    // WHY: ореолы сравниваются в пикселях кадра, а не в единицах своей позы: у подсказки, меню и
    // WHY: уведомления разные сдвиги и масштабы, общий у них только кадр. Перенос тот же, что у центра
    // WHY: стекла в UiPane: поза без поворота, умноженная на масштаб интерфейса, ось y снизу вверх
    private static void place(GuiGraphics graphics, float x, float y, float width, float height,
                              float radius, float reach) {
        Window window = Minecraft.getInstance().getWindow();
        float gui = (float) Math.max(1.0, window.getGuiScale());
        float left = UiRender.screenX(graphics, x, y) * gui;
        float top = UiRender.screenY(graphics, x, y) * gui;
        float right = UiRender.screenX(graphics, x + width, y + height) * gui;
        float bottom = UiRender.screenY(graphics, x + width, y + height) * gui;
        float pixels = UiRender.pixels(graphics);

        spot.centreX = (left + right) * 0.5f;
        spot.centreY = window.getHeight() - (top + bottom) * 0.5f;
        spot.halfWidth = Math.abs(right - left) * 0.5f;
        spot.halfHeight = Math.abs(bottom - top) * 0.5f;
        spot.radius = Math.min(radius * pixels, Math.min(spot.halfWidth, spot.halfHeight));
        spot.reach = reach * pixels;
    }

    private static void arm() {
        bind(shapeUniform, formUniform, spot);
        for (int index = 0; index < UiHushLedger.PRIORS; index++) {
            bind(priorBoxes[index], priorForms[index], priors[index]);
        }
    }

    private static void bind(AbstractUniform box, AbstractUniform form, UiHushSpot source) {
        box.set(source.centreX, source.centreY, source.halfWidth, source.halfHeight);
        form.set(source.radius, source.reach, source.strength, source.cover);
    }

    // WHY: ореол только гасит кадр. Запиши он глубину, всё, что ляжет позже ниже его z, пропало бы
    // WHY: в кольце шириной в дальность вокруг панели. Альфу цели он не трогает: в офскрин-стадии она
    // WHY: служит маской покрытия, и любая ненулевая альфа делает пиксель интерфейсом, так что кольцо
    // WHY: горело бы искрами и выносило в композит перенесённый фон стадии
    private static void paint(Matrix4f matrix, float x, float y, float width, float height) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> hushShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, x, y + height, 0.0f).uv(0.0f, 1.0f).endVertex();
        builder.vertex(matrix, x + width, y + height, 0.0f).uv(1.0f, 1.0f).endVertex();
        builder.vertex(matrix, x + width, y, 0.0f).uv(1.0f, 0.0f).endVertex();
        builder.vertex(matrix, x, y, 0.0f).uv(0.0f, 0.0f).endVertex();
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        UiRender.standardBlend();
    }
}
