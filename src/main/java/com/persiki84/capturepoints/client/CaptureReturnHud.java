package com.persiki84.capturepoints.client;

import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import com.persiki84.shared.zone.ZoneArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class CaptureReturnHud {
    private static final float CARD_WIDTH = 158.0f;
    private static final float CARD_HEIGHT = 30.0f;
    private static final float PADDING = 8.0f;
    private static final float BAR_HEIGHT = 3.0f;
    private static final float TITLE_SCALE = 0.9f;
    private static final float TIMER_SCALE = 0.75f;
    private static final float SLIDE = 8.0f;
    private static final float FADE_SPEED = 12.0f;
    private static final long FADE_MS = 140L;
    private static final float BAR_SPEED = 9.0f;
    private static final long EXTEND_SLACK_MS = 350L;

    private static final Toggle visibility = new Toggle(FADE_SPEED, FADE_MS);
    private static final Smooth barFill = new Smooth(1.0f, BAR_SPEED);

    private static long endsAt;
    private static long span = 1L;
    private static int shownSeconds;
    private static float shownValue;

    private CaptureReturnHud() {}

    public static void show(int seconds) {
        long now = System.currentTimeMillis();
        long ending = now + seconds * 1000L;

        if (!lingering(now)) {
            endsAt = ending;
            span = Math.max(1000L, seconds * 1000L);
            return;
        }
        if (ending <= endsAt + EXTEND_SLACK_MS) return;

        endsAt = ending;
        span = Math.max(span, ending - now);
    }

    public static void clear() {
        endsAt = 0L;
    }

    private static boolean lingering(long now) {
        return endsAt > now;
    }

    private static boolean wanted() {
        return lingering(System.currentTimeMillis())
                && ClientCaptureData.getLocalCapturingPoint() != null
                && outside();
    }

    private static boolean outside() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return true;

        String point = ClientCaptureData.getLocalCapturingPoint();
        ZoneArea area = ClientCaptureData.getPointArea(point);
        if (area == null) area = ClientCaptureData.getFinalPointArea(point);
        if (area == null) return true;

        return !area.contains(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
    }

    public static float render(GuiGraphics graphics, Minecraft mc, float centerX, float top, float delta) {
        float alpha = visibility.update(wanted(), delta);
        if (visibility.live()) {
            track();
        }
        if (alpha <= 0.01f) return 0.0f;

        float x = centerX - CARD_WIDTH / 2.0f;
        float y = top - (1.0f - UiAnim.easeOut(alpha)) * SLIDE;
        UiVital.card(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, UiMetrics.radius(CARD_HEIGHT), alpha);
        paint(graphics, mc, x, y, alpha, delta);

        return y + CARD_HEIGHT;
    }

    private static void track() {
        long left = Math.max(0L, endsAt - System.currentTimeMillis());
        shownSeconds = (int) Math.ceil(left / 1000.0);
        shownValue = UiAnim.clamp01(left / (float) span);
    }

    private static void paint(GuiGraphics graphics, Minecraft mc, float x, float y, float alpha, float delta) {
        UiRender.textCentered(graphics, mc.font, Component.translatable("capturepoints.hud.return_title"),
                x + CARD_WIDTH / 2.0f, y + PADDING - 2.0f, TITLE_SCALE,
                UiTheme.alpha(UiPalette.alert(), alpha), false);

        UiRender.textCentered(graphics, mc.font,
                Component.translatable("capturepoints.hud.return_subtitle", timer()),
                x + CARD_WIDTH / 2.0f, y + PADDING + 8.0f, TIMER_SCALE,
                UiTheme.alpha(HudInk.text(), alpha), false);

        float barX = x + PADDING;
        float barY = y + CARD_HEIGHT - PADDING + 1.0f;
        float barWidth = CARD_WIDTH - PADDING * 2.0f;
        UiGlass.sunken(graphics, barX, barY, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, barX, barY, barWidth, BAR_HEIGHT,
                barFill.to(shownValue, delta), UiPalette.alert(), alpha);
    }

    private static Component timer() {
        return Component.translatable("capturepoints.hud.return_seconds", shownSeconds);
    }
}
