package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class MenuFeedback {
    private static final long LIFE_MS = 3600L;
    private static final float FADE_SPEED = 14.0f;
    private static final float SCALE = 0.85f;
    private static final float HEIGHT = 18.0f;
    private static final float PAD = 12.0f;
    private static final float SLIDE = 5.0f;
    private static final float MAX_WIDTH = 380.0f;

    private static final Smooth shown = new Smooth(0.0f, FADE_SPEED);

    private static Component message;
    private static boolean alerting;
    private static long shownAt;

    private MenuFeedback() {}

    public static void show(Component text, boolean alert) {
        message = text;
        alerting = alert;
        shownAt = System.currentTimeMillis();
    }

    public static void clear() {
        message = null;
        shownAt = 0L;
    }

    private static boolean fresh() {
        return message != null && System.currentTimeMillis() - shownAt < LIFE_MS;
    }

    public static void render(GuiGraphics graphics, float centerX, float top) {
        float alpha = shown.to(fresh() ? 1.0f : 0.0f, UiFrame.delta());
        if (alpha <= 0.01f || message == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        float scale = UiRender.crisp(graphics, SCALE);
        float width = Math.min(MAX_WIDTH, UiRender.measure(graphics, minecraft.font, message, scale) + PAD * 2.0f);
        float y = top + (1.0f - UiAnim.easeOut(alpha)) * SLIDE;

        UiGlass.panel(graphics, centerX - width / 2.0f, y, width, HEIGHT, UiMetrics.radius(HEIGHT), alpha);
        UiRender.textTrackedFit(graphics, minecraft.font, message, centerX, y, HEIGHT,
                width - PAD * 2.0f, scale, 0.0f, tint(alpha), false);
    }

    private static int tint(float alpha) {
        return UiTheme.alpha(alerting ? UiPalette.alert() : UiAccent.color(), alpha);
    }
}
