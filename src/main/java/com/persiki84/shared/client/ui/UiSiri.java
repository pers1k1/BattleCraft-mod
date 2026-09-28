package com.persiki84.shared.client.ui;

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

// WHY: свечение по кромке экрана, как у Siri с Apple Intelligence: цвета текут по кругу,
// WHY: толщина волнуется, а каждое нажатие клавиши даёт вспышку, будто ассистент слышит ввод
@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiSiri {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_siri");
    private static final float WIDTH_UNITS = 26.0f;
    private static final float SHOW_SPEED = 7.0f;
    private static final float PULSE_FADE = 3.2f;

    private static final Smooth shown = new Smooth(0.0f, SHOW_SPEED);
    private static ShaderInstance siriShader;
    private static float pulse;
    private static float elapsed;
    private static long stamp = -1L;

    private UiSiri() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> siriShader = shader);
    }

    public static void pulse() {
        pulse = 1.0f;
    }

    // WHY: свечение общее на все экраны, а гаснет только в кадре своего экрана: ушедший стол
    // WHY: уносил его зажжённым, и возврат на стол начинался с угасающей кромки
    public static void hide() {
        shown.snap(0.0f);
        pulse = 0.0f;
    }

    public static void render(GuiGraphics graphics, float width, float height, boolean wanted, float themed) {
        advance(wanted);
        if (siriShader == null || shown.get() <= 0.004f) return;

        graphics.flush();
        float scale = (float) Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        int accent = UiAccent.color();
        siriShader.safeGetUniform("Time").set(elapsed);
        siriShader.safeGetUniform("Glow").set(UiAnim.easeOut(shown.get()), pulse, WIDTH_UNITS * scale, themed);
        siriShader.safeGetUniform("Accent").set(((accent >> 16) & 0xFF) / 255.0f,
                ((accent >> 8) & 0xFF) / 255.0f, (accent & 0xFF) / 255.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.setShader(() -> siriShader);
        quad(graphics.pose().last().pose(), width, height);
        UiRender.standardBlend();
    }

    private static void advance(boolean wanted) {
        long frame = UiFrame.frame();
        if (frame == stamp) return;
        stamp = frame;
        float delta = UiFrame.delta();
        elapsed += delta;
        shown.to(wanted ? 1.0f : 0.0f, delta);
        pulse = Math.max(0.0f, pulse - delta * PULSE_FADE);
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
}
