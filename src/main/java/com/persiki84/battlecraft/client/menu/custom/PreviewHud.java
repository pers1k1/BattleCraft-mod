package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudPlacement;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.hudedit.HudSample;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiCorner;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: элементы HUD показываются их собственной отрисовкой (та же, что в редакторе HUD в игре), а не
// WHY: похожей заглушкой: остров, микрофон и карточка эфира в кастомизации ровно такие, какими их
// WHY: увидят поверх мира, и подчиняются тем же выключателям. Видимость элементов правится щелчком
// WHY: прямо по их плашкам: так видно сразу, что включено, без строки на каждый элемент
final class PreviewHud {
    private static final float MAX_SCALE = 1.6f;
    private static final float SCALE_SPEED = 10.0f;
    private static final float HIDDEN_ALPHA = 0.4f;
    private static final float CHIP_HEIGHT = 17.0f;
    private static final float CHIP_GAP = 4.0f;
    private static final float CHIP_SCALE = 0.68f;
    private static final float CHIP_SPEED = 14.0f;
    private static final float CAPTION_SCALE = 0.7f;
    private static final HudSlot[] SLOTS = HudSlot.values();
    private static final Component[] NAMES = names();
    private static final Component OFF = Component.translatable("battlecraft.custom.preview.off");
    private static final Component IN_CHAT = Component.translatable("battlecraft.custom.hud.in_chat");

    private final HudBox[] boxes = new HudBox[SLOTS.length];
    private final Smooth[] scales = new Smooth[SLOTS.length];
    private final Smooth[] chipShown = new Smooth[SLOTS.length];
    private final Smooth[] chipHover = new Smooth[SLOTS.length];
    private float chipsLeft;
    private float chipsTop;
    private float chipWidth;
    private boolean chipsDrawn;

    PreviewHud() {
        for (int index = 0; index < SLOTS.length; index++) {
            boxes[index] = new HudBox(SLOTS[index]);
            scales[index] = new Smooth(0.0f, SCALE_SPEED);
            chipShown[index] = new Smooth(HudLayout.of(SLOTS[index]).visible() ? 1.0f : 0.0f, CHIP_SPEED);
            chipHover[index] = new Smooth(0.0f, CHIP_SPEED);
        }
    }

    private static Component[] names() {
        Component[] built = new Component[SLOTS.length];
        for (int index = 0; index < SLOTS.length; index++) {
            built[index] = Component.translatable(SLOTS[index].translationKey());
        }
        return built;
    }

    void forget() {
        chipsDrawn = false;
    }

    // WHY: образец меряет себя сам (HudLayout.sample), поэтому масштаб под ширину колонки считается по
    // WHY: прошлому кадру и доезжает сглаживанием: у острова ширина меняется вместе с треком
    float sample(GuiGraphics graphics, HudSlot slot, boolean enabled, float centerX, float y, float width) {
        int index = slot.ordinal();
        float sampleWidth = Math.max(1.0f, HudLayout.outlineWidth(slot));
        float sampleHeight = Math.max(1.0f, HudLayout.outlineHeight(slot));
        float wanted = Math.min(MAX_SCALE, width / sampleWidth);
        Smooth smooth = scales[index];
        if (smooth.get() <= 0.0f) smooth.snap(wanted);
        float scale = smooth.to(wanted, UiFrame.delta());
        HudBox box = boxes[index];
        box.set(centerX - sampleWidth * scale / 2.0f, y, sampleWidth, sampleHeight, scale, 1.0f);
        boolean previous = UiCorner.hud(true);
        try {
            HudSample.draw(graphics, slot, box, enabled ? 1.0f : HIDDEN_ALPHA);
        } finally {
            UiCorner.hud(previous);
        }
        float bottom = y + sampleHeight * scale;
        if (!enabled) bottom = caption(graphics, OFF, centerX, bottom + UiMetrics.PAD, width);
        return bottom;
    }

    private static float caption(GuiGraphics graphics, Component text, float centerX, float y, float width) {
        UiRender.textTrackedFit(graphics, font(), text, centerX, y, 10.0f, width, CAPTION_SCALE, 0.0f,
                UiAccent.textFaint(), false);
        return y + 10.0f;
    }

    float chips(GuiGraphics graphics, float left, float top, float width, int mouseX, int mouseY) {
        chipsLeft = left;
        chipsTop = top + 14.0f;
        chipWidth = (width - CHIP_GAP) / 2.0f;
        chipsDrawn = true;
        caption(graphics, IN_CHAT, left + width / 2.0f, top, width);
        int pointed = chipAt(mouseX, mouseY);
        float delta = UiFrame.delta();
        for (int index = 0; index < SLOTS.length; index++) {
            float shown = chipShown[index].to(HudLayout.of(SLOTS[index]).visible() ? 1.0f : 0.0f, delta);
            float hover = chipHover[index].to(index == pointed ? 1.0f : 0.0f, delta);
            paintChip(graphics, index, shown, hover);
        }
        int rows = (SLOTS.length + 1) / 2;
        return chipsTop + rows * (CHIP_HEIGHT + CHIP_GAP);
    }

    private void paintChip(GuiGraphics graphics, int index, float shown, float hover) {
        float x = chipsLeft + (index % 2) * (chipWidth + CHIP_GAP);
        float y = chipsTop + (index / 2) * (CHIP_HEIGHT + CHIP_GAP);
        float radius = UiMetrics.radius(CHIP_HEIGHT);
        UiGlass.sunken(graphics, x, y, chipWidth, CHIP_HEIGHT, radius, 1.0f);
        if (shown > 0.01f) UiGlass.panel(graphics, x, y, chipWidth, CHIP_HEIGHT, radius, shown, 0.25f + 0.35f * hover);
        if (hover > 0.01f) UiRender.rim(graphics, x, y, chipWidth, CHIP_HEIGHT, radius, 1.0f,
                UiTheme.alpha(UiAccent.color(), 0.6f * hover));
        UiRender.dot(graphics, x + 7.0f, y + CHIP_HEIGHT / 2.0f, 2.2f,
                UiTheme.alpha(UiTheme.mix(UiAccent.faint(), UiAccent.color(), shown), 0.5f + 0.5f * shown));
        int ink = UiTheme.mix(UiAccent.textFaint(), UiAccent.text(), shown);
        UiRender.textTrackedBox(graphics, font(), NAMES[index], x + 13.0f, y, CHIP_HEIGHT, chipWidth - 17.0f,
                CHIP_SCALE, 0.0f, ink, false, 0.0f);
    }

    HudSlot slotAt(double mouseX, double mouseY) {
        int index = chipAt(mouseX, mouseY);
        return index < 0 ? null : SLOTS[index];
    }

    private int chipAt(double mouseX, double mouseY) {
        if (!chipsDrawn || mouseX < chipsLeft || mouseY < chipsTop) return -1;
        float stepY = CHIP_HEIGHT + CHIP_GAP;
        int column = (int) ((mouseX - chipsLeft) / (chipWidth + CHIP_GAP));
        int row = (int) ((mouseY - chipsTop) / stepY);
        if (column > 1 || mouseX - chipsLeft - column * (chipWidth + CHIP_GAP) > chipWidth) return -1;
        if (mouseY - chipsTop - row * stepY > CHIP_HEIGHT) return -1;
        int index = row * 2 + column;
        return index < SLOTS.length ? index : -1;
    }

    static void toggle(HudSlot slot) {
        HudPlacement placement = HudLayout.of(slot);
        placement.visible(!placement.visible());
        Customization.save();
        UiSound.toggle(placement.visible());
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
