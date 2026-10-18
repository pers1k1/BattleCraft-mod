package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiQuality;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: миниатюра пресета это пластина его настоящего стекла над двумя шарами. Рисуется она в два
// WHY: прохода: сначала шары всех карточек, потом пересъёмка подложки и пластины. Без пересъёмки
// WHY: стекло преломляло снимок, снятый до шаров, и сквозь пластину был виден мир за окном
final class PresetGlyph {
    private static final float INSET = 0.18f;
    private static final float BLOB = 0.34f;
    private static final float RADIUS_FULL = 20.0f;
    private static final float LIFT_FULL = 2.5f;

    private PresetGlyph() {
    }

    // WHY: одиночная миниатюра вне ножниц (арт инспектора) переснимает подложку сама
    static void paint(GuiGraphics graphics, PresetCard card, float x, float y, float width, float height,
                      float alpha) {
        paintGround(graphics, card, x, y, width, height, alpha);
        UiGlass.layer(graphics);
        paintGlass(graphics, card, x, y, width, height, alpha);
    }

    static void paintGround(GuiGraphics graphics, PresetCard card, float x, float y, float width, float height,
                            float alpha) {
        int accent = card.accent() == 0 ? UiAccent.color() : card.accent();
        float radius = Math.min(width, height) * 0.16f;
        UiGlass.sunken(graphics, x, y, width, height, radius, alpha);
        float blob = Math.min(width, height) * BLOB;
        UiRender.dot(graphics, x + width * 0.3f, y + height * 0.42f, blob, UiTheme.alpha(accent, 0.75f * alpha));
        UiRender.dot(graphics, x + width * 0.7f, y + height * 0.62f, blob * 0.8f,
                UiTheme.alpha(UiTheme.mix(accent, UiTheme.WHITE, 0.55f), 0.55f * alpha));
    }

    record Plate(float x, float y, float width, float height, float radius, float lift) {
    }

    static Plate plate(PresetCard card, float x, float y, float width, float height) {
        float insetX = width * INSET;
        float insetY = height * INSET;
        float plateHeight = height - insetY * 2.0f;
        float radius = Math.min(plateHeight / 2.0f,
                2.0f + card.value(GlassKey.RADIUS_PANEL) / RADIUS_FULL * plateHeight * 0.4f);
        return new Plate(x + insetX, y + insetY, width - insetX * 2.0f, plateHeight, radius,
                Math.min(1.0f, card.value(GlassKey.LIFT) / LIFT_FULL));
    }

    // WHY: запасной путь, пока готовой миниатюры нет: пластина своими ручками на общем снимке
    static void paintGlass(GuiGraphics graphics, PresetCard card, float x, float y, float width, float height,
                           float alpha) {
        Plate plate = plate(card, x, y, width, height);
        if (card.base().mode() == UiQuality.Mode.PLAIN) {
            UiRender.panel(graphics, plate.x(), plate.y(), plate.width(), plate.height(), plate.radius(),
                    UiTheme.alpha(UiTheme.PANEL, card.value(GlassKey.SURFACE_ALPHA) * alpha));
            return;
        }
        PresetStyle.wear(graphics, card);
        try {
            UiGlass.panel(graphics, plate.x(), plate.y(), plate.width(), plate.height(), plate.radius(), alpha,
                    plate.lift());
        } finally {
            PresetStyle.shed(graphics);
        }
    }

    // WHY: готовая миниатюра из PresetThumbs, а без неё живая пластина
    static void paintFace(GuiGraphics graphics, PresetThumbs thumbs, PresetCard card, float x, float y,
                          float width, float height, float alpha) {
        int texture = thumbs.texture(graphics, card, width, height);
        if (texture > 0) {
            UiRender.frame(graphics, texture, x, y, width, height, alpha);
            return;
        }
        paintGlass(graphics, card, x, y, width, height, alpha);
    }
}
