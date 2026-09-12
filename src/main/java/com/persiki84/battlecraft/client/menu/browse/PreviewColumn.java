package com.persiki84.battlecraft.client.menu.browse;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: колонка раскладывается от отданной ей высоты, а не от постоянных отступов: при низком
// WHY: окне панель ужимается до высоты кнопок, и жёсткая раскладка уводила строки под них
final class PreviewColumn {
    private static final float ICON_MAX = 48.0f;
    private static final float ICON_SHARE = 0.42f;
    private static final float NAME_GAP = 6.0f;
    private static final float NAME_HEIGHT = 13.0f;
    private static final float NAME_SCALE = 1.0f;
    private static final float NAME_TRACKING = 0.4f;
    private static final float FACTS_GAP = 6.0f;
    private static final float ROW_HEIGHT = 13.0f;
    private static final float ROW_SCALE = 0.72f;
    private static final float ROW_TRACKING = 0.3f;

    private final GuiGraphics graphics;
    private final Font font;
    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private float y;

    PreviewColumn(GuiGraphics graphics, Font font, int left, int top, int width, int height) {
        this.graphics = graphics;
        this.font = font;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.y = top;
    }

    static void nothing(GuiGraphics graphics, Font font, int left, int top, int width, int height) {
        UiRender.textCentered(graphics, font, Component.translatable("battlecraft.browse.nothing"),
                left + width / 2.0f, top + height / 2.0f - 4.0f, ROW_SCALE,
                UiAccent.textFaint(), false);
    }

    void head(BrowseCard card, Component name) {
        float size = Math.min(ICON_MAX, height * ICON_SHARE);
        card.paintIcon(graphics, left + (width - size) / 2.0f, top, size);
        UiRender.textTrackedFit(graphics, font, name, left + width / 2.0f, top + size + NAME_GAP,
                NAME_HEIGHT, width, NAME_SCALE, NAME_TRACKING, UiAccent.text(), false);
        y = top + size + NAME_GAP + NAME_HEIGHT + FACTS_GAP;
    }

    void fact(String label, Component value) {
        if (y + ROW_HEIGHT > top + height) return;

        UiRender.textTrackedLeft(graphics, font, Component.translatable(label), left, y,
                ROW_SCALE, ROW_TRACKING, UiAccent.textFaint());
        UiRender.textRight(graphics, font, value, left + width, y, ROW_SCALE,
                UiAccent.textDim(), false);
        y += ROW_HEIGHT;
    }
}
