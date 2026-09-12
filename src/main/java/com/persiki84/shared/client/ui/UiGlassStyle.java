package com.persiki84.shared.client.ui;

public final class UiGlassStyle {
    public static final float DENSITY = 0.52f;
    public static final float SURFACE_ALPHA = 1.0f;
    public static final float LIFT_GAIN = 1.0f;
    public static final float EDGE_PIXELS = 1.15f;
    public static final float BORDER = 0.0f;
    public static final float SOFTNESS = 0.0f;
    public static final float FRESNEL_RANGE = 30.0f;
    public static final float FRESNEL_HARD = 0.2f;
    public static final float FRESNEL_GLOW = 0.2f;
    public static final float SATURATION = 1.0f;
    public static final float BRIGHTNESS = 1.0f;
    public static final float DISSOLVE = 0.0f;
    public static final float DISSOLVE_CHAOS = 0.5f;
    public static final float DISSOLVE_DRIFT = 0.35f;
    public static final float SQUIRCLE = 4.6f;
    public static final float SWITCH_SQUIRCLE = 2.0f;
    public static final float SWITCH_LENS = 1.0f;
    public static final float CIRCLE_POWER = 2.0f;
    public static final float RADIUS_PANEL = 6.5f;
    public static final float RADIUS_CELL = 5.5f;

    public static final float BAND = 13.0f;
    public static final float PULL = 4.6f;
    public static final float FALLOFF = 1.35f;
    public static final float BEND_PROFILE = 2.0f;
    public static final float BEND_AIM = -1.0f;
    public static final float BEND_REACH = 3.5f;
    public static final float ZOOM = 0.022f;
    public static final int RINGS = 10;
    public static final int EDGE_RINGS = 6;
    public static final float DISPERSION = 0.35f;

    public static final int BLUR_SHRINK = 2;
    public static final int BLUR_LEVELS = 5;
    public static final float BLUR_DOWN = 1.2f;
    public static final float BLUR_UP = 0.7f;
    public static final float BLUR_POLISH = 0.6f;

    public static final float SWEEP_DEPTH = 3.2f;
    public static final float SWEEP_LIGHT = 0.42f;
    public static final float SWEEP_POWER = 7.0f;
    public static final float SWEEP_PERIOD = 1600.0f;

    public static final float GLOW_SPREAD = 0.22f;
    public static final float GLOW_ALPHA = 0.26f;
    public static final float GLOW_LIFT = 0.15f;
    public static final float TEXT_WEIGHT = 0.0f;

    private static boolean liquid = true;
    private static float density = DENSITY;
    private static float surfaceAlpha = SURFACE_ALPHA;
    private static float liftGain = LIFT_GAIN;
    private static float edgePixels = EDGE_PIXELS;
    private static float border = BORDER;
    private static float softness = SOFTNESS;
    private static float fresnelRange = FRESNEL_RANGE;
    private static float fresnelHard = FRESNEL_HARD;
    private static float fresnelGlow = FRESNEL_GLOW;
    private static float saturation = SATURATION;
    private static float brightness = BRIGHTNESS;
    private static float dissolve = DISSOLVE;
    private static float dissolveChaos = DISSOLVE_CHAOS;
    private static float dissolveDrift = DISSOLVE_DRIFT;
    private static float squircle = SQUIRCLE;
    private static float switchSquircle = SWITCH_SQUIRCLE;
    private static float switchLens = SWITCH_LENS;
    private static boolean switching;
    private static float radiusPanel = RADIUS_PANEL;
    private static float radiusCell = RADIUS_CELL;

    private static float band = BAND;
    private static float pull = PULL;
    private static float falloff = FALLOFF;
    private static float bendAim = BEND_AIM;
    private static float bendReach = BEND_REACH;
    private static float zoom = ZOOM;
    private static int rings = RINGS;
    private static int edgeRings = EDGE_RINGS;
    private static float dispersion = DISPERSION;

    private static int blurShrink = BLUR_SHRINK;
    private static int blurLevels = BLUR_LEVELS;
    private static float blurDown = BLUR_DOWN;
    private static float blurUp = BLUR_UP;
    private static float blurPolish = BLUR_POLISH;

    private static float sweepDepth = SWEEP_DEPTH;
    private static float sweepLight = SWEEP_LIGHT;
    private static float sweepPower = SWEEP_POWER;
    private static float sweepPeriod = SWEEP_PERIOD;

    private static float glowSpread = GLOW_SPREAD;
    private static float glowAlpha = GLOW_ALPHA;
    private static float glowLift = GLOW_LIFT;
    private static float textWeight = TEXT_WEIGHT;

    private UiGlassStyle() {}

    public static void liquid(boolean value) {
        liquid = value;
    }

    public static boolean liquid() {
        return liquid;
    }

    public static void reset() {
        resetMaterial();
        resetLens();
        resetBlur();
        resetAccents();
    }

    private static void resetMaterial() {
        density = DENSITY;
        surfaceAlpha = SURFACE_ALPHA;
        liftGain = LIFT_GAIN;
        edgePixels = EDGE_PIXELS;
        border = BORDER;
        softness = SOFTNESS;
        fresnelRange = FRESNEL_RANGE;
        fresnelHard = FRESNEL_HARD;
        fresnelGlow = FRESNEL_GLOW;
        saturation = SATURATION;
        brightness = BRIGHTNESS;
        dissolve = DISSOLVE;
        dissolveChaos = DISSOLVE_CHAOS;
        dissolveDrift = DISSOLVE_DRIFT;
        squircle = SQUIRCLE;
        switchSquircle = SWITCH_SQUIRCLE;
        switchLens = SWITCH_LENS;
        radiusPanel = RADIUS_PANEL;
        radiusCell = RADIUS_CELL;
        UiRender.rebakeSquircle();
        UiRender.rebakeSwitchShape();
    }

    private static void resetLens() {
        band = BAND;
        pull = PULL;
        falloff = FALLOFF;
        bendAim = BEND_AIM;
        bendReach = BEND_REACH;
        zoom = ZOOM;
        rings = RINGS;
        edgeRings = EDGE_RINGS;
        dispersion = DISPERSION;
    }

    private static void resetBlur() {
        blurShrink = BLUR_SHRINK;
        blurLevels = BLUR_LEVELS;
        blurDown = BLUR_DOWN;
        blurUp = BLUR_UP;
        blurPolish = BLUR_POLISH;
    }

    private static void resetAccents() {
        sweepDepth = SWEEP_DEPTH;
        sweepLight = SWEEP_LIGHT;
        sweepPower = SWEEP_POWER;
        sweepPeriod = SWEEP_PERIOD;
        glowSpread = GLOW_SPREAD;
        glowAlpha = GLOW_ALPHA;
        glowLift = GLOW_LIFT;
        textWeight = TEXT_WEIGHT;
    }

    public static void density(float value) {
        density = value;
    }

    public static void surfaceAlpha(float value) {
        surfaceAlpha = value;
    }

    public static void liftGain(float value) {
        liftGain = value;
    }

    public static void edgePixels(float value) {
        edgePixels = value;
    }

    public static void border(float value) {
        border = value;
    }

    public static void softness(float value) {
        softness = value;
    }

    public static void fresnelRange(float value) {
        fresnelRange = value;
    }

    public static void fresnelHard(float value) {
        fresnelHard = value;
    }

    public static void fresnelGlow(float value) {
        fresnelGlow = value;
    }

    public static void saturation(float value) {
        saturation = value;
    }

    public static void brightness(float value) {
        brightness = value;
    }

    public static void dissolve(float value) {
        dissolve = value;
    }

    public static void dissolveChaos(float value) {
        dissolveChaos = value;
    }

    public static void dissolveDrift(float value) {
        dissolveDrift = value;
    }

    public static void squircle(float value) {
        squircle = value;
        UiRender.rebakeSquircle();
    }

    public static void switchSquircle(float value) {
        switchSquircle = value;
        UiRender.rebakeSwitchShape();
    }

    public static void switchLens(float value) {
        switchLens = value;
    }

    // WHY: у переключателя своя форма угла и своя сила преломления, поэтому на время его отрисовки
    // WHY: стиль отвечает его значениями всем, кто читает ручки: и таблице дуги, и линзе
    public static boolean switching(boolean value) {
        boolean previous = switching;
        switching = value;
        return previous;
    }

    public static boolean switching() {
        return switching;
    }

    public static void radiusPanel(float value) {
        radiusPanel = value;
    }

    public static void radiusCell(float value) {
        radiusCell = value;
    }

    public static void band(float value) {
        band = value;
    }

    public static void pull(float value) {
        pull = value;
    }

    public static void falloff(float value) {
        falloff = value;
    }

    public static void bendAim(float value) {
        bendAim = value;
    }

    public static void bendReach(float value) {
        bendReach = value;
    }

    public static void zoom(float value) {
        zoom = value;
    }

    public static void rings(int value) {
        rings = value;
        edgeRings = Math.min(edgeRings, Math.max(1, value - 1));
    }

    public static void edgeRings(int value) {
        edgeRings = Math.min(value, Math.max(1, rings - 1));
    }

    public static void dispersion(float value) {
        dispersion = value;
    }

    public static void blurLevels(int value) {
        blurLevels = value;
    }

    public static void blurShrink(int value) {
        blurShrink = value;
    }

    public static void blurDown(float value) {
        blurDown = value;
    }

    public static void blurUp(float value) {
        blurUp = value;
    }

    public static void blurPolish(float value) {
        blurPolish = value;
    }

    public static void sweepDepth(float value) {
        sweepDepth = value;
    }

    public static void sweepLight(float value) {
        sweepLight = value;
    }

    public static void sweepPower(float value) {
        sweepPower = value;
    }

    public static void sweepPeriod(float value) {
        sweepPeriod = value;
    }

    public static void glowSpread(float value) {
        glowSpread = value;
    }

    public static void glowAlpha(float value) {
        glowAlpha = value;
    }

    public static void glowLift(float value) {
        glowLift = value;
    }

    public static void textWeight(float value) {
        textWeight = value;
    }

    public static float density() {
        return density;
    }

    public static float surfaceAlpha() {
        return surfaceAlpha;
    }

    public static float liftGain() {
        return liftGain;
    }

    public static float edgePixels() {
        return edgePixels;
    }

    public static float border() {
        return border;
    }

    public static float softness() {
        return softness;
    }

    public static float fresnelRange() {
        return fresnelRange;
    }

    public static float fresnelHard() {
        return fresnelHard;
    }

    public static float fresnelGlow() {
        return fresnelGlow;
    }

    public static float saturation() {
        return saturation;
    }

    public static float brightness() {
        return brightness;
    }

    public static float dissolve() {
        return dissolve;
    }

    public static float dissolveChaos() {
        return dissolveChaos;
    }

    public static float dissolveDrift() {
        return dissolveDrift;
    }

    public static float squircle() {
        return squircle;
    }

    public static float switchSquircle() {
        return switchSquircle;
    }

    public static float switchLens() {
        return switchLens;
    }

    public static boolean circular(float width, float height, float radius) {
        float smallest = Math.min(width, height);
        if (smallest <= 0.0f) return false;
        return radius >= smallest * 0.49f && Math.abs(width - height) <= smallest * 0.02f;
    }

    public static float shapePower(float width, float height, float radius) {
        if (switching) return switchSquircle;
        return circular(width, height, radius) ? CIRCLE_POWER : squircle;
    }

    public static float radiusPanel() {
        return radiusPanel;
    }

    public static float radiusCell() {
        return radiusCell;
    }

    public static float band() {
        return band;
    }

    // WHY: у переключателя не своё число, а доля общей силы: единица это ровно то же искажение,
    // WHY: что у панелей, поэтому заводской вид совпадает с остальным стеклом при любом пресете
    public static float pull() {
        return switching ? pull * switchLens : pull;
    }

    public static float falloff() {
        return falloff;
    }

    public static float bendProfile() {
        return falloff / FALLOFF * BEND_PROFILE;
    }

    public static float bendAim() {
        return bendAim;
    }

    public static float bendReach() {
        return bendReach;
    }

    // WHY: кольцевой путь не знает про BEND_REACH шейдера, поэтому дальность приходит долей
    // WHY: от заводской: на дефолте это ровно единица и прежний вид сохраняется бит в бит
    public static float bendScale() {
        return bendAim * bendReach / BEND_REACH;
    }

    public static float zoom() {
        return zoom;
    }

    public static int rings() {
        return rings;
    }

    public static int edgeRings() {
        return edgeRings;
    }

    public static float dispersion() {
        return dispersion;
    }

    public static int blurLevels() {
        return blurLevels;
    }

    public static float blurDown() {
        return blurDown;
    }

    public static int blurShrink() {
        return blurShrink;
    }

    public static float blurUp() {
        return blurUp;
    }

    public static float blurPolish() {
        return blurPolish;
    }

    public static float sweepDepth() {
        return sweepDepth;
    }

    public static float sweepLight() {
        return sweepLight;
    }

    public static float sweepPower() {
        return sweepPower;
    }

    public static float sweepPeriod() {
        return sweepPeriod;
    }

    public static float glowSpread() {
        return glowSpread;
    }

    public static float glowAlpha() {
        return glowAlpha;
    }

    public static float glowLift() {
        return glowLift;
    }

    public static float textWeight() {
        return textWeight;
    }
}
