package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootRoller;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

// WHY: пробный бросок кладёт лут в ящик на 27 слотов тем же кодом, что и сервер: так видно, как
// WHY: таблица выглядит глазами игрока, открывшего тайник, а не столбцом процентов
public final class LootTrial {
    public static final int SLOTS = 27;

    private static final int COLUMNS = 9;
    private static final float CELL = 24.0f;
    private static final float PAD = 12.0f;
    private static final float TITLE_HEIGHT = 18.0f;
    private static final float FOOT_HEIGHT = 16.0f;
    private static final float RADIUS = 9.0f;
    private static final float SHOW_SPEED = 14.0f;
    private static final float STAGGER_MS = 18.0f;
    private static final float POP_MS = 180.0f;

    private final SimpleContainer box = new SimpleContainer(SLOTS);
    private final Smooth shown = new Smooth(0.0f, SHOW_SPEED);
    private final RandomSource random = RandomSource.create();
    private boolean open;
    private long rolledAt;
    private int placed;
    private float left;
    private float top;

    public void roll(LootTable table) {
        placed = LootRoller.fill(box, table, random);
        rolledAt = System.currentTimeMillis();
        open = true;
    }

    public void close() {
        open = false;
    }

    public boolean open() {
        return open;
    }

    public boolean visible() {
        return open || shown.get() > 0.01f;
    }

    public float width() {
        return COLUMNS * CELL + PAD * 2.0f;
    }

    public float height() {
        return (SLOTS / COLUMNS) * CELL + PAD * 2.0f + TITLE_HEIGHT + FOOT_HEIGHT;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width() && mouseY >= top && mouseY < top + height();
    }

    public void render(GuiGraphics graphics, float centerX, float centerY, int mouseX, int mouseY) {
        float alpha = UiAnim.easeOut(shown.to(open ? 1.0f : 0.0f, UiFrame.delta()));
        if (alpha <= 0.01f) return;

        left = centerX - width() / 2.0f;
        top = centerY - height() / 2.0f + (1.0f - alpha) * 6.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 300.0f);
        try {
            UiGlass.window(graphics, left, top, width(), height(), RADIUS, alpha);
            UiGlass.layer(graphics);
            UiRender.textTrackedFit(graphics, Minecraft.getInstance().font, Component.translatable("airdrop.editor.trial"),
                    centerX, top + PAD * 0.5f, TITLE_HEIGHT, width() - PAD * 2.0f, 0.85f, 0.0f,
                    UiTheme.alpha(UiAccent.text(), alpha), false);
            paintSlots(graphics, alpha);
            UiRender.textTrackedFit(graphics, Minecraft.getInstance().font,
                    Component.translatable("airdrop.editor.trial.foot", placed), centerX,
                    top + height() - PAD - FOOT_HEIGHT + 4.0f, FOOT_HEIGHT, width() - PAD * 2.0f, 0.65f, 0.0f,
                    UiTheme.alpha(UiAccent.textDim(), alpha), false);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void paintSlots(GuiGraphics graphics, float alpha) {
        float gridTop = top + PAD + TITLE_HEIGHT;
        for (int slot = 0; slot < SLOTS; slot++) {
            float x = left + PAD + (slot % COLUMNS) * CELL;
            float y = gridTop + (slot / COLUMNS) * CELL;
            UiGlass.sunken(graphics, x + 1.0f, y + 1.0f, CELL - 2.0f, CELL - 2.0f, UiMetrics.radius(CELL) * 0.4f, alpha);
            ItemStack stack = box.getItem(slot);
            if (!stack.isEmpty()) paintItem(graphics, stack, x, y, pop(slot) * alpha);
        }
    }

    private float pop(int slot) {
        return UiAnim.easeOut((System.currentTimeMillis() - rolledAt - slot * STAGGER_MS) / POP_MS);
    }

    // WHY: значок предмета не берёт альфу из цвета шейдера, поэтому на уходе окна он сжимается
    // WHY: вместе с ним: иначе предметы висели бы над погасшим стеклом до последнего кадра
    private static void paintItem(GuiGraphics graphics, ItemStack stack, float x, float y, float pop) {
        if (pop <= 0.05f) return;

        float centerX = x + CELL / 2.0f;
        float centerY = y + CELL / 2.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0f);
        graphics.pose().scale(pop, pop, 1.0f);
        graphics.pose().translate(-8.0f, -8.0f, 0.0f);
        try {
            graphics.renderItem(stack, 0, 0);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    public ItemStack hovered(double mouseX, double mouseY) {
        if (!open) return ItemStack.EMPTY;
        float gridTop = top + PAD + TITLE_HEIGHT;
        int column = (int) Math.floor((mouseX - left - PAD) / CELL);
        int row = (int) Math.floor((mouseY - gridTop) / CELL);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= SLOTS / COLUMNS) return ItemStack.EMPTY;
        return box.getItem(row * COLUMNS + column);
    }
}
