package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class LootGrid {
    public static final int TILE = 58;
    public static final int GAP = 6;

    private static final float RADIUS = 7.0f;
    private static final float LEAD = 3.0f;
    private static final float ICON_SCALE = 1.5f;
    private static final float ICON_TOP = 7.0f;
    private static final float NAME_TOP = 33.0f;
    private static final float NAME_SCALE = 0.6f;
    private static final float PLATE_HEIGHT = 11.0f;
    private static final float PLATE_BOTTOM = 4.0f;
    private static final float PLATE_SCALE = 0.62f;
    private static final float BADGE_SCALE = 0.55f;
    private static final float BAR_HEIGHT = 1.5f;
    private static final float HOVER_SPEED = 16.0f;
    private static final float SLIDE_SPEED = 17.0f;
    private static final float CHOICE_SPEED = 13.0f;
    private static final float APPEAR_MS = 200.0f;
    private static final float STAGGER_MS = 14.0f;
    private static final float APPEAR_LIFT = 5.0f;
    private static final float CARRY_LIFT = 1.12f;
    private static final float CARRY_ALPHA = 0.92f;
    private static final float LIFT_SPEED = 13.0f;
    private static final int UNKNOWN_SCRIM = 0xB0301014;

    private final List<Smooth> hover = new ArrayList<>();
    private final List<Smooth> slideX = new ArrayList<>();
    private final List<Smooth> slideY = new ArrayList<>();
    private final Smooth choice = new Smooth(1.0f, CHOICE_SPEED);
    private final Smooth carryLift = new Smooth(0.0f, LIFT_SPEED);
    private long shownAt = System.currentTimeMillis();
    private float left;
    private float top;
    private float width;
    private float height;
    private int scroll;
    private int chosen = -1;
    private int carriedFrom = -1;
    private int carriedTo = -1;
    private float carryX;
    private float carryY;

    public void place(float gridLeft, float gridTop, float gridWidth, float gridHeight) {
        left = gridLeft;
        top = gridTop;
        width = gridWidth;
        height = gridHeight;
    }

    public void reset() {
        scroll = 0;
        shownAt = System.currentTimeMillis();
        slideX.clear();
        slideY.clear();
    }

    // WHY: после ответа сервера номера записей уже в новом порядке, и сглаживание по номеру
    // WHY: повело бы плитку от места чужой записи: места защёлкиваются на новом составе
    public void settle() {
        carriedFrom = -1;
        carriedTo = -1;
        slideX.clear();
        slideY.clear();
    }

    public int columns() {
        return Math.max(1, (int) ((width - UiMetrics.PAD * 2.0f + GAP) / (TILE + GAP)));
    }

    public int rows() {
        return Math.max(1, (int) ((height - LEAD + GAP) / (TILE + GAP)));
    }

    public void scrollBy(int step, int total) {
        int rowsTotal = (total + columns() - 1) / columns();
        int lastRow = Math.max(0, rowsTotal - rows());
        scroll = Math.max(0, Math.min(lastRow, scroll / columns() + step)) * columns();
    }

    public int pick(double mouseX, double mouseY, int total) {
        for (int index = scroll; index < Math.min(total, scroll + columns() * rows()); index++) {
            float x = tileX(index);
            float y = tileY(index);
            if (mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE) return index;
        }
        return -1;
    }

    public boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    public void carry(int index, double mouseX, double mouseY) {
        carriedFrom = index;
        carriedTo = index;
        carryX = (float) mouseX;
        carryY = (float) mouseY;
        carryLift.snap(0.0f);
    }

    public boolean carrying() {
        return carriedFrom >= 0;
    }

    public void carryTo(double mouseX, double mouseY, int total) {
        carryX = (float) mouseX;
        carryY = (float) mouseY;
        carriedTo = Math.min(total - 1, slotAt(mouseX, mouseY, total));
    }

    public int[] drop() {
        int[] move = {carriedFrom, carriedTo};
        carryLift.snap(0.0f);
        return move;
    }

    public int slotAt(double mouseX, double mouseY, int total) {
        int column = (int) Math.floor((mouseX - tileX(0) + GAP / 2.0f) / (TILE + GAP));
        int row = (int) Math.floor((mouseY - top - LEAD + GAP / 2.0f) / (TILE + GAP));
        int slot = Math.max(0, Math.min(columns() - 1, column)) + Math.max(0, row) * columns() + scroll;
        return Math.max(0, Math.min(total, slot));
    }

    private int slotOf(int index) {
        if (carriedFrom < 0 || carriedTo == carriedFrom) return index;
        if (index == carriedFrom) return carriedTo;
        if (carriedFrom < carriedTo) return index > carriedFrom && index <= carriedTo ? index - 1 : index;
        return index >= carriedTo && index < carriedFrom ? index + 1 : index;
    }

    public void render(GuiGraphics graphics, LootTable table, int selected, int mouseX, int mouseY, boolean held) {
        int total = table.entries().size();
        grow(total);
        if (selected != chosen) {
            chosen = selected;
            choice.snap(0.0f);
        }
        choice.to(1.0f, UiFrame.delta());
        UiRender.clip(graphics, left, top, width, height);
        try {
            for (int index = 0; index < total; index++) {
                if (held && index == carriedFrom) continue;
                paintSlot(graphics, table, index, mouseX, mouseY);
            }
            if (held && carriedFrom >= 0 && carriedFrom < total) paintCarried(graphics, table);
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
    }

    private void grow(int total) {
        while (hover.size() < total) {
            hover.add(new Smooth(0.0f, HOVER_SPEED));
        }
        while (slideX.size() < total) {
            int index = slideX.size();
            slideX.add(new Smooth(tileX(slotOf(index)), SLIDE_SPEED));
            slideY.add(new Smooth(tileY(slotOf(index)), SLIDE_SPEED));
        }
    }

    private void paintSlot(GuiGraphics graphics, LootTable table, int index, int mouseX, int mouseY) {
        int slot = slotOf(index);
        float delta = UiFrame.delta();
        float x = slideX.get(index).to(tileX(slot), delta);
        float y = slideY.get(index).to(tileY(slot), delta);
        if (y + TILE < top || y > top + height) return;

        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt - slot * STAGGER_MS) / APPEAR_MS);
        boolean hovered = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE && over(mouseX, mouseY);
        float focus = hover.get(index).to(hovered ? 1.0f : 0.0f, delta);
        paintTile(graphics, table, index, x, y + (1.0f - appear) * APPEAR_LIFT - focus, appear, focus);
    }

    private void paintCarried(GuiGraphics graphics, LootTable table) {
        float lift = carryLift.to(1.0f, UiFrame.delta());
        float scale = 1.0f + (CARRY_LIFT - 1.0f) * lift;
        float x = carryX - TILE / 2.0f;
        float y = carryY - TILE / 2.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(carryX, carryY, 50.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-carryX, -carryY, 0.0f);
        try {
            paintTile(graphics, table, carriedFrom, x, y, CARRY_ALPHA, lift);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void paintTile(GuiGraphics graphics, LootTable table, int index, float x, float y, float appear, float focus) {
        LootEntry entry = table.entries().get(index);
        float taken = index == chosen ? choice.get() : 0.0f;
        UiGlass.panel(graphics, x, y, TILE, TILE, RADIUS, appear, 0.35f * taken + focus * 0.5f);
        if (taken > 0.01f) {
            UiRender.rim(graphics, x, y, TILE, TILE, RADIUS, 1.2f, UiTheme.alpha(UiAccent.color(), 0.75f * taken));
        }
        ItemStack stack = LootSnapshot.stack(entry);
        paintIcon(graphics, stack, x + TILE / 2.0f - 8.0f * ICON_SCALE, y + ICON_TOP);
        paintBadge(graphics, entry, x, y);
        UiRender.textTrackedFit(graphics, font(), stack.isEmpty() ? Component.literal(entry.itemId().toString())
                        : stack.getHoverName(), x + TILE / 2.0f, y + NAME_TOP, PLATE_HEIGHT, TILE - 8.0f,
                NAME_SCALE, 0.0f, UiAccent.text(), false);
        paintChance(graphics, table, index, entry, x, y, Math.max(taken, focus));
        if (stack.isEmpty()) UiRender.panel(graphics, x, y, TILE, TILE, RADIUS, UNKNOWN_SCRIM);
    }

    private static void paintIcon(GuiGraphics graphics, ItemStack stack, float x, float y) {
        if (stack.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(ICON_SCALE, ICON_SCALE, 1.0f);
        try {
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void paintBadge(GuiGraphics graphics, LootEntry entry, float x, float y) {
        String count = entry.min() == entry.max() ? "x" + entry.min() : entry.min() + "-" + entry.max();
        UiRender.textTrackedBox(graphics, font(), Component.literal(count), x + 4.0f, y + 3.0f, 8.0f, TILE - 8.0f,
                BADGE_SCALE, 0.0f, UiAccent.textDim(), false, 0.0f);
    }

    private static void paintChance(GuiGraphics graphics, LootTable table, int index, LootEntry entry,
                                    float x, float y, float lit) {
        float plateX = x + UiMetrics.GAP;
        float plateWidth = TILE - UiMetrics.GAP * 2.0f;
        float plateY = y + TILE - PLATE_HEIGHT - PLATE_BOTTOM;
        UiGlass.sunken(graphics, plateX, plateY, plateWidth, PLATE_HEIGHT, UiMetrics.radius(PLATE_HEIGHT), 0.9f);
        float real = LootOdds.appears(table, index);
        UiRender.rect(graphics, plateX + 2.0f, plateY + PLATE_HEIGHT - BAR_HEIGHT - 1.0f,
                (plateWidth - 4.0f) * real, BAR_HEIGHT, UiTheme.alpha(chanceColor(entry), 0.8f));
        Component text = Component.literal(LootChance.shown(entry.chance() * 100.0f) + " %");
        UiRender.textTrackedFit(graphics, font(), text, x + TILE / 2.0f, plateY - 0.5f, PLATE_HEIGHT,
                plateWidth - 2.0f, PLATE_SCALE, 0.0f, UiTheme.mix(UiAccent.text(), UiAccent.color(), lit), false);
    }

    private static int chanceColor(LootEntry entry) {
        return entry.guaranteed() ? UiAccent.color() : UiTheme.mix(UiPalette.alert(), UiAccent.color(),
                (float) LootChance.toSlider(entry.chance() * 100.0f));
    }

    private float tileX(int slot) {
        return left + UiMetrics.PAD + Math.floorMod(slot, columns()) * (TILE + GAP);
    }

    private float tileY(int slot) {
        return top + LEAD + (Math.floorDiv(slot, columns()) - scroll / columns()) * (TILE + GAP);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
