package com.persiki84.battlecraft.client.custom;

import com.persiki84.shared.client.font.FontShape;

// WHY: свечение глифов общее для всех начертаний, а «Шёлк» задуман с мягким ореолом шрифта острова
// WHY: Glass MediaPlayer Island (кольцо копий по 3 % на 0.7 единицы, без подсветки цвета), а «Резкий»
// WHY: без ореола вовсе. Выбравшему шрифт свечение ставится под него, ползунки дают вернуть своё
public final class FontLook {
    private static final float SILK_GLOW_ALPHA = 0.03f;
    private static final float SILK_GLOW_SPREAD = 0.7f;
    private static final float SILK_GLOW_LIFT = 0.0f;

    private FontLook() {}

    public static void choose(FontShape shape) {
        Customization.font(shape);
        if (shape == FontShape.SHARP) Customization.glass(GlassKey.GLOW_ALPHA, 0.0f);
        if (shape != FontShape.SILK) return;

        Customization.glass(GlassKey.GLOW_ALPHA, SILK_GLOW_ALPHA);
        Customization.glass(GlassKey.GLOW_SPREAD, SILK_GLOW_SPREAD);
        Customization.glass(GlassKey.GLOW_LIFT, SILK_GLOW_LIFT);
    }
}
