package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;

// WHY: обычная заливка кладёт на фигуру полигон с растушёвкой, и на мелочи вроде полоски
// WHY: визуализатора в три пикселя шириной на кромку места уже не остаётся - край выходит рваным.
// WHY: точное расстояние до формы считается одинаково на любом размере, поэтому кромка ровная
// WHY: и на встроенном экране ноутбука, и на 4K: мягкость берётся от пикселей на единицу интерфейса
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiCrisp {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_crisp");
    private static final float EDGE_SOFT_PIXELS = 1.15f;

    private static ShaderInstance crispShader;

    private UiCrisp() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> crispShader = shader);
    }

    public static boolean ready() {
        return crispShader != null;
    }

    public static void panel(GuiGraphics graphics, float x, float y, float width, float height,
                             float radius, int color) {
        panelShaded(graphics, x, y, width, height, radius, color, color);
    }

    public static void panelShaded(GuiGraphics graphics, float x, float y, float width, float height,
                                   float radius, int top, int bottom) {
        if (crispShader == null || width <= 0.0f || height <= 0.0f) return;

        float pad = softness(graphics);
        arm(top, bottom, width, height, radius, pad);
        UiRender.standardBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> crispShader);
        quad(graphics.pose().last().pose(), x - pad, y - pad, width + pad * 2.0f, height + pad * 2.0f);
        RenderSystem.enableCull();
    }

    private static float softness(GuiGraphics graphics) {
        return EDGE_SOFT_PIXELS / Math.max(0.05f, UiRender.pixels(graphics));
    }

    private static void arm(int top, int bottom, float width, float height, float radius, float pad) {
        tint("TopTint", top);
        tint("BottomTint", bottom);
        crispShader.safeGetUniform("Half").set(width / 2.0f + pad, height / 2.0f + pad);
        crispShader.safeGetUniform("Shape").set(width / 2.0f, height / 2.0f);
        crispShader.safeGetUniform("Radius").set(Math.min(radius, Math.min(width, height) / 2.0f));
        crispShader.safeGetUniform("Soft").set(pad);
    }

    private static void tint(String name, int color) {
        crispShader.safeGetUniform(name).set(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                ((color >>> 24) & 0xFF) / 255.0f);
    }

    // WHY: квад берётся на растушёвку шире самой фигуры, иначе кромка срезается его же краем
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
