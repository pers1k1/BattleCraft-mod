package com.persiki84.shared.client.ui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;

public final class UiReveal {
    public static final float ENTER = 0.0f;
    public static final float BURN = 1.0f;
    public static final float FOCUS = 2.0f;

    private static final float ENTER_SECONDS = 0.85f;
    private static final float MAX_STEP = 1.0f / 15.0f;
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

        entered += Math.min(MAX_STEP, UiFrame.delta());
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
