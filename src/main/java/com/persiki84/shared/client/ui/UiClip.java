package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.platform.Window;
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

import java.io.IOException;

// WHY: обрезка по скруглённой форме через буфер глубины режет только целыми пикселями, и угол
// WHY: содержимого идёт ступенькой. Содержимое рисуется в офскрин окна, где под ним лежит копия
// WHY: кадра, а на место кладётся маской по точному расстоянию до формы со сглаженной кромкой
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiClip {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_clip");
    private static final float MARGIN = 1.0f;

    private static ShaderInstance clipShader;

    private UiClip() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> clipShader = shader);
    }

    public static boolean begin(GuiGraphics graphics) {
        if (clipShader == null) return false;
        graphics.flush();
        return UiStage.beginWindow(null, 0.0f, graphics.guiWidth(), graphics.guiHeight());
    }

    public static void end(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
        graphics.flush();
        UiStage.endWindow();
        arm(graphics, x, y, width, height, Math.min(radius, Math.min(width, height) * 0.5f));
        RenderSystem.setShaderTexture(0, UiStage.windowTexture());
        UiRender.standardBlend();
        RenderSystem.setShader(() -> clipShader);
        quad(graphics.pose().last().pose(), x - MARGIN, y - MARGIN, width + MARGIN * 2.0f, height + MARGIN * 2.0f);
    }

    private static void arm(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
        Window window = Minecraft.getInstance().getWindow();
        double gui = Math.max(1.0, window.getGuiScale());
        float pixels = UiRender.pixels(graphics);
        float centreX = (float) (UiRender.screenX(graphics, x + width / 2.0f, y + height / 2.0f) * gui);
        float centreY = (float) (window.getHeight() - UiRender.screenY(graphics, x + width / 2.0f, y + height / 2.0f) * gui);
        clipShader.safeGetUniform("ClipShape").set(centreX, centreY, width * pixels / 2.0f, height * pixels / 2.0f);
        float reach = Math.min(UiGlassStyle.shapeReach(width, height, radius), Math.min(width, height) * 0.5f);
        clipShader.safeGetUniform("ClipCorner").set(reach * pixels, UiGlassStyle.shapePower(width, height, radius));
    }

    private static void quad(Matrix4f matrix, float x, float y, float width, float height) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, x, y + height, 0.0f).uv(0.0f, 1.0f).endVertex();
        builder.vertex(matrix, x + width, y + height, 0.0f).uv(1.0f, 1.0f).endVertex();
        builder.vertex(matrix, x + width, y, 0.0f).uv(1.0f, 0.0f).endVertex();
        builder.vertex(matrix, x, y, 0.0f).uv(0.0f, 0.0f).endVertex();
        Tesselator.getInstance().end();
    }
}
