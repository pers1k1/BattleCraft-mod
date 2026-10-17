package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

// WHY: число на глазах игрока меняется так же, как таймер острова: стоящие цифры не трогаются,
// WHY: сменившиеся уходят вверх и гаснут, новые приходят снизу (у убывающего числа наоборот).
// WHY: Строка собирается только при смене значения, поэтому неизменное число в кадре ничего не
// WHY: аллоцирует. Если новое значение пришло раньше конца прошлого хода, ход сжимается под темп
// WHY: смен и подхватывается с текущего места: дистанция на бегу и процент захвата не копят хвост
public final class UiTicker {
    public static final float LEFT = 0.0f;
    public static final float CENTER = 0.5f;
    public static final float RIGHT = 1.0f;
    public static final Format PLAIN = Long::toString;
    public static final Format CLOCK = UiTicker::clock;
    public static final Format PERCENT = UiTicker::percent;

    private static final long NEVER = Long.MIN_VALUE;
    private static final long GAP_FRAMES = 3L;
    private static final float RELAXED_SECONDS = 1.0f;
    private static final float CADENCE_SHARE = 0.5f;
    private static final float PACE_FIT = 0.9f;
    private static final float HOLD_SLACK_UNITS = 6.0f;
    private static final float WIDTH_SPEED = 12.0f;

    private final UiMorphText morph = new UiMorphText();

    private float align = LEFT;
    private float tracking;
    private boolean rising;
    private float throttle;

    private long value = NEVER;
    private Format format;
    private boolean numeric;
    private boolean pending;
    private long pendingValue;
    private Format pendingFormat;
    private String pendingText;

    private long frame = NEVER;
    private float since = RELAXED_SECONDS;
    private float cadence = RELAXED_SECONDS;
    private float peak = -1.0f;
    private float glide;
    private long glideFrame = NEVER;
    private long woke = NEVER;
    long seen = NEVER;

    @FunctionalInterface
    public interface Format {
        String text(long value);
    }

    public UiTicker align(float share) {
        align = Math.max(0.0f, Math.min(1.0f, share));
        return this;
    }

    public UiTicker tracking(float units) {
        tracking = units;
        return this;
    }

    // WHY: число внутри строки обычного текста пишется тем же начертанием экрана, что textLine
    public UiTicker regular() {
        morph.regular();
        return this;
    }

    // WHY: часы, которые идут только вперёд, всегда вводят цифру снизу, даже на перемотке назад
    public UiTicker rising() {
        rising = true;
        return this;
    }

    // WHY: число, которое меняется каждый кадр (скорость, дистанция, FPS), иначе морфилось бы по
    // WHY: кадру и никогда не стояло бы целым. Значение копится и показывается не чаще раза в seconds
    public UiTicker throttle(float seconds) {
        throttle = Math.max(0.0f, seconds);
        return this;
    }

    public void set(long next) {
        set(next, PLAIN);
    }

    public void set(long next, Format shape) {
        touch();
        if (numeric && !pending && next == value && shape == format) return;
        if (pending && pendingFormat == shape && pendingText == null && next == pendingValue) return;
        if (deferred()) {
            hold(next, shape, null);
            return;
        }
        apply(next, shape, shape.text(next), true);
    }

    public void set(String next) {
        touch();
        if (!numeric && !pending && next.equals(morph.raw())) return;
        if (deferred()) {
            hold(0L, null, next);
            return;
        }
        apply(0L, null, next, false);
    }

    public void snap(long next) {
        snap(next, PLAIN);
    }

    public void snap(long next, Format shape) {
        touch();
        pending = false;
        boolean same = numeric && next == value && shape == format;
        morph.snap(same ? morph.raw() : shape.text(next));
        remember(next, shape, true);
    }

    public void snap(String next) {
        touch();
        pending = false;
        morph.snap(next);
        remember(0L, null, false);
    }

    public void reset() {
        morph.clear();
        value = NEVER;
        format = null;
        numeric = false;
        pending = false;
        pendingFormat = null;
        pendingText = null;
        frame = NEVER;
        since = RELAXED_SECONDS;
        cadence = RELAXED_SECONDS;
        peak = -1.0f;
        glideFrame = NEVER;
        woke = NEVER;
    }

    public String text() {
        return morph.raw();
    }

    public boolean moving() {
        return morph.morphing();
    }

    public float width(GuiGraphics graphics, Font font, float scale) {
        touch();
        return morph.measure(graphics, font, scale, tracking);
    }

    // WHY: место под число держит свой недавний максимум и отпускает его только на заметное сужение
    // WHY: и плавно: иначе каждая смена 9 на 10 и обратно толкала бы соседей по строке
    public float steadyWidth(GuiGraphics graphics, Font font, float scale) {
        float measured = width(graphics, font, scale);
        float slack = HOLD_SLACK_UNITS * scale;
        if (peak < 0.0f || measured > peak || measured < peak - slack) peak = measured;
        if (glideFrame == NEVER) {
            glide = peak;
        } else if (glideFrame != frame) {
            glide = UiAnim.approach(glide, peak, WIDTH_SPEED, UiFrame.delta());
        }
        glideFrame = frame;
        return glide;
    }

    // WHY: anchorX - точка выравнивания: левый край, середина или правый край по align. У правого
    // WHY: выравнивания стоит хвост числа, у левого голова, поэтому старшие разряды не дёргаются
    public void draw(GuiGraphics graphics, Font font, float anchorX, float y, float scale, int color) {
        touch();
        if (UiRender.vanishing(color)) return;
        morph.drawAligned(graphics, font, anchorX, y, scale, tracking, align, color);
    }

    public void draw(GuiGraphics graphics, Font font, float anchorX, float y, float scale, int color, float alpha) {
        draw(graphics, font, anchorX, y, scale, UiTheme.alpha(color, alpha));
    }

    private boolean deferred() {
        return throttle > 0.0f && since < throttle && morph.raw().length() > 0;
    }

    private void hold(long next, Format shape, String text) {
        pending = true;
        pendingValue = next;
        pendingFormat = shape;
        pendingText = text;
    }

    private void release() {
        if (!pending || since < throttle) return;

        pending = false;
        if (pendingText != null) {
            apply(0L, null, pendingText, false);
        } else {
            apply(pendingValue, pendingFormat, pendingFormat.text(pendingValue), true);
        }
    }

    private void apply(long next, Format shape, String text, boolean number) {
        pending = false;
        boolean upward = rising || !number || !numeric || next >= value;
        boolean changed = !text.equals(morph.raw());
        remember(next, shape, number);
        if (!changed) return;
        if (woke == frame) {
            morph.snap(text);
            return;
        }

        cadence = cadence + (Math.min(since, RELAXED_SECONDS) - cadence) * CADENCE_SHARE;
        float seconds = since >= RELAXED_SECONDS ? 0.0f : Math.min(since, cadence) * PACE_FIT;
        since = 0.0f;
        morph.pace(text, seconds, upward);
    }

    private void remember(long next, Format shape, boolean number) {
        value = next;
        format = shape;
        numeric = number;
    }

    // WHY: часы тикера идут от общего кадра интерфейса, по разу за кадр, сколько бы раз его ни
    // WHY: спросили. Тикер, которого не рисовали несколько кадров, досматривает ход сразу, а смена,
    // WHY: пришедшая в кадр возвращения, встаёт без хода: она случилась без зрителя
    private void touch() {
        long now = UiFrame.frame();
        seen = now;
        if (now == frame) return;

        boolean gap = frame == NEVER || now - frame > GAP_FRAMES;
        frame = now;
        if (gap) {
            woke = now;
            morph.snap(morph.raw());
            since = RELAXED_SECONDS;
            cadence = RELAXED_SECONDS;
            glideFrame = NEVER;
        } else {
            float delta = UiFrame.delta();
            morph.advance(delta);
            since = Math.min(RELAXED_SECONDS, since + delta);
        }
        release();
    }

    private static String clock(long seconds) {
        long total = Math.max(0L, seconds);
        long hours = total / 3600L;
        long minutes = total / 60L % 60L;
        long rest = total % 60L;
        if (hours > 0L) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest);
        return String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }

    private static String percent(long value) {
        return value + "%";
    }
}
