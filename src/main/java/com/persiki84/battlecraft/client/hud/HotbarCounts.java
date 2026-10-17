package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// WHY: число предметов в ячейке меняется на каждом поставленном блоке и подобранном предмете, поэтому
// WHY: идёт тикером, как остальные числа худа, а ванильная подпись гасится пустой строкой. Ячейки
// WHY: хотбара неподвижны, поэтому тикер держится номером ячейки; смена вида предмета в ячейке это
// WHY: другой предмет, а не изменившееся число, и его счёт встаёт без хода. Кегль постоянный, рост
// WHY: ячейки при выборе и сворачивании заявлен ходом вокруг её середины покоя
public final class HotbarCounts {
    public static final String VANILLA_HIDDEN = "";
    public static final int OFFHAND = 9;

    private static final String NO_COUNT = "";

    private static final int CELLS = OFFHAND + 1;
    private static final float TEXT_SCALE = 0.72f;
    private static final float RIGHT_FROM_CENTER = 9.5f;
    private static final float BOTTOM_FROM_CENTER = 9.0f;
    private static final float ABOVE_ITEM = 200.0f;

    private static final UiTicker[] counts = new UiTicker[CELLS];
    private static final Item[] items = new Item[CELLS];

    static {
        for (int cell = 0; cell < CELLS; cell++) {
            counts[cell] = new UiTicker().align(UiTicker.RIGHT);
        }
    }

    private HotbarCounts() {}

    public static int cellOf(int slotNumber) {
        return slotNumber <= 0 || slotNumber > OFFHAND ? OFFHAND : slotNumber - 1;
    }

    public static void draw(GuiGraphics graphics, Minecraft mc, int cell, ItemStack stack, float centerX,
                            float restCenterY, float lift, float scale, float alpha) {
        UiTicker ticker = counts[cell];
        aim(ticker, cell, stack);
        if (ticker.text().isEmpty() && !ticker.moving()) return;

        float top = restCenterY + BOTTOM_FROM_CENTER - mc.font.lineHeight * TEXT_SCALE;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, ABOVE_ITEM);
        UiRestFrame.push(graphics, centerX, restCenterY, scale, scale, 0.0f, lift);
        try {
            ticker.draw(graphics, mc.font, centerX + RIGHT_FROM_CENTER, top, TEXT_SCALE, HudInk.text(), alpha);
        } finally {
            UiRestFrame.pop(graphics);
            graphics.pose().popPose();
        }
    }

    private static void aim(UiTicker ticker, int cell, ItemStack stack) {
        int count = stack.getCount();
        Item item = stack.getItem();
        if (item != items[cell]) {
            items[cell] = item;
            if (count > 1) {
                ticker.snap(count);
            } else {
                ticker.snap(NO_COUNT);
            }
            return;
        }
        if (count > 1) {
            ticker.set(count);
        } else {
            ticker.set(NO_COUNT);
        }
    }
}
