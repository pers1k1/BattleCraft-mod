package com.persiki84.shared.client.ui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;

public final class UiReveal {
    public static final float ENTER = 0.0f;
    public static final float BURN = 1.0f;
    public static final float FOCUS = 2.0f;
    public static final float FROST = 3.0f;
    public static final float FROST_LEAVE = 3.5f;

    private static final float ENTER_SECONDS = 0.85f;
    private static final float MAX_STEP = 1.0f / 15.0f;
    // WHY: короткий вход (иней, 0.17 с) проскакивал за кадр-два: первый кадр после открытия экрана
    // WHY: приходит с задержкой инициализации. Шаг зажат долей длины, и любой вход длится хотя бы
    // WHY: шесть кадров, а первый кадр начинается с нуля
    private static final float MIN_FRAMES = 6.0f;
    private static final float DRAG_LIMIT = 2.5f;

    private static boolean allowed = true;
    private static boolean probing;

    private float entered;
    private float span = ENTER_SECONDS;
    private long stamp = -1L;

    private int frames;
    private long startedAt;
    private boolean opened;
    private boolean closed;

    public static void allow(boolean value) {
        allowed = value;
    }

    public static void probe(boolean value) {
        probing = value;
    }

    public static boolean enabled() {
        return allowed && UiAssemble.ready();
    }

    // WHY: сила линзы идёт гладкой ступенькой по фазе всей анимации: на обоих концах без излома,
    // WHY: поэтому в покое ровно единица, а на уходе та же кривая берётся от обратной фазы
    public static float glassPresence(float phase) {
        float t = UiAnim.clamp01(phase);
        return t * t * (3.0f - 2.0f * t);
    }

    // WHY: та же кривая, что frostCloseOpacity в ui_assemble.fsh: всё, что гаснет вместе с телом
    // WHY: уходящей панели (ореол окна палитры), обязано гаснуть с ним в такт
    public static float frostLeaveOpacity(float phase) {
        float t = UiAnim.clamp01(phase);
        float kept = 1.0f - t * t;
        return kept * kept;
    }

    public void pace(float enterSeconds) {
        span = Math.max(MAX_STEP, enterSeconds);
    }

    // WHY: экран мода держит свой автомат полем и умирает вместе с ним, а ванильные экраны ведёт
    // WHY: один общий: новый экран заводит тот же автомат заново, а не выделяет ещё один в кадре
    public void restart() {
        entered = 0.0f;
        stamp = -1L;
        frames = 0;
        startedAt = 0L;
        opened = false;
        closed = false;
    }

    public void advance() {
        long frame = UiFrame.frame();
        if (frame == stamp) return;
        stamp = frame;

        entered += frames == 0 ? 0.0f : Math.min(Math.min(MAX_STEP, span / MIN_FRAMES), UiFrame.delta());
        frames++;
        if (dragging()) entered = span;
    }

    private boolean dragging() {
        if (startedAt == 0L) return false;
        return System.currentTimeMillis() - startedAt > (long) (span * DRAG_LIMIT * 1000.0f);
    }

    public void report(String owner, boolean staged) {
        if (!probing) return;

        if (!opened) {
            opened = true;
            startedAt = System.currentTimeMillis();
            LogUtils.getLogger().info("[battlecraft] reveal {} begin span={} gui={} size={}x{}",
                    owner, span, Minecraft.getInstance().getWindow().getGuiScale(),
                    Minecraft.getInstance().getWindow().getWidth(),
                    Minecraft.getInstance().getWindow().getHeight());
        }
        if (active()) {
            LogUtils.getLogger().info("[battlecraft] reveal {} f={} wall={}ms delta={}ms phase={} staged={}",
                    owner, frames, System.currentTimeMillis() - startedAt,
                    Math.round(UiFrame.delta() * 1000.0f), phase(), staged);
            return;
        }
        if (closed) return;
        closed = true;
        LogUtils.getLogger().info("[battlecraft] reveal {} done frames={} wall={}ms",
                owner, frames, System.currentTimeMillis() - startedAt);
    }

    public boolean active() {
        return enabled() && entered < span;
    }

    public float phase() {
        return UiAnim.clamp01(entered / span);
    }

    public float seconds() {
        return entered;
    }
}
