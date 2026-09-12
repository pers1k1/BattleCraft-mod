package com.persiki84.shared.client.ui;

public final class Toggle {
    private final Smooth alpha;
    private final long debounceMs;
    private boolean wanted;
    private long since;

    public Toggle(float speed, long debounceMs) {
        this.alpha = new Smooth(0.0f, speed);
        this.debounceMs = debounceMs;
    }

    public float update(boolean value, float delta) {
        long now = System.currentTimeMillis();
        if (value != wanted) {
            wanted = value;
            since = now;
        }
        boolean shown = alpha.get() >= 0.98f;
        boolean visible = value && (shown || now - since >= debounceMs);
        return alpha.to(visible ? 1.0f : 0.0f, delta);
    }

    public boolean live() {
        return wanted;
    }

    public boolean hidden() {
        return alpha.get() <= 0.02f;
    }

    public boolean cleared() {
        return !wanted && alpha.get() <= 0.02f;
    }
}
