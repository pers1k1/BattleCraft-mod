package com.persiki84.shared.client.ui;

public final class UiAccent {
    private static final float FILL_MIN_LIGHTNESS = 0.56f;
    private static final float TEXT_MIN_LIGHTNESS = 0.70f;
    private static final float DIM_TOWARD_FAINT = 0.45f;
    private static final float FAINT_TOWARD_FAINT = 0.72f;

    private static final float TEXT_TOWARD_ACCENT = 0.72f;
    private static final float TEXT_DIM_TOWARD_ACCENT = 0.58f;
    private static final float TEXT_FAINT_TOWARD_ACCENT = 0.42f;

    private static final float WASH_SECONDS = 0.44f;

    // WHY: приглушённый и тусклый обязаны отличаться от основного ровно настолько же, насколько
    // WHY: заводские отличаются друг от друга, иначе акцентный текст вырождается в один цвет
    private static final float DIM_STEP =
            UiOklab.lightness(UiTheme.TEXT_DIM) / UiOklab.lightness(UiTheme.TEXT);
    private static final float FAINT_STEP =
            UiOklab.lightness(UiTheme.TEXT_FAINT) / UiOklab.lightness(UiTheme.TEXT);

    private static int color = UiTheme.FILL;
    private static int dim = UiTheme.FILL_DIM;
    private static int faint = UiTheme.FILL_FAINT;
    private static int text = UiTheme.TEXT;
    private static int textDim = UiTheme.TEXT_DIM;
    private static int textFaint = UiTheme.TEXT_FAINT;

    private static int plainText = UiTheme.TEXT;
    private static int plainTextDim = UiTheme.TEXT_DIM;
    private static int plainTextFaint = UiTheme.TEXT_FAINT;
    private static int accentText = UiTheme.TEXT;
    private static int accentTextDim = UiTheme.TEXT_DIM;
    private static int accentTextFaint = UiTheme.TEXT_FAINT;

    // WHY: анимация стоит на самом выходе, а не в расчёте оттенков: производные считаются от
    // WHY: плотного цвета, и если бы они догоняли уже едущее значение, тройка текста разъезжалась бы
    private static final UiWash colorWash = new UiWash(WASH_SECONDS, UiTheme.FILL);
    private static final UiWash dimWash = new UiWash(WASH_SECONDS, UiTheme.FILL_DIM);
    private static final UiWash faintWash = new UiWash(WASH_SECONDS, UiTheme.FILL_FAINT);
    private static final UiWash textWash = new UiWash(WASH_SECONDS, UiTheme.TEXT);
    private static final UiWash textDimWash = new UiWash(WASH_SECONDS, UiTheme.TEXT_DIM);
    private static final UiWash textFaintWash = new UiWash(WASH_SECONDS, UiTheme.TEXT_FAINT);
    private static final UiWash plainTextWash = new UiWash(WASH_SECONDS, UiTheme.TEXT);
    private static final UiWash plainTextDimWash = new UiWash(WASH_SECONDS, UiTheme.TEXT_DIM);
    private static final UiWash plainTextFaintWash = new UiWash(WASH_SECONDS, UiTheme.TEXT_FAINT);
    private static final UiWash accentTextWash = new UiWash(WASH_SECONDS, UiTheme.TEXT);
    private static final UiWash accentTextDimWash = new UiWash(WASH_SECONDS, UiTheme.TEXT_DIM);
    private static final UiWash accentTextFaintWash = new UiWash(WASH_SECONDS, UiTheme.TEXT_FAINT);

    private static boolean inkFollowsAccent;

    private static Integer colorOverride;
    private static Integer textOverride;
    private static Integer textDimOverride;
    private static Integer textFaintOverride;

    private UiAccent() {}

    public static void set(int argb) {
        tint(UiTheme.withAlpha(argb, 1.0f), 1.0f);
        applyOverrides();
    }

    public static void reset() {
        color = UiTheme.FILL;
        dim = UiTheme.FILL_DIM;
        faint = UiTheme.FILL_FAINT;
        plainText = UiTheme.TEXT;
        plainTextDim = UiTheme.TEXT_DIM;
        plainTextFaint = UiTheme.TEXT_FAINT;
        accentText = UiTheme.TEXT;
        accentTextDim = UiTheme.TEXT_DIM;
        accentTextFaint = UiTheme.TEXT_FAINT;
        applyOverrides();
    }

    public static void advance(float delta) {
        colorWash.advance(delta);
        dimWash.advance(delta);
        faintWash.advance(delta);
        textWash.advance(delta);
        textDimWash.advance(delta);
        textFaintWash.advance(delta);
        plainTextWash.advance(delta);
        plainTextDimWash.advance(delta);
        plainTextFaintWash.advance(delta);
        accentTextWash.advance(delta);
        accentTextDimWash.advance(delta);
        accentTextFaintWash.advance(delta);
    }

    public static void inkFollowsAccent(boolean value) {
        inkFollowsAccent = value;
        applyOverrides();
    }

    public static void clearOverrides() {
        colorOverride = null;
        textOverride = null;
        textDimOverride = null;
        textFaintOverride = null;
    }

    public static void overrideColor(int argb) {
        colorOverride = argb;
        applyOverrides();
    }

    public static void overrideText(int argb) {
        textOverride = argb;
        applyOverrides();
    }

    public static void overrideTextDim(int argb) {
        textDimOverride = argb;
        applyOverrides();
    }

    public static void overrideTextFaint(int argb) {
        textFaintOverride = argb;
        applyOverrides();
    }

    // WHY: заливкам хватает светлоты 0.56, чтобы читаться на тёмном стекле, тексту нужна 0.70:
    // WHY: один общий порог либо выбеливал заливки, либо оставлял мелкие буквы неразборчивыми
    private static void tint(int solid, float share) {
        int fill = UiOklab.lift(solid, FILL_MIN_LIGHTNESS);
        int lit = UiOklab.lift(solid, TEXT_MIN_LIGHTNESS);

        color = UiTheme.withAlpha(fill, share);
        dim = UiTheme.withAlpha(UiTheme.mix(fill, UiTheme.FILL_FAINT, DIM_TOWARD_FAINT), share);
        faint = UiTheme.withAlpha(UiTheme.mix(fill, UiTheme.FILL_FAINT, FAINT_TOWARD_FAINT), share);
        plainText = UiTheme.mix(UiTheme.TEXT, lit, TEXT_TOWARD_ACCENT);
        plainTextDim = UiTheme.mix(UiTheme.TEXT_DIM, lit, TEXT_DIM_TOWARD_ACCENT);
        plainTextFaint = UiTheme.mix(UiTheme.TEXT_FAINT, lit, TEXT_FAINT_TOWARD_ACCENT);
        accentText = lit;
        accentTextDim = UiOklab.scaleLightness(lit, DIM_STEP);
        accentTextFaint = UiOklab.scaleLightness(lit, FAINT_STEP);
    }

    // WHY: выбранная в палитре альфа обязана доходить до заливок, поэтому оттенки считаются от плотного цвета
    private static void applyOverrides() {
        if (colorOverride != null) {
            tint(UiTheme.withAlpha(colorOverride, 1.0f), opacity(colorOverride));
        }
        text = inkFollowsAccent ? accentText : plainText;
        textDim = inkFollowsAccent ? accentTextDim : plainTextDim;
        textFaint = inkFollowsAccent ? accentTextFaint : plainTextFaint;

        if (textOverride != null) text = textOverride;
        if (textDimOverride != null) textDim = textDimOverride;
        if (textFaintOverride != null) textFaint = textFaintOverride;
        publish();
    }

    private static void publish() {
        colorWash.aim(color);
        dimWash.aim(dim);
        faintWash.aim(faint);
        textWash.aim(text);
        textDimWash.aim(textDim);
        textFaintWash.aim(textFaint);
        plainTextWash.aim(plainText);
        plainTextDimWash.aim(plainTextDim);
        plainTextFaintWash.aim(plainTextFaint);
        accentTextWash.aim(accentText);
        accentTextDimWash.aim(accentTextDim);
        accentTextFaintWash.aim(accentTextFaint);
    }

    private static float opacity(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0f;
    }

    public static int color() {
        return colorWash.get();
    }

    public static int dim() {
        return dimWash.get();
    }

    public static int faint() {
        return faintWash.get();
    }

    public static int text() {
        return textWash.get();
    }

    public static int textDim() {
        return textDimWash.get();
    }

    public static int textFaint() {
        return textFaintWash.get();
    }

    public static int plainText() {
        return plainTextWash.get();
    }

    public static int plainTextDim() {
        return plainTextDimWash.get();
    }

    public static int plainTextFaint() {
        return plainTextFaintWash.get();
    }

    public static int accentText() {
        return accentTextWash.get();
    }

    public static int accentTextDim() {
        return accentTextDimWash.get();
    }

    public static int accentTextFaint() {
        return accentTextFaintWash.get();
    }
}
