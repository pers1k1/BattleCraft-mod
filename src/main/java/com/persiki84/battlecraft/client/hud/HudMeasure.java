package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.shared.client.ui.UiTicker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// WHY: тикер запоминает места букв по плотности пикселей позы, а ширина для HudLayout.place нужна до
// WHY: HudLayout.push, который масштабирует позу под размер слота. Замер вне этой позы и отрисовка в
// WHY: ней пересчитывали бы места букв дважды за кадр, поэтому слот с размером не 1 меряется под тем же
// WHY: масштабом, что и рисуется (масштаб слота берётся с прошлого кадра)
public final class HudMeasure {
    private HudMeasure() {}

    public static float steady(GuiGraphics graphics, UiTicker ticker, Font font, float textScale, HudSlot slot) {
        boolean scaled = begin(graphics, slot);
        try {
            return ticker.steadyWidth(graphics, font, textScale);
        } finally {
            end(graphics, scaled);
        }
    }

    public static boolean begin(GuiGraphics graphics, HudSlot slot) {
        float slotScale = HudLayout.box(slot).scale();
        if (slotScale == 1.0f) return false;

        graphics.pose().pushPose();
        graphics.pose().scale(slotScale, slotScale, 1.0f);
        return true;
    }

    public static void end(GuiGraphics graphics, boolean scaled) {
        if (scaled) graphics.pose().popPose();
    }
}
