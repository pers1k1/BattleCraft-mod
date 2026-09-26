package com.persiki84.shared.client.menu.studio;

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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StudioGrid {
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
    private static final float BORN_MS = 900.0f;
    private static final float GAP_SPEED = 14.0f;
    private static final int FADED_SCRIM = 0x9A0C0C12;

    private final List<Smooth> hover = new ArrayList<>();
    private final List<Smooth> slideX = new ArrayList<>();
    private final List<Smooth> slideY = new ArrayList<>();
    private final Smooth choice = new Smooth(1.0f, CHOICE_SPEED);
    private final Smooth carryLift = new Smooth(0.0f, LIFT_SPEED);
    private final Smooth gapGlow = new Smooth(0.0f, GAP_SPEED);
    private long shownAt = System.currentTimeMillis();
    private long bornAt;
    private int born = -1;
    private float left;
    private float top;
    private float width;
    private float height;
    private int scroll;
    private int chosen = -1;
    private int carriedFrom = -1;
    private int carriedTo = -1;
    private int gap = -1;
    private boolean holding;
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
        settle();
    }

    // WHY: после ответа сервера номера плиток уже в новом порядке, и сглаживание по номеру
    // WHY: повело бы плитку от места чужой записи: места защёлкиваются на новом составе
    public void settle() {
        carriedFrom = -1;
        carriedTo = -1;
        holding = false;
        gap = -1;
        slideX.clear();
        slideY.clear();
    }

    public void flash(int index) {
        born = index;
        bornAt = System.currentTimeMillis();
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

    public void reveal(int index, int total) {
        if (index < 0) return;
        int row = index / columns();
        int first = scroll / columns();
        if (row < first) scrollBy(row - first, total);
        if (row >= first + rows()) scrollBy(row - first - rows() + 1, total);
    }

    public int pick(double mouseX, double mouseY, int total) {
        if (!over(mouseX, mouseY)) return -1;
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
        holding = true;
        carryX = (float) mouseX;
        carryY = (float) mouseY;
        carryLift.snap(0.0f);
    }

    public boolean carrying() {
        return holding;
    }

    public void carryTo(double mouseX, double mouseY, int total, boolean reorder) {
        carryX = (float) mouseX;
        carryY = (float) mouseY;
        boolean placing = reorder && over(mouseX, mouseY);
        carriedTo = placing ? Math.min(total - 1, slotAt(mouseX, mouseY, total)) : carriedFrom;
    }

    // WHY: отпущенная плитка едет в своё место от курсора, а не возникает там из прежнего слота;
    // WHY: порядок соседей держится до ответа сервера, иначе сетка на полсекунды вернулась бы назад
    public int[] drop() {
        int[] move = {carriedFrom, carriedTo};
        holding = false;
        carryLift.snap(0.0f);
        if (carriedFrom >= 0 && carriedFrom < slideX.size()) {
            slideX.get(carriedFrom).snap(carryX - TILE / 2.0f);
            slideY.get(carriedFrom).snap(carryY - TILE / 2.0f);
        }
        return move;
    }

    // WHY: предмет, который несут с полки, раздвигает соседей там, куда ляжет: без просвета игрок
    // WHY: не видит, встанет ли он перед плиткой под курсором или после неё
    public void openGap(double mouseX, double mouseY, int total) {
        gap = over(mouseX, mouseY) ? slotAt(mouseX, mouseY, total) : -1;
    }

    public void closeGap() {
        gap = -1;
    }

    public int slotAt(double mouseX, double mouseY, int total) {
        int column = (int) Math.floor((mouseX - tileX(0) + GAP / 2.0f) / (TILE + GAP));
        int row = (int) Math.floor((mouseY - top - LEAD + GAP / 2.0f) / (TILE + GAP));
        int slot = Math.max(0, Math.min(columns() - 1, column)) + Math.max(0, row) * columns() + scroll;
        return Math.max(0, Math.min(total, slot));
    }

    private int slotOf(int index) {
        int slot = carriedSlot(index);
        return gap >= 0 && slot >= gap ? slot + 1 : slot;
    }

    private int carriedSlot(int index) {
        if (carriedFrom < 0 || carriedTo == carriedFrom) return index;
        if (index == carriedFrom) return carriedTo;
        if (carriedFrom < carriedTo) return index > carriedFrom && index <= carriedTo ? index - 1 : index;
        return index >= carriedTo && index < carriedFrom ? index + 1 : index;
    }

    public void render(GuiGraphics graphics, List<? extends StudioTile> tiles, int selected, int mouseX, int mouseY) {
        int total = tiles.size();
        grow(total);
        follow(selected);
        UiRender.clip(graphics, left, top, width, height);
        try {
            paintGap(graphics);
            for (int index = 0; index < total; index++) {
                if (carrying() && index == carriedFrom) continue;
                paintSlot(graphics, tiles.get(index), index, mouseX, mouseY);
            }
            if (carrying() && carriedFrom < total) paintCarried(graphics, tiles.get(carriedFrom));
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
    }

    private void follow(int selected) {
        if (selected != chosen) {
            chosen = selected;
            choice.snap(0.0f);
        }
        choice.to(1.0f, UiFrame.delta());
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

    private void paintGap(GuiGraphics graphics) {
        float glow = UiAnim.easeOut(gapGlow.to(gap >= 0 ? 1.0f : 0.0f, UiFrame.delta()));
        if (gap < 0 || glow <= 0.01f) return;

        float x = tileX(gap);
        float y = tileY(gap);
        UiGlass.sunken(graphics, x, y, TILE, TILE, RADIUS, 0.8f * glow);
        UiRender.rim(graphics, x, y, TILE, TILE, RADIUS, 1.2f, UiTheme.alpha(UiAccent.color(), 0.6f * glow));
    }

    private void paintSlot(GuiGraphics graphics, StudioTile tile, int index, int mouseX, int mouseY) {
        int slot = slotOf(index);
        float delta = UiFrame.delta();
        float x = slideX.get(index).to(tileX(slot), delta);
        float y = slideY.get(index).to(tileY(slot), delta);
        if (y + TILE < top || y > top + height) return;

        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt - slot * STAGGER_MS) / APPEAR_MS);
        boolean hovered = !carrying() && mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE
                && over(mouseX, mouseY);
        float focus = hover.get(index).to(hovered ? 1.0f : 0.0f, delta);
        paintTile(graphics, tile, index, x, y + (1.0f - appear) * APPEAR_LIFT - focus, appear, focus);
    }

    private void paintCarried(GuiGraphics graphics, StudioTile tile) {
        float lift = carryLift.to(1.0f, UiFrame.delta());
        float scale = 1.0f + (CARRY_LIFT - 1.0f) * lift;
        graphics.pose().pushPose();
        graphics.pose().translate(carryX, carryY, 50.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-carryX, -carryY, 0.0f);
        try {
            paintTile(graphics, tile, carriedFrom, carryX - TILE / 2.0f, carryY - TILE / 2.0f, CARRY_ALPHA, lift);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void paintTile(GuiGraphics graphics, StudioTile tile, int index, float x, float y, float appear, float focus) {
        float taken = index == chosen ? choice.get() : 0.0f;
        UiGlass.panel(graphics, x, y, TILE, TILE, RADIUS, appear, 0.35f * taken + focus * 0.5f);
        if (taken > 0.01f) {
            UiRender.rim(graphics, x, y, TILE, TILE, RADIUS, 1.2f, UiTheme.alpha(UiAccent.color(), 0.75f * taken));
        }
        paintBorn(graphics, index, x, y);
        paintIcon(graphics, tile.icon(), x + TILE / 2.0f - 8.0f * ICON_SCALE, y + ICON_TOP);
        paintCorners(graphics, tile, x, y);
        UiRender.textTrackedFit(graphics, font(), tile.name(), x + TILE / 2.0f, y + NAME_TOP, PLATE_HEIGHT,
                TILE - 8.0f, NAME_SCALE, 0.0f, UiAccent.text(), false);
        paintPlate(graphics, tile, x, y, Math.max(taken, focus));
        if (tile.faded()) UiRender.panel(graphics, x, y, TILE, TILE, RADIUS, FADED_SCRIM);
    }

    private void paintBorn(GuiGraphics graphics, int index, float x, float y) {
        if (index != born) return;
        float age = (System.currentTimeMillis() - bornAt) / BORN_MS;
        if (age >= 1.0f) return;

        float glow = 1.0f - UiAnim.easeOut(age);
        float spread = UiAnim.easeOut(age) * 4.0f;
        UiRender.rim(graphics, x - spread, y - spread, TILE + spread * 2.0f, TILE + spread * 2.0f,
                RADIUS + spread, 1.4f, UiTheme.alpha(UiAccent.color(), glow));
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

    private static void paintCorners(GuiGraphics graphics, StudioTile tile, float x, float y) {
        UiRender.textTrackedBox(graphics, font(), tile.corner(), x + 4.0f, y + 3.0f, 8.0f, TILE - 8.0f,
                BADGE_SCALE, 0.0f, UiAccent.textDim(), false, 0.0f);
        UiRender.textTrackedBox(graphics, font(), tile.mark(), x + 4.0f, y + 3.0f, 8.0f, TILE - 8.0f,
                BADGE_SCALE, 0.0f, UiPalette.alert(), false, 1.0f);
    }

    private static void paintPlate(GuiGraphics graphics, StudioTile tile, float x, float y, float lit) {
        float plateX = x + UiMetrics.GAP;
        float plateWidth = TILE - UiMetrics.GAP * 2.0f;
        float plateY = y + TILE - PLATE_HEIGHT - PLATE_BOTTOM;
        UiGlass.sunken(graphics, plateX, plateY, plateWidth, PLATE_HEIGHT, UiMetrics.radius(PLATE_HEIGHT), 0.9f);
        float meter = tile.meter();
        if (meter >= 0.0f) {
            UiRender.rect(graphics, plateX + 2.0f, plateY + PLATE_HEIGHT - BAR_HEIGHT - 1.0f,
                    (plateWidth - 4.0f) * Math.min(1.0f, meter), BAR_HEIGHT, UiTheme.alpha(tile.meterColor(), 0.8f));
        }
        UiRender.textTrackedFit(graphics, font(), tile.plate(), x + TILE / 2.0f, plateY - 0.5f, PLATE_HEIGHT,
                plateWidth - 2.0f, PLATE_SCALE, 0.0f, UiTheme.mix(UiAccent.text(), UiAccent.color(), lit), false);
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
