package com.persiki84.battlecraft.client.loading;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlow;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class LoadingArt {
    public static final float UNKNOWN = -1.0f;

    private static final float MARK_SHARE = 0.19f;
    private static final float MARK_CENTER_ALONE = 0.44f;
    private static final float MARK_CENTER_CROWDED = 0.24f;
    private static final int MARK_SCALE_MIN = 2;
    private static final int MARK_SCALE_MAX = 12;

    private static final float BAR_GAP = 15.0f;
    private static final float BAR_HEIGHT = 4.0f;
    private static final float BAR_SPAN_MAX = 240.0f;
    private static final float BAR_SPAN_SHARE = 0.5f;
    private static final float BAR_TRACK_ALPHA = 0.22f;
    private static final float BAR_MIN_FILL = 4.0f;
    private static final float BAR_GLOW = 0.95f;
    private static final float TIP_SHARE = 2.7f;
    private static final float TIP_MIN = 0.62f;
    private static final float TIP_PERIOD = 1.5f;
    private static final float BREATH_SHARE = 0.22f;

    private static final float DRIFT_PERIOD = 2.1f;
    private static final float DRIFT_SHARE = 0.22f;

    private static final int BLOOM_RINGS = 7;
    private static final float BLOOM_REACH = 7.0f;
    private static final float BLOOM_ALPHA = 0.14f;

    private static final float ASSEMBLE_SECONDS = 1.1f;
    private static final float NOTE_GAP = 9.0f;
    private static final float NOTE_SCALE = 0.85f;
    private static final float NOTE_TRACKING = 0.4f;
    private static final float PERCENT_SCALE = 0.85f;

    private LoadingArt() {}

    // WHY: полоса стоит сразу под меткой, а не по нижнему краю кадра: разнесённые по экрану
    // WHY: голова и прогресс читаются как две несвязанные вещи вместо одного узла
    public static float paint(GuiGraphics graphics, int width, int height, Component note,
                              float progress, float seconds, float age, boolean crowded) {
        int scale = markScale(height);
        float center = height * (crowded ? MARK_CENTER_CROWDED : MARK_CENTER_ALONE);
        int top = Math.round(center - MarkPaint.height(scale) / 2.0f);
        int left = Math.round((width - MarkPaint.width(scale)) / 2.0f);
        MarkPaint.paint(graphics, left, top, scale, assembly(age), seconds);

        float span = Math.min(BAR_SPAN_MAX, width * BAR_SPAN_SHARE);
        float barLeft = (width - span) / 2.0f;
        float barY = top + MarkPaint.height(scale) + BAR_GAP;
        bar(graphics, barLeft, barY, span, progress, seconds);
        if (note == null) return barY + BAR_HEIGHT;

        float noteY = barY + BAR_HEIGHT + NOTE_GAP;
        caption(graphics, barLeft, noteY, span, note, progress);
        return noteY + font().lineHeight * NOTE_SCALE;
    }

    // WHY: сборка идёт по своему времени, а не по прогрессу: доля загрузки застревает на нуле
    // WHY: и на долгих стадиях, и метка на таком экране просто не появлялась бы
    private static float assembly(float age) {
        return UiAnim.clamp01(age / ASSEMBLE_SECONDS);
    }

    private static int markScale(int height) {
        if (!LoadingMark.ready()) return MARK_SCALE_MIN;

        int wanted = Math.round(height * MARK_SHARE / LoadingMark.height());
        return Math.max(MARK_SCALE_MIN, Math.min(MARK_SCALE_MAX, wanted));
    }

    private static void caption(GuiGraphics graphics, float left, float y, float span,
                                Component note, float progress) {
        UiRender.textTrackedLeft(graphics, font(), note, left, y, NOTE_SCALE, NOTE_TRACKING,
                UiAccent.textDim());
        if (progress < 0.0f) return;

        UiRender.textRight(graphics, font(), Component.literal(percent(progress)), left + span, y,
                PERCENT_SCALE, UiAccent.text(), false);
    }

    private static String percent(float progress) {
        return Math.round(UiAnim.clamp01(progress) * 100.0f) + "%";
    }

    // WHY: свет снимается тем же проходом, что и у текста: полоса рисуется в полотно, размывается
    // WHY: пирамидой и кладётся под себя сложением, поэтому ореол мягкий, а не восьмью копиями
    private static void bar(GuiGraphics graphics, float left, float y, float span,
                            float progress, float seconds) {
        if (span <= 0.0f) return;

        float radius = BAR_HEIGHT / 2.0f;
        float force = BAR_GLOW * (1.0f - BREATH_SHARE + BREATH_SHARE * breath(seconds));
        Piece piece = Piece.of(left, span, progress, seconds);

        UiRender.panel(graphics, left, y, span, BAR_HEIGHT, radius,
                UiTheme.alpha(UiAccent.faint(), BAR_TRACK_ALPHA));
        if (UiGlow.ready() && !BootPaint.booting()) {
            UiGlow.halo(graphics, force, () -> fill(graphics, piece, y, radius, seconds, true));
        } else {
            bloom(graphics, piece, y, seconds, force);
        }
        fill(graphics, piece, y, radius, seconds, false);
    }

    private static float breath(float seconds) {
        return 0.5f + 0.5f * (float) Math.sin(seconds / TIP_PERIOD * Math.PI * 2.0);
    }

    private static void fill(GuiGraphics graphics, Piece piece, float y, float radius,
                             float seconds, boolean lit) {
        UiRender.panel(graphics, piece.left(), y, piece.width(), BAR_HEIGHT, radius, UiAccent.color());
        if (lit) tip(graphics, piece.head(), y, seconds);
    }

    // WHY: кадр запуска игры идёт раньше RegisterShadersEvent: core-шейдеры регистрируются самой
    // WHY: перезагрузкой ресурсов, за ходом которой этот экран и следит, поэтому пирамида размытия
    // WHY: доступна только к её концу, а до тех пор ореол набирается кольцами по контуру полосы
    private static void bloom(GuiGraphics graphics, Piece piece, float y, float seconds, float force) {
        float size = tipSize(seconds);
        float centerX = piece.head();
        float centerY = y + BAR_HEIGHT / 2.0f;

        for (int ring = BLOOM_RINGS; ring >= 1; ring--) {
            float reach = BLOOM_REACH * ring / BLOOM_RINGS;
            int tint = UiTheme.alpha(UiAccent.color(), force * BLOOM_ALPHA / ring);
            UiRender.panel(graphics, piece.left() - reach, y - reach, piece.width() + reach * 2.0f,
                    BAR_HEIGHT + reach * 2.0f, BAR_HEIGHT / 2.0f + reach, tint);
            UiRender.dot(graphics, centerX, centerY, size / 2.0f + reach, tint);
        }
    }

    // WHY: огонёк на голове полосы живёт только в проходе свечения: нарисованный в самой полосе он
    // WHY: читался бы белым пятном, а через размытие даёт мягкий свет акцента и дышит вместе с ним
    private static void tip(GuiGraphics graphics, float x, float y, float seconds) {
        float size = tipSize(seconds);
        UiRender.panel(graphics, x - size / 2.0f, y + BAR_HEIGHT / 2.0f - size / 2.0f, size, size,
                size / 2.0f, UiAccent.color());
    }

    private static float tipSize(float seconds) {
        return BAR_HEIGHT * TIP_SHARE * (TIP_MIN + (1.0f - TIP_MIN) * breath(seconds));
    }

    // WHY: без доли прогресса полоса не врёт числом, а водит отрезком: маятник со сглаженными
    // WHY: концами читается как ожидание, а равномерный бег по кругу читается как зависание
    private record Piece(float left, float width) {
        private static Piece of(float left, float span, float progress, float seconds) {
            if (progress >= 0.0f) {
                return new Piece(left, Math.max(BAR_MIN_FILL, span * UiAnim.clamp01(progress)));
            }
            float phase = (float) (0.5 - Math.cos(seconds / DRIFT_PERIOD * Math.PI * 2.0) * 0.5);
            float piece = span * DRIFT_SHARE;
            return new Piece(left + (span - piece) * UiAnim.smoothstep(0.0f, 1.0f, phase), piece);
        }

        private float head() {
            return left + width;
        }
    }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }
}
