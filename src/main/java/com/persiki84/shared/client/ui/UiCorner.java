package com.persiki84.shared.client.ui;

public final class UiCorner {
    public static final float LEVEL = 2.0f;
    public static final float LEVEL_MIN = 1.0f;
    public static final float LEVEL_MAX = 5.0f;
    public static final int BANDS = 8;

    // WHY: ступени 1-4 держат вершину угла там же, где её держит окружность радиуса r, и меняют только
    // WHY: разгон кривизны: суперэллипс степени n с длиной s·r, где s = (1 - 2^-0.5) / (1 - 2^(-1/n)).
    // WHY: 3.26 и 1.529 это длина сопряжения iOS 7 (UIBezierPath continuous corner), пятая ступень это
    // WHY: прежний плотный сквиркл 4.6 в квадрате r
    private static final float[] POWER = {2.35f, 3.26f, 4.0f, 5.0f, 4.6f};
    private static final float[] EXTENT = {1.147f, 1.529f, 1.841f, 2.263f, 1.0f};
    private static final float CIRCLE = 2.0f;
    private static final float TIGHTEST_SPAN = 0.02f;
    private static final float SPEED = 9.0f;

    private static final Smooth shown = new Smooth(SPEED);
    private static float target = LEVEL;
    private static float settled = Float.NaN;
    private static long stamp = -1L;
    private static float power = POWER[1];
    private static float extent = EXTENT[1];
    private static boolean hud;

    private UiCorner() {}

    public static void level(float value) {
        target = Math.max(LEVEL_MIN, Math.min(LEVEL_MAX, value));
    }

    public static float level() {
        return target;
    }

    // WHY: HUD рисуется своей прежней формой угла, а меню своей; защёлку ставит проход Gui и снимает
    // WHY: в конце, прежнее значение вызывающий возвращает сам
    public static boolean hud(boolean value) {
        boolean previous = hud;
        hud = value;
        return previous;
    }

    public static boolean hud() {
        return hud;
    }

    public static int band(float width, float height, float radius) {
        return Math.round(fit(width, height, radius) * BANDS);
    }

    public static float bandPower(int band) {
        return CIRCLE + (power - CIRCLE) * band / BANDS;
    }

    public static float power(float width, float height, float radius) {
        return bandPower(band(width, height, radius));
    }

    public static float reach(float width, float height, float radius) {
        float share = fit(width, height, radius);
        return radius * (1.0f + (extent - 1.0f) * share);
    }

    // WHY: где сопряжению не хватает места, форма стекает к окружности, а не упирается в полуширину:
    // WHY: капсула получает честный полукруг, и порог между плашкой и капсулой не щёлкает
    private static float fit(float width, float height, float radius) {
        settle();
        float half = Math.min(width, height) * 0.5f;
        if (radius <= 1.0e-4f || half <= 0.0f) return 1.0f;
        float room = half / Math.min(radius, half);
        return UiAnim.clamp01((room - 1.0f) / Math.max(extent - 1.0f, TIGHTEST_SPAN));
    }

    private static void settle() {
        long frame = UiFrame.frame();
        if (frame == stamp) return;

        stamp = frame;
        float level = shown.to(target, UiFrame.delta());
        if (level == settled) return;

        settled = level;
        shape(level);
        UiRender.rebakeMenuCorner();
    }

    private static void shape(float level) {
        float position = Math.max(0.0f, Math.min(POWER.length - 1.0f, level - LEVEL_MIN));
        int lower = Math.min(POWER.length - 2, (int) position);
        float blend = position - lower;
        power = POWER[lower] + (POWER[lower + 1] - POWER[lower]) * blend;
        extent = EXTENT[lower] + (EXTENT[lower + 1] - EXTENT[lower]) * blend;
    }
}
