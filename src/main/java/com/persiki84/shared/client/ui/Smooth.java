package com.persiki84.shared.client.ui;

public final class Smooth {
    private float value;
    private float speed;
    private boolean primed;

    public Smooth(float initial, float speed) {
        this.value = initial;
        this.speed = speed;
        this.primed = true;
    }

    public Smooth(float speed) {
        this.value = 0.0f;
        this.speed = speed;
        this.primed = false;
    }

    public float to(float target, float delta) {
        if (!primed) {
            value = target;
            primed = true;
            return value;
        }
        value = UiAnim.approach(value, target, speed, delta);
        return value;
    }

    public float to(float target, float customSpeed, float delta) {
        if (!primed) {
            value = target;
            primed = true;
            return value;
        }
        value = UiAnim.approach(value, target, customSpeed, delta);
        return value;
    }

    public float get() {
        return value;
    }

    public void snap(float target) {
        value = target;
        primed = true;
    }

}
