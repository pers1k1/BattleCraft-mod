package com.persiki84.battlecraft.client.custom;

import com.google.gson.JsonObject;
import com.persiki84.shared.JsonRead;
import com.persiki84.shared.client.font.FontShape;

// WHY: свечение глифов общее для всех начертаний, а «Шёлк» задуман с мягким ореолом шрифта острова
// WHY: Glass MediaPlayer Island (кольцо копий по 3 % на 0.7 единицы, без подсветки цвета), а «Резкий»
// WHY: без ореола вовсе. Выбравшему шрифт свечение ставится под него, ползунки дают вернуть своё
public final class FontLook {
    private static final float SILK_GLOW_ALPHA = 0.03f;
    private static final float SILK_GLOW_SPREAD = 0.7f;
    private static final float SILK_GLOW_LIFT = 0.0f;
    private static final String FONT = "font";
    private static final String VALUES = "values";

    private FontLook() {}

    public static void choose(FontShape shape) {
        Customization.font(shape);
        if (shape == FontShape.SHARP) Customization.glass(GlassKey.GLOW_ALPHA, 0.0f);
        if (shape != FontShape.SILK) return;

        Customization.glass(GlassKey.GLOW_ALPHA, SILK_GLOW_ALPHA);
        Customization.glass(GlassKey.GLOW_SPREAD, SILK_GLOW_SPREAD);
        Customization.glass(GlassKey.GLOW_LIFT, SILK_GLOW_LIFT);
    }

    // WHY: владелец перевёл всех на «Шёлк» один раз (05.10.2026): старый выбор в файле стоит сильнее
    // WHY: дефолта, поэтому без переписи файла шрифт сменился бы только у новых игроков
    static void migrate(ConfigDoc doc) {
        doc.openSection(ConfigSection.INTERFACE).addProperty(FONT, FontShape.SILK.id());
        JsonObject values = JsonRead.open(doc.openSection(ConfigSection.GLASS), VALUES);
        values.addProperty(GlassKey.GLOW_ALPHA.id(), SILK_GLOW_ALPHA);
        values.addProperty(GlassKey.GLOW_SPREAD.id(), SILK_GLOW_SPREAD);
        values.addProperty(GlassKey.GLOW_LIFT.id(), SILK_GLOW_LIFT);
    }
}
