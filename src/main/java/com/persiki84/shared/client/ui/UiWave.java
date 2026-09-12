package com.persiki84.shared.client.ui;

public final class UiWave {
    private static final float IDLE = -1.0f;
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private final float seconds;
    private final float spread;

    private float elapsed = IDLE;
    private long stamp = -1L;

    public UiWave(float seconds, float spread) {
        this.seconds = seconds;
        this.spread = spread;
    }

    public void begin() {
        elapsed = 0.0f;
        stamp = UiFrame.frame();
    }

    public void stop() {
        elapsed = IDLE;
    }

    public boolean running() {
        return elapsed >= 0.0f;
    }

    public void advance() {
        if (elapsed < 0.0f) return;

        long frame = UiFrame.frame();
        if (frame == stamp) return;

        stamp = frame;
        elapsed += UiFrame.delta();
        if (elapsed >= seconds) elapsed = IDLE;
    }
    // WHY: гребню нужно место в строке, а не только вес буквы: под ним бежит черта, и она обязана
    // WHY: идти той же дорогой, что и свет
    public float head(int count) {
        if (elapsed < 0.0f) return -spread;
        return elapsed / seconds * (count + spread * 2.0f) - spread;
    }

    public float weight(int index, int count) {
        if (elapsed < 0.0f || count <= 0) return 0.0f;

        float head = elapsed / seconds * (count + spread * 2.0f) - spread;
        float distance = Math.abs(index - head) / spread;
        if (distance >= 1.0f) return 0.0f;

        float shape = (float) Math.cos(distance * HALF_PI);
        return shape * shape;
    }
}
