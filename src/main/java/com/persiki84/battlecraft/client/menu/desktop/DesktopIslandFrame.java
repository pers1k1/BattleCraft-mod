package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.island.IslandGlyph;
import com.persiki84.shared.client.ui.UiRender;

// WHY: таблетка и карточка раскладываются каждая в своём покое, а форма между ними меняется
// WHY: пустой: содержимое не перетекает, а уходит в размытие и приходит из него. Поэтому у
// WHY: каждой из раскладок свои места, и ни одна строка не ведётся лерпом координат и кегля
final class DesktopIslandFrame {
    static final float PILL_HEIGHT = 12.0f;
    static final float CARD_HEIGHT = 82.0f;
    static final float SCREEN_EDGE = 8.0f;

    private static final float PILL_PAD = 3.5f;
    private static final float CARD_PAD = 9.0f;
    private static final float FACE = 9.0f;
    private static final float ART = 30.0f;
    private static final float PILL_GAP = 5.0f;
    private static final float CARD_GAP = 7.0f;
    private static final float MAX_RADIUS = 19.0f;
    private static final float TITLE_TOP = 4.0f;
    private static final float ARTIST_TOP = 16.0f;
    private static final float BAR_CENTER = 50.0f;
    private static final float ROW_CENTER = 66.0f;
    private static final float TIME_GAP = 5.0f;
    private static final float TITLE_MIN = 40.0f;
    private static final float TITLE_MAX = 92.0f;
    private static final float TITLE_LEAST = 16.0f;
    private static final float CARD_TEXT_MIN = 92.0f;
    private static final float CARD_TEXT_MAX = 132.0f;

    float x;
    float y;
    float width;
    float height;
    float radius;
    float reveal;
    float centerX;

    float pillLeft;
    float pillWidth;
    float pillCoverX;
    float pillCoverY;
    float pillTextX;
    float pillTitleY;
    float pillTitleSlot;
    float pillWaveX;

    float cardLeft;
    float cardWidth;
    float coverX;
    float coverY;
    float textX;
    float titleY;
    float titleSlot;
    float artistY;
    float waveX;
    float barX;
    float barY;
    float barWidth;
    float rowY;

    static float pillWidth(float titleWidth, float room, boolean bars) {
        float body = pillFrame(bars) + clamp(titleWidth, TITLE_MIN, TITLE_MAX);
        return Math.min(body, room);
    }

    static boolean fits(float room, boolean bars) {
        return room >= pillFrame(bars) + TITLE_LEAST;
    }

    private static float pillFrame(boolean bars) {
        return PILL_PAD + FACE + PILL_GAP + (bars ? PILL_GAP + IslandGlyph.PILL_WIDTH : 0.0f) + PILL_PAD;
    }

    static float cardWidth(float titleWidth, float artistWidth, float screenWidth) {
        float body = CARD_PAD + ART + CARD_GAP + clamp(Math.max(titleWidth, artistWidth), CARD_TEXT_MIN,
                CARD_TEXT_MAX) + CARD_GAP + IslandGlyph.CARD_WIDTH + CARD_PAD;
        return Math.min(body, screenWidth - SCREEN_EDGE * 2.0f);
    }

    void lay(float middle, float top, float shape, float pill, float card, boolean bars) {
        centerX = middle;
        width = lerp(pill, card, shape);
        height = lerp(PILL_HEIGHT, CARD_HEIGHT, shape);
        x = middle - width / 2.0f;
        y = top;
        radius = Math.min(height / 2.0f, MAX_RADIUS);
        layPill(middle, top, pill, bars);
        layCard(middle, top, card);
    }

    private void layPill(float middle, float top, float pill, boolean bars) {
        pillWidth = pill;
        pillLeft = middle - pill / 2.0f;
        pillCoverX = pillLeft + PILL_PAD + FACE / 2.0f;
        pillCoverY = top + PILL_HEIGHT / 2.0f;
        pillTextX = pillCoverX + FACE / 2.0f + PILL_GAP;
        pillTitleY = UiRender.centerY(top, PILL_HEIGHT, DesktopIslandText.PILL_TITLE_SCALE);
        pillWaveX = pillLeft + pill - PILL_PAD - IslandGlyph.PILL_WIDTH / 2.0f;
        float textEnd = bars ? pillWaveX - IslandGlyph.PILL_WIDTH / 2.0f - PILL_GAP : pillLeft + pill - PILL_PAD;
        pillTitleSlot = textEnd - pillTextX;
    }

    private void layCard(float middle, float top, float card) {
        cardWidth = card;
        cardLeft = middle - card / 2.0f;
        coverX = cardLeft + CARD_PAD + ART / 2.0f;
        coverY = top + CARD_PAD + ART / 2.0f;
        textX = coverX + ART / 2.0f + CARD_GAP;
        titleY = top + CARD_PAD + TITLE_TOP;
        artistY = top + CARD_PAD + ARTIST_TOP;
        waveX = cardLeft + card - CARD_PAD - IslandGlyph.CARD_WIDTH / 2.0f;
        titleSlot = waveX - IslandGlyph.CARD_WIDTH / 2.0f - CARD_GAP - textX;
        barY = top + BAR_CENTER;
        rowY = top + ROW_CENTER;
    }

    void layBar(float timeSlot) {
        barX = cardLeft + CARD_PAD + timeSlot + TIME_GAP;
        barWidth = Math.max(0.0f, cardLeft + cardWidth - CARD_PAD - timeSlot - TIME_GAP - barX);
    }

    float coverSize() {
        return ART;
    }

    float faceSize() {
        return FACE;
    }

    float timeLeft() {
        return cardLeft + CARD_PAD;
    }

    float timeRight() {
        return cardLeft + cardWidth - CARD_PAD;
    }

    boolean inside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    boolean onHeader(double mouseX, double mouseY) {
        return inside(mouseX, mouseY) && mouseY <= y + CARD_PAD + ART + 3.0f;
    }

    private static float lerp(float from, float to, float weight) {
        return from + (to - from) * weight;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
