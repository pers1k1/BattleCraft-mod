package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class UiVital {
    private static final float PULSE_CALM = 1.05f;
    private static final float PULSE_RACING = 3.4f;
    private static final float PULSE_WINDED = 2.5f;
    private static final float PULSE_RUSH = 3.8f;
    private static final float EXERTION_RISE = 2.6f;
    private static final float EXERTION_FALL = 0.55f;
    private static final float RUSH_RISE = 1.8f;
    private static final float RUSH_FALL = 0.5f;
    private static final float VIGOR_RISE = 6.0f;
    private static final float VIGOR_FALL = 3.2f;
    private static final float CRITICAL_RATIO = 0.3f;
    private static float roundness = 1.0f;
    private static float shading;

    private static final float CARD_LIFT = 0.15f;
    private static final float BEAT_LIFT = 0.22f;
    private static final float FLASH_LIFT = 0.12f;
    private static final float ALARM_TINT = 0.5f;
    private static final float TRACE_STEADY = 0.92f;
    private static final float URGENCY_STEADY = 0.35f;
    private static final float MORTAL_TINT = 0.72f;

    private static final Smooth flash = new Smooth(0.0f, 5.0f);
    private static final Smooth exertion = new Smooth(0.0f, EXERTION_RISE);
    private static final Smooth rush = new Smooth(0.0f, RUSH_RISE);
    private static final Smooth vigor = new Smooth(1.0f, VIGOR_FALL);

    private static float ratio = 1.0f;
    private static float phase;
    private static float spike;
    private static float beatsPerSecond = PULSE_CALM;
    private static float lastHealth = -1.0f;
    private static boolean throbbing;

    private UiVital() {}

    public static void advance(float health, float maxHealth, float effort, float drive, float delta) {
        float max = Math.max(1.0f, maxHealth);
        float current = UiAnim.clamp01(health / max);
        if (lastHealth >= 0.0f && health < lastHealth - 0.01f) {
            flash.snap(1.0f);
        }
        lastHealth = health;
        ratio = current;

        boolean stopped = health <= 0.0f;
        vigor.to(stopped ? 0.0f : 1.0f, stopped ? VIGOR_FALL : VIGOR_RISE, delta);
        flash.to(0.0f, delta);
        strain(stopped ? 0.0f : effort, stopped ? VIGOR_FALL : EXERTION_FALL, delta);
        surge(stopped ? 0.0f : drive, delta);
        beat(delta);
    }

    public static void rest(float delta) {
        lastHealth = -1.0f;
        ratio = 1.0f;
        vigor.to(1.0f, VIGOR_RISE, delta);
        flash.to(0.0f, delta);
        strain(0.0f, EXERTION_FALL, delta);
        surge(0.0f, delta);
        beat(delta);
    }

    public static void card(GuiGraphics graphics, float x, float y, float width, float height, float alpha) {
        card(graphics, x, y, width, height, UiMetrics.radius(height), alpha, 0.0f);
    }

    public static void card(GuiGraphics graphics, float x, float y, float width, float height,
                            float radius, float alpha) {
        card(graphics, x, y, width, height, radius, alpha, 0.0f);
    }

    public static void card(GuiGraphics graphics, float x, float y, float width, float height,
                            float radius, float alpha, float extraLift) {
        UiGlass.deep(graphics, x, y, width, height, radius * roundness, alpha, alpha,
                CARD_LIFT + lift() + extraLift, shaded(tint()));
    }

    public static void dress(float corner, float shade) {
        roundness = corner;
        shading = shade;
    }

    public static void undress() {
        roundness = 1.0f;
        shading = 0.0f;
    }

    private static int shaded(int base) {
        if (shading <= 0.001f) return base;
        return UiTheme.withAlpha(UiTheme.BLACK, Math.max(shading, ((base >>> 24) & 0xFF) / 255.0f));
    }

    public static void cardTinted(GuiGraphics graphics, float x, float y, float width, float height,
                                  float radius, float alpha, float extraLift, int tint) {
        UiGlass.deep(graphics, x, y, width, height, radius * roundness, alpha, alpha,
                CARD_LIFT + lift() + extraLift, shaded(tint));
    }

    public static void throbbing(boolean value) {
        throbbing = value;
    }

    // WHY: с выключенной пульсацией удар сердца отдаёт постоянную полную величину, а не мерцает от неё до единицы
    private static float beatGain(float steady) {
        return throbbing ? steady + (1.0f - steady) * spike : 1.0f;
    }

    private static float throb() {
        return throbbing ? spike : 0.0f;
    }

    public static float traceGain() {
        return beatGain(TRACE_STEADY);
    }

    public static float lift() {
        return FLASH_LIFT * flash.get() + BEAT_LIFT * throb();
    }

    public static int tint() {
        return UiTheme.withAlpha(UiPalette.alert(), urgency() * ALARM_TINT);
    }

    public static float phase() {
        return phase;
    }

    public static float exertion() {
        return exertion.get();
    }

    public static float rush() {
        return rush.get();
    }

    public static float beatsPerSecond() {
        return beatsPerSecond;
    }

    public static float secondsToBeat() {
        return UiPulse.untilSpike(phase) / Math.max(0.05f, beatsPerSecond);
    }

    public static long beatIndex() {
        return UiPulse.spikeIndex(phase);
    }

    public static float vigor() {
        return vigor.get();
    }

    private static float urgency() {
        return Math.max(strain() * beatGain(URGENCY_STEADY), mortal() * MORTAL_TINT);
    }

    private static float strain() {
        if (ratio > CRITICAL_RATIO) return 0.0f;
        return 1.0f - ratio / CRITICAL_RATIO;
    }

    public static int tone(int calm) {
        float alarm = Math.max(strain() * 0.85f, mortal());
        return UiTheme.mix(UiTheme.mix(calm, UiPalette.alert(), alarm), UiTheme.WHITE, 0.30f * throb());
    }

    private static float mortal() {
        return 1.0f - vigor.get();
    }

    private static void strain(float effort, float fall, float delta) {
        float target = UiAnim.clamp01(effort);
        exertion.to(target, target > exertion.get() ? EXERTION_RISE : fall, delta);
    }

    private static void surge(float drive, float delta) {
        float target = UiAnim.clamp01(drive);
        rush.to(target, target > rush.get() ? RUSH_RISE : RUSH_FALL, delta);
    }

    private static void beat(float delta) {
        float driven = UiPulse.rate(ratio, exertion.get(), PULSE_CALM, PULSE_RACING, PULSE_WINDED);
        beatsPerSecond = Math.max(driven, PULSE_CALM + (PULSE_RUSH - PULSE_CALM) * rush.get());
        phase += beatsPerSecond * delta;
        if (phase > 1024.0f) phase -= 1024.0f;
        spike = UiPulse.spike(phase) * vigor.get();
    }
}
