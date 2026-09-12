package com.persiki84.shared.client.ui;

public final class Spring {
    private static final float SUBSTEP = 1.0f / 240.0f;
    private static final int MAX_SUBSTEPS = 32;
    private static final float REST_DISTANCE = 0.0004f;
    private static final float REST_SPEED = 0.0025f;

    private final float stiffness;
    private final float damping;

    private float value;
    private float velocity;
    private boolean primed;

    public Spring(float response, float dampingRatio) {
        float frequency = (float) (Math.PI * 2.0) / Math.max(0.02f, response);
        this.stiffness = frequency * frequency;
        this.damping = 2.0f * dampingRatio * frequency;
    }

    public Spring(float response, float dampingRatio, float initial) {
        this(response, dampingRatio);
        this.value = initial;
        this.primed = true;
    }

    public float to(float target, float delta) {
        if (!primed) {
            value = target;
            primed = true;
            return value;
        }
        if (delta <= 0.0f) return value;

        int steps = Math.min(MAX_SUBSTEPS, Math.max(1, (int) Math.ceil(delta / SUBSTEP)));
        float step = delta / steps;
        for (int i = 0; i < steps; i++) {
            velocity += ((target - value) * stiffness - velocity * damping) * step;
            value += velocity * step;
        }

        if (Math.abs(target - value) < REST_DISTANCE && Math.abs(velocity) < REST_SPEED) {
            value = target;
            velocity = 0.0f;
        }
        return value;
    }

    public float get() {
        return value;
    }

    public float velocity() {
        return velocity;
    }

    public void snap(float target) {
        value = target;
        velocity = 0.0f;
        primed = true;
    }

}
