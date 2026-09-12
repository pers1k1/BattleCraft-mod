package com.persiki84.shared.client.ui;

public enum UiCue {
    HOVER(Gate.HOVER, "hover", 0.34f, 1.0f, 45L),
    PRESS(Gate.PRESS, "click", 0.55f, 1.0f, 40L),
    TOGGLE_ON(Gate.PRESS, "toggle_on", 0.50f, 1.0f, 40L),
    TOGGLE_OFF(Gate.PRESS, "toggle_off", 0.50f, 1.0f, 40L),
    CONFIRM(Gate.PRESS, "confirm", 0.55f, 1.0f, 40L),
    DENY(Gate.PRESS, "deny", 0.55f, 1.0f, 40L),
    SLOT(Gate.SLOT, "click", 0.26f, 1.12f, 42L),
    NOTIFY(Gate.NOTIFY, "notify", 0.50f, 1.0f, 90L),
    ALERT(Gate.ALERT, "deny", 0.72f, 0.92f, 900L),
    SLIDE(Gate.SLIDE, "hover", 0.15f, 1.0f, 55L),
    SCREEN_OPEN(Gate.SCREEN, "drag_start", 0.40f, 1.0f, 140L),
    SCREEN_CLOSE(Gate.SCREEN, "drag_end", 0.38f, 1.0f, 140L),
    CHIP_UP(Gate.CHIP, "toggle_on", 0.30f, 1.08f, 130L),
    CHIP_DOWN(Gate.CHIP, "toggle_off", 0.30f, 1.0f, 130L);

    public enum Gate {
        HOVER,
        PRESS,
        SLOT,
        NOTIFY,
        ALERT,
        SLIDE,
        SCREEN,
        CHIP
    }

    private final Gate gate;
    private final String sample;
    private final float gain;
    private final float pitch;
    private final long intervalMs;

    UiCue(Gate gate, String sample, float gain, float pitch, long intervalMs) {
        this.gate = gate;
        this.sample = sample;
        this.gain = gain;
        this.pitch = pitch;
        this.intervalMs = intervalMs;
    }

    public Gate gate() {
        return gate;
    }

    public String sample() {
        return sample;
    }

    public float gain() {
        return gain;
    }

    public float pitch() {
        return pitch;
    }

    public long intervalMs() {
        return intervalMs;
    }
}
