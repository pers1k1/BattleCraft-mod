package com.persiki84.shared.client.ui;

public final class UiAnim {

    private UiAnim() {}

    public static float approach(float current, float target, float speed, float delta) {
        if (delta <= 0.0f) return current;
        float next = target + (current - target) * (float) Math.exp(-speed * delta);
        return Math.abs(target - next) < 0.0008f ? target : next;
    }

    public static float easeOut(float t) {
        float x = clamp01(t);
        float inv = 1.0f - x;
        return 1.0f - inv * inv * inv;
    }

    public static float easeOutBack(float t) {
        float x = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float inv = x - 1.0f;
        return 1.0f + c3 * inv * inv * inv + c1 * inv * inv;
    }

    public static float pulse(float periodMs, float low, float high) {
        double phase = (System.currentTimeMillis() % (long) periodMs) / periodMs * Math.PI * 2.0;
        float wave = (float) (0.5 - Math.cos(phase) * 0.5);
        return low + (high - low) * wave;
    }

    public static float fadeIn(long since, float durationMs) {
        if (durationMs <= 0.0f) return 1.0f;
        return easeOut((System.currentTimeMillis() - since) / durationMs);
    }

    public static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 - edge0 <= 0.0f) return value < edge1 ? 0.0f : 1.0f;
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    public static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        return Math.min(value, 1.0f);
    }
}
