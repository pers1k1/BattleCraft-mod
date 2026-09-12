package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.announce.AnnounceStyle;
import com.persiki84.battlecraft.announce.Announcements;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

// WHY: объявление оператора живёт поверх всего и по центру экрана: у него нет своего слота в
// WHY: редакторе, потому что смысл его в том, что мимо него не пройти
public final class AnnounceHud {
    private static final float BORN_MS = 320.0f;
    private static final float GONE_MS = 700.0f;
    private static final float TEXT_SCALE = 1.15f;
    private static final float CAPTION_SCALE = 0.72f;
    private static final float CAPTION_TRACKING = 1.4f;
    private static final float MAX_WIDTH = 306.0f;
    private static final float MIN_WIDTH = 150.0f;
    private static final float PAD_X = 20.0f;
    private static final float PAD_Y = 11.0f;
    private static final float CAPTION_GAP = 7.0f;
    private static final float RULE_HEIGHT = 1.4f;
    private static final float RULE_WIDTH = 26.0f;
    private static final float LINE_GAP = 2.5f;
    private static final float SCREEN_SHARE = 0.28f;
    private static final float RISE = 12.0f;
    private static final float BORN_SCALE = 0.9f;
    private static final float GLOW = 0.26f;
    private static final float PULSE_MS = 1600.0f;
    private static final float GONE = 0.01f;

    private static final List<FormattedCharSequence> lines = new ArrayList<>();

    private static Component text;
    private static Component measured;
    private static float measuredScale;
    private static float boxWidth;
    private static float boxHeight;
    private static long shownAt;
    private static long lifespan;

    private AnnounceHud() {}

    public static void show(String message, AnnounceStyle style, int seconds) {
        Component body = Component.literal(message);
        long life = Announcements.clampSeconds(seconds) * 1000L;
        if (style.noticed()) ToastHud.pushFor(body, life);
        if (!style.bannered()) return;

        text = body;
        measured = null;
        shownAt = System.currentTimeMillis();
        lifespan = life;
        UiSound.alert();
    }

    public static void forget() {
        text = null;
        lines.clear();
    }

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        if (text == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed > lifespan) {
            forget();
            return;
        }

        float alpha = Math.min(UiAnim.fadeIn(shownAt, BORN_MS),
                UiAnim.clamp01((lifespan - elapsed) / GONE_MS));
        if (alpha <= GONE) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, mc, screenWidth / scale, screenHeight / scale, alpha);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight,
                               float alpha) {
        layout(graphics, mc.font);
        if (lines.isEmpty()) return;

        float centerX = screenWidth / 2.0f;
        float top = screenHeight * SCREEN_SHARE;
        float eased = UiAnim.easeOut(alpha);
        float born = BORN_SCALE + (1.0f - BORN_SCALE) * UiAnim.easeOutBack(alpha);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, top + (1.0f - eased) * RISE, 0.0f);
        graphics.pose().scale(born, born, 1.0f);
        graphics.pose().translate(-centerX, -top, 0.0f);
        try {
            paint(graphics, mc.font, centerX - boxWidth / 2.0f, top, eased);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void paint(GuiGraphics graphics, Font font, float x, float y, float alpha) {
        float pulse = UiAnim.pulse(PULSE_MS, 0.62f, 1.0f);
        int accent = UiAccent.color();

        UiVital.cardTinted(graphics, x, y, boxWidth, boxHeight, UiMetrics.radius(boxHeight), alpha,
                GLOW * pulse, UiTheme.withAlpha(accent, 0.22f));

        float centerX = x + boxWidth / 2.0f;
        float caption = y + PAD_Y;
        UiRender.textTracked(graphics, font, Component.translatable("battlecraft.announce.caption"),
                centerX, caption, CAPTION_SCALE, CAPTION_TRACKING,
                UiTheme.alpha(HudInk.textDim(), alpha));

        float ruleY = caption + font.lineHeight * CAPTION_SCALE + CAPTION_GAP / 2.0f;
        UiRender.panel(graphics, centerX - RULE_WIDTH / 2.0f, ruleY, RULE_WIDTH, RULE_HEIGHT,
                RULE_HEIGHT / 2.0f, UiTheme.alpha(accent, alpha * pulse));

        float textY = ruleY + RULE_HEIGHT + CAPTION_GAP;
        float step = font.lineHeight * TEXT_SCALE + LINE_GAP;
        for (FormattedCharSequence line : lines) {
            float span = UiRender.measureLine(graphics, font, line, TEXT_SCALE);
            UiRender.textLine(graphics, font, line, centerX - span / 2.0f, textY, TEXT_SCALE,
                    UiTheme.alpha(HudInk.text(), alpha), false);
            textY += step;
        }
    }

    // WHY: разбор на строки идёт через атласы шрифта, поэтому держится до смены текста или
    // WHY: масштаба интерфейса, а не считается каждый кадр
    private static void layout(GuiGraphics graphics, Font font) {
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        if (text == measured && Math.abs(scale - measuredScale) < 1.0E-4f) return;

        measured = text;
        measuredScale = scale;
        lines.clear();
        lines.addAll(UiRender.split(graphics, font, text, TEXT_SCALE,
                (int) ((MAX_WIDTH - PAD_X * 2.0f) / TEXT_SCALE)));

        float widest = 0.0f;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, UiRender.measureLine(graphics, font, line, TEXT_SCALE));
        }
        boxWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, widest + PAD_X * 2.0f));
        boxHeight = PAD_Y * 2.0f + font.lineHeight * CAPTION_SCALE + CAPTION_GAP * 1.5f + RULE_HEIGHT
                + lines.size() * (font.lineHeight * TEXT_SCALE + LINE_GAP) - LINE_GAP;
    }
}
