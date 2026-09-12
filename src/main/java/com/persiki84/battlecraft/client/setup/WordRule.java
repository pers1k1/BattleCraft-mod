package com.persiki84.battlecraft.client.setup;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;

public final class WordRule {
    private static final float IDLE = -1.0f;
    private static final float STEP = 0.72f;
    private static final float DRAW = 0.46f;
    private static final float WIPE = 0.34f;
    private static final float SHINE = 0.55f;
    private static final float HOLD = 0.9f;
    private static final float FADE = 0.7f;

    private final int words;

    private float elapsed = IDLE;
    private long stamp = -1L;

    public WordRule(int words) {
        this.words = Math.max(1, words);
    }

    public float seconds() {
        return lit() + HOLD + FADE;
    }

    private float lit() {
        return (words - 1) * STEP + DRAW + SHINE;
    }

    // WHY: партитура заканчивается тем же, чем шла: черта и её свет уходят затуханием, а не обрывом
    public float fade() {
        if (elapsed < 0.0f) return 0.0f;
        return 1.0f - UiAnim.easeOut(UiAnim.clamp01((elapsed - lit() - HOLD) / FADE));
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
        if (elapsed >= seconds()) elapsed = IDLE;
    }

    public float drawn(int index) {
        if (elapsed < 0.0f) return 0.0f;
        return UiAnim.easeOut(UiAnim.clamp01((elapsed - index * STEP) / DRAW));
    }

    // WHY: линия предыдущего слова уходит с правого края, откуда росла, ровно когда начинает
    // WHY: расти линия следующего: подчёркивание читается как одна бегущая по фразе черта
    public float wiped(int index) {
        if (elapsed < 0.0f || index >= words - 1) return 0.0f;
        return UiAnim.easeOut(UiAnim.clamp01((elapsed - (index + 1) * STEP) / WIPE));
    }

    public float shine() {
        if (elapsed < 0.0f) return 0.0f;
        return UiAnim.easeOut(UiAnim.clamp01((elapsed - (words - 1) * STEP - DRAW) / SHINE)) * fade();
    }
}
