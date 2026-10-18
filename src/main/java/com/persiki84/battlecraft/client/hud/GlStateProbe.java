package com.persiki84.battlecraft.client.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.client.ui.UiFarewell;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Locale;

// WHY: чёрный кадр вместо мира и HUD у владельца не воспроизводится без игры, и ни одна ошибка в лог
// WHY: не попадает. Зонд под ключом screenProbe раз в две секунды пишет состояние GL и цвет пикселя в
// WHY: середине кадра на четырёх точках кадра: так видно, на каком шаге кадр становится чёрным
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class GlStateProbe {
    private static final long PERIOD_NANOS = 2_000_000_000L;
    private static final ByteBuffer PIXEL = BufferUtils.createByteBuffer(4);
    private static final int[] VIEWPORT = new int[4];

    private static long nextAt;
    private static boolean sampling;
    private static int lastPixel;

    private GlStateProbe() {}

    @SubscribeEvent
    public static void onLevelStage(RenderLevelStageEvent event) {
        if (!sampling) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) report("after_sky");
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) report("after_level");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGuiPre(RenderGuiEvent.Pre event) {
        if (!sampling) return;
        report("gui_pre");
        lastPixel = PIXEL.getInt(0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (sampling) step("overlay " + event.getOverlay().id());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPostHighest(RenderGuiEvent.Post event) {
        if (sampling) step("post highest");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPostHigh(RenderGuiEvent.Post event) {
        if (sampling) step("post high");
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPostNormal(RenderGuiEvent.Post event) {
        if (sampling) step("post normal");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPostLow(RenderGuiEvent.Post event) {
        if (sampling) step("post low");
    }

    // WHY: пишется только шаг, после которого пиксель середины сменился: так в логе остаётся сам
    // WHY: виновник, а не сорок слоёв HUD подряд
    private static void step(String point) {
        center();
        int pixel = PIXEL.getInt(0);
        if (pixel == lastPixel) return;
        lastPixel = pixel;
        LogUtils.getLogger().info("[battlecraft] gl probe step {}: {} | {}", point, center(), switches());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuiPost(RenderGuiEvent.Post event) {
        if (sampling) report("gui_post");
        long now = System.nanoTime();
        sampling = HudConfig.screenProbe() && now >= nextAt;
        if (sampling) nextAt = now + PERIOD_NANOS;
    }

    private static void report(String point) {
        LogUtils.getLogger().info("[battlecraft] gl probe {}: {} | {} | {}", point, framing(), switches(), center());
    }

    private static String framing() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);
        return String.format(Locale.ROOT, "draw=%d read=%d main=%d/%d status=%x viewport=%d,%d,%dx%d burn=%b",
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                main.frameBufferId, main.getColorTextureId(), GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER),
                VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3], UiFarewell.burning());
    }

    private static String switches() {
        float[] color = RenderSystem.getShaderColor();
        return String.format(Locale.ROOT,
                "depth=%b mask=%b func=%x colormask=%b blend=%b scissor=%b cull=%b tint=%.2f,%.2f,%.2f,%.2f err=%x",
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC), colorWritten(), GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                color[0], color[1], color[2], color[3], GL11.glGetError());
    }

    private static boolean colorWritten() {
        PIXEL.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, PIXEL);
        return PIXEL.get(0) != 0 && PIXEL.get(1) != 0 && PIXEL.get(2) != 0;
    }

    private static String center() {
        PIXEL.clear();
        GL11.glReadPixels(VIEWPORT[0] + VIEWPORT[2] / 2, VIEWPORT[1] + VIEWPORT[3] / 2, 1, 1,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PIXEL);
        return String.format(Locale.ROOT, "center=%d,%d,%d,%d", PIXEL.get(0) & 0xFF, PIXEL.get(1) & 0xFF,
                PIXEL.get(2) & 0xFF, PIXEL.get(3) & 0xFF);
    }
}
