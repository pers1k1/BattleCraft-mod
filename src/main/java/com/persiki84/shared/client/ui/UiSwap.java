package com.persiki84.shared.client.ui;

import net.minecraft.network.chat.Component;

public final class UiSwap {
    public static final float LIFT = 4.0f;
    private static final float SPEED = 14.0f;
    private static final float SETTLED = 0.999f;
    private static final long LIVE_MS = 300L;

    private final Smooth phase = new Smooth(1.0f, SPEED);
    private Component current;
    private Component outgoing;
    private String shownText;
    private long changedAt;

    public float advance(Component now, float delta) {
        track(now);
        return phase.to(1.0f, delta);
    }

    public Component outgoing() {
        return phase.get() < SETTLED ? outgoing : null;
    }

    public void take(UiSwap older) {
        phase.take(older.phase);
        current = older.current;
        outgoing = older.outgoing;
        shownText = older.shownText;
        changedAt = older.changedAt;
    }

    private void track(Component now) {
        String text = now.getString();
        if (text.equals(shownText)) {
            current = now;
            return;
        }
        long time = System.currentTimeMillis();
        boolean first = shownText == null;
        boolean live = time - changedAt < LIVE_MS;
        outgoing = first || live ? null : current;
        phase.snap(first || live ? 1.0f : 0.0f);
        changedAt = first ? 0L : time;
        shownText = text;
        current = now;
    }
}
