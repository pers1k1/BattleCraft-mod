package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// WHY: пресеты лежат в центре студии плитками с миниатюрой: игрок выбирает вид глазами, а не по
// WHY: названию в списке. Плитка подсвечивается под курсором, выбранная держит кант, применённая
// WHY: несёт точку акцента. Вход лесенкой и подъём под курсором сдержанные, как у плиток витрины
final class PresetGallery {
    private static final int COLUMNS = 3;
    private static final float GAP = 7.0f;
    private static final float NAME_ROW = 13.0f;
    private static final float NAME_SCALE = 0.68f;
    private static final float RADIUS = 7.0f;
    private static final float GLYPH_INSET = 4.0f;
    private static final float HOVER_SPEED = 16.0f;
    private static final float CHOICE_SPEED = 13.0f;
    private static final float GLIDE_SPEED = 17.0f;
    private static final float APPEAR_MS = 220.0f;
    private static final float STAGGER_MS = 24.0f;
    private static final float APPEAR_LIFT = 6.0f;
    private static final float HOVER_RISE = 1.0f;
    private static final float BADGE_RADIUS = 2.6f;
    private static final float PLUS_ARM = 0.16f;

    private final List<Smooth> hover = new ArrayList<>();
    private final Smooth choice = new Smooth(1.0f, CHOICE_SPEED);
    private final Smooth glide = new Smooth(0.0f, GLIDE_SPEED);
    private List<PresetCard> cards = List.of();
    private String chosenKey;
    private String announced;
    private long shownAt = System.currentTimeMillis();
    private float left;
    private float top;
    private float width;
    private float height;
    private int scrollRow;

    void show(List<PresetCard> shown) {
        cards = shown;
        while (hover.size() < cards.size()) {
            hover.add(new Smooth(0.0f, HOVER_SPEED));
        }
        scrollRow = Math.min(scrollRow, lastRow());
    }

    void restart() {
        shownAt = System.currentTimeMillis();
        scrollRow = 0;
        glide.snap(0.0f);
    }

    void place(float galleryLeft, float galleryTop, float galleryWidth, float galleryHeight) {
        left = galleryLeft + UiMetrics.PAD_WIDE;
        top = galleryTop + UiMetrics.PAD_WIDE;
        width = galleryWidth - UiMetrics.PAD_WIDE * 2.0f;
        height = galleryHeight - UiMetrics.PAD_WIDE * 2.0f;
    }

    private float tileWidth() {
        return (width - GAP * (COLUMNS - 1)) / COLUMNS;
    }

    private float tileHeight() {
        return tileWidth() * 0.78f + NAME_ROW;
    }

    private float step() {
        return tileHeight() + GAP;
    }

    private int visibleRows() {
        return Math.max(1, (int) ((height + GAP) / step()));
    }

    private int lastRow() {
        int rows = (cards.size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - visibleRows());
    }

    boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    void scrollBy(int rows) {
        int next = Math.max(0, Math.min(lastRow(), scrollRow + rows));
        if (next == scrollRow) return;
        glide.snap(glide.get() + (next - scrollRow) * step());
        scrollRow = next;
    }

    void reveal(String key) {
        int index = indexOf(key);
        if (index < 0) return;
        int row = index / COLUMNS;
        if (row < scrollRow) scrollBy(row - scrollRow);
        if (row >= scrollRow + visibleRows()) scrollBy(row - scrollRow - visibleRows() + 1);
    }

    private int indexOf(String key) {
        for (int index = 0; index < cards.size(); index++) {
            if (cards.get(index).key().equals(key)) return index;
        }
        return -1;
    }

    PresetCard at(double mouseX, double mouseY) {
        if (!over(mouseX, mouseY)) return null;
        for (int index = 0; index < cards.size(); index++) {
            float x = tileX(index);
            float y = tileY(index);
            if (mouseX >= x && mouseX < x + tileWidth() && mouseY >= y && mouseY < y + tileHeight()) {
                return cards.get(index);
            }
        }
        return null;
    }

    private float tileX(int index) {
        return left + (index % COLUMNS) * (tileWidth() + GAP);
    }

    private float tileY(int index) {
        return top + (index / COLUMNS - scrollRow) * step() + glide.get();
    }

    private float restY(int index) {
        return top + (index / COLUMNS - scrollRow) * step();
    }

    void render(GuiGraphics graphics, String selectedKey, int mouseX, int mouseY) {
        glide.to(0.0f, UiFrame.delta());
        followChoice(selectedKey);
        UiRender.clip(graphics, left, top, width, height);
        try {
            String pointed = null;
            for (int index = 0; index < cards.size(); index++) {
                if (paintSlot(graphics, index, selectedKey, mouseX, mouseY)) pointed = cards.get(index).key();
            }
            announce(pointed);
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
    }

    private void followChoice(String selectedKey) {
        if (selectedKey == null ? chosenKey != null : !selectedKey.equals(chosenKey)) {
            chosenKey = selectedKey;
            choice.snap(0.0f);
        }
        choice.to(1.0f, UiFrame.delta());
    }

    private void announce(String pointed) {
        if (pointed != null && !pointed.equals(announced)) UiSound.hover();
        announced = pointed;
    }

    private boolean paintSlot(GuiGraphics graphics, int index, String selectedKey, int mouseX, int mouseY) {
        float x = tileX(index);
        float y = tileY(index);
        if (y + tileHeight() < top || y > top + height) return false;
        boolean pointed = mouseX >= x && mouseX < x + tileWidth() && mouseY >= y && mouseY < y + tileHeight()
                && over(mouseX, mouseY);
        float focus = hover.get(index).to(pointed ? 1.0f : 0.0f, UiFrame.delta());
        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt - index * STAGGER_MS) / APPEAR_MS);
        PresetCard card = cards.get(index);
        float taken = card.key().equals(selectedKey) ? choice.get() : 0.0f;
        float restY = restY(index);
        UiRestFrame.shift(graphics, 0.0f, y - restY + (1.0f - appear) * APPEAR_LIFT - focus * wholeRise(graphics));
        try {
            paintTile(graphics, card, x, restY, UiAnim.clamp01(appear), focus, taken);
        } finally {
            UiRestFrame.pop(graphics);
        }
        return pointed;
    }

    // WHY: подъём держится, пока плитка наведена, поэтому встаёт на целый пиксель: на дробном подъёме
    // WHY: имя всё время наведения стояло бы на полупикселе и читалось мягче соседних
    private static float wholeRise(GuiGraphics graphics) {
        float pixels = UiRender.pixels(graphics) / UiRestFrame.stretchX();
        return pixels <= 0.01f ? HOVER_RISE : Math.round(HOVER_RISE * pixels) / pixels;
    }

    private void paintTile(GuiGraphics graphics, PresetCard card, float x, float y, float appear, float focus,
                           float taken) {
        float w = tileWidth();
        float h = tileHeight();
        UiGlass.panel(graphics, x, y, w, h, RADIUS, appear, 0.3f * taken + 0.45f * focus);
        if (taken > 0.01f) UiRender.rim(graphics, x, y, w, h, RADIUS, 1.2f,
                UiTheme.alpha(UiAccent.color(), 0.8f * taken * appear));
        float glyphSize = h - NAME_ROW - GLYPH_INSET * 2.0f;
        PresetGlyph.paint(graphics, card, x + GLYPH_INSET, y + GLYPH_INSET, w - GLYPH_INSET * 2.0f, glyphSize, appear);
        if (card.fresh()) paintPlus(graphics, x + w / 2.0f, y + GLYPH_INSET + glyphSize / 2.0f, glyphSize, appear);
        if (card.current()) UiRender.dot(graphics, x + w - GLYPH_INSET - BADGE_RADIUS * 2.0f,
                y + GLYPH_INSET + BADGE_RADIUS * 2.0f, BADGE_RADIUS, UiTheme.alpha(UiAccent.color(), appear));
        paintName(graphics, card.name(), x, y + h - NAME_ROW - 1.0f, w, appear, taken);
    }

    private static void paintPlus(GuiGraphics graphics, float centerX, float centerY, float size, float appear) {
        float arm = size * PLUS_ARM;
        int tint = UiTheme.alpha(UiTheme.WHITE, 0.9f * appear);
        UiRender.dot(graphics, centerX, centerY, arm * 1.6f, UiTheme.alpha(UiAccent.color(), 0.9f * appear));
        UiRender.line(graphics, centerX - arm, centerY, centerX + arm, centerY, 1.4f, tint);
        UiRender.line(graphics, centerX, centerY - arm, centerX, centerY + arm, 1.4f, tint);
    }

    private static void paintName(GuiGraphics graphics, Component name, float x, float y, float width,
                                  float appear, float taken) {
        int tint = UiTheme.mix(UiAccent.text(), UiAccent.color(), 0.6f * taken);
        UiRender.textTrackedFit(graphics, font(), name, x + width / 2.0f, y, NAME_ROW, width - GLYPH_INSET * 2.0f,
                NAME_SCALE, 0.0f, UiTheme.alpha(tint, appear), false);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
