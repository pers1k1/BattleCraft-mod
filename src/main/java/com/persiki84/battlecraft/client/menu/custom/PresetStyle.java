package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiLens;
import com.persiki84.shared.client.ui.UiQuality;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumSet;

// WHY: миниатюра пресета показывает его настоящее стекло, а не рисунок стекла: на время одной
// WHY: пластины стиль стекла встаёт на числа пресета и сразу возвращается к выбранному игроком.
// WHY: Стекло читает стиль в момент отрисовки, и батч сбрасывается до и после подмены, поэтому
// WHY: остальной кадр подмены не видит. Размытие подложки входит только в полную подмену, под которую
// WHY: миниатюра снимает свою пирамиду (PresetThumbs). Форма угла меню и размер HUD не входят никогда
final class PresetStyle {
    private static final GlassKey[] KEYS = GlassKey.values();
    private static final EnumSet<GlassKey.Group> WORN =
            EnumSet.of(GlassKey.Group.MATERIAL, GlassKey.Group.LENS, GlassKey.Group.SHINE);
    private static final EnumSet<GlassKey.Group> WORN_FULL =
            EnumSet.of(GlassKey.Group.MATERIAL, GlassKey.Group.LENS, GlassKey.Group.SHINE, GlassKey.Group.BLUR);
    private static final EnumSet<GlassKey> KEPT = EnumSet.of(GlassKey.HUD_SCALE, GlassKey.MENU_CORNER);

    private PresetStyle() {
    }

    static void wear(GuiGraphics graphics, PresetCard card) {
        wear(graphics, card, false);
    }

    static void wear(GuiGraphics graphics, PresetCard card, boolean full) {
        graphics.flush();
        for (GlassKey key : KEYS) {
            if (worn(key, full)) key.apply(card.value(key));
        }
        UiGlassStyle.liquid(card.base().liquid());
        UiQuality.use(card.base().mode());
        UiLens.useDispersion(UiGlassStyle.dispersion());
        UiLens.useBrightness(UiGlassStyle.brightness());
    }

    static void shed(GuiGraphics graphics) {
        shed(graphics, false);
    }

    static void shed(GuiGraphics graphics, boolean full) {
        graphics.flush();
        for (GlassKey key : KEYS) {
            if (worn(key, full)) key.apply(Customization.glass(key));
        }
        UiGlassStyle.liquid(Customization.liquid());
        UiQuality.use(Customization.mode());
        UiLens.useDispersion(UiGlassStyle.dispersion());
        UiLens.useBrightness(UiGlassStyle.brightness());
    }

    private static boolean worn(GlassKey key, boolean full) {
        return (full ? WORN_FULL : WORN).contains(key.group()) && !KEPT.contains(key);
    }
}
