package com.persiki84.shared.client.ui;

import com.persiki84.shared.client.font.MsdfFontSets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class UiTitle {
    private static final float PAD = 13.0f;
    private static final float PAD_Y = 6.0f;
    private static final float TITLE_PAD_Y = 6.0f;
    private static final float PLATE_ALPHA = 1.0f;

    // WHY: высота заглавных title.ttf по контурам: кириллица 728 на 1000 единиц эма (OS/2 sCapHeight),
    // WHY: латиница 688. Плашка держит именно заглавные: ванильная высота строки 9 к титульной гарнитуре
    // WHY: отношения не имеет, её буквы втрое крупнее, и плашка по lineHeight выходила ниже самих букв
    private static final float CYRILLIC_CAP = 0.728f;
    private static final float LATIN_CAP = 0.688f;
    private static final int CYRILLIC_FIRST = 0x0400;
    private static final int CYRILLIC_LAST = 0x04FF;
    // WHY: атлас и растр титульной гарнитуры кладут эм в 25.25 единиц (MsdfFontSets.TITLE_EM),
    // WHY: а базовую линию на 7 единиц ниже точки строки: BASELINE 10 минус ванильные 3 из BakedGlyph.render
    private static final float FIELD_EM = 25.25f;
    private static final float FIELD_BASELINE = 7.0f;
    // WHY: запасной путь это ванильный TTF из title*.json: size 28.4 задаёт высоту hhea (969 + 241
    // WHY: на 1000 единиц), то есть эм 28.4 / 1.21, а базовая линия стоит на ascent минус ванильные 3.
    // WHY: Сдвиг shift 0.5 TrueTypeGlyphProvider учитывает дважды: в рамке растра (stbtt_GetGlyphBitmapBoxSubpixel)
    // WHY: и ещё раз в bearingY, поэтому буквы опускаются на целую единицу, а не на половину
    private static final float TTF_EM = 28.4f * 1000.0f / 1210.0f;
    private static final float TTF_BASELINE = 28.4f * 969.0f / 1210.0f - 3.0f + 0.5f * 2.0f;

    private static Component capSource;
    private static float capShare = CYRILLIC_CAP;

    private UiTitle() {}

    public static void render(GuiGraphics graphics, Font font, Component title, float centerX, float top,
                              float scale, float tracking, int color) {
        plate(graphics, font, UiRender.measureTracked(graphics, font, title, scale, tracking), centerX, top, scale);
        UiRender.textTracked(graphics, font, title, centerX, top, scale, tracking, color);
    }

    public static float title(GuiGraphics graphics, Font font, Component title, float centerX, float top,
                              float scale, float tracking, int color) {
        float span = UiRender.measureTitle(graphics, font, title, scale, tracking);
        if (span <= 0.0f) return top;

        float drawn = UiRender.titleScale(graphics, scale);
        float cap = capShare(title) * fieldEm() * drawn;
        float height = cap + TITLE_PAD_Y * 2.0f;
        float width = span + PAD * 2.0f;
        UiGlass.panel(graphics, centerX - width / 2.0f, top, width, height, UiMetrics.radius(height), PLATE_ALPHA);

        float baseline = top + TITLE_PAD_Y + cap;
        UiRender.textTitle(graphics, font, title, centerX, baseline - baselineDrop() * drawn, scale, tracking, color);
        return top + height;
    }

    // WHY: заголовок экрана приходит одним и тем же компонентом каждый кадр, поэтому строка
    // WHY: разбирается только при смене ссылки, а не getString() на кадр
    private static float capShare(Component title) {
        if (title == capSource) return capShare;
        capSource = title;
        capShare = scriptCap(title.getString());
        return capShare;
    }

    private static float scriptCap(String text) {
        for (int index = 0; index < text.length(); index++) {
            char letter = text.charAt(index);
            if (letter >= CYRILLIC_FIRST && letter <= CYRILLIC_LAST) return CYRILLIC_CAP;
        }
        return LATIN_CAP;
    }

    private static float fieldEm() {
        return MsdfFontSets.ready() ? FIELD_EM : TTF_EM;
    }

    private static float baselineDrop() {
        return MsdfFontSets.ready() ? FIELD_BASELINE : TTF_BASELINE;
    }

    private static void plate(GuiGraphics graphics, Font font, float span, float centerX, float top, float scale) {
        if (span <= 0.0f) return;

        float textHeight = font.lineHeight * scale;
        float height = textHeight + PAD_Y * 2.0f;
        float width = span + PAD * 2.0f;

        UiGlass.panel(graphics, centerX - width / 2.0f, top - PAD_Y, width, height,
                UiMetrics.radius(height), PLATE_ALPHA);
    }
}
