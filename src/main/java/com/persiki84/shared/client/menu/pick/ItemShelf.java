package com.persiki84.shared.client.menu.pick;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemShelf {
    public static final int CELL = 20;
    public static final int OFFHAND = 40;

    private static final int COLUMNS = 9;
    private static final int[] INVENTORY_ORDER = inventoryOrder();
    private static final float CELL_RADIUS = 4.0f;
    private static final float HOVER_SPEED = 18.0f;
    private static final int HOTBAR_GAP = 4;

    public record Pick(int slot, ItemStack stack) {
        public boolean fromInventory() {
            return slot >= 0;
        }
    }

    private final Smooth scrollGlide = new Smooth(0.0f, HOVER_SPEED);
    private final List<ItemStack> catalog = new ArrayList<>();
    private final List<ItemStack> found = new ArrayList<>();
    private boolean inventory = true;
    private String query = "";
    private float left;
    private float top;
    private float width;
    private float height;
    private int scroll;
    private ItemStack marked = ItemStack.EMPTY;

    public void place(float pickerLeft, float pickerTop, float pickerWidth, float pickerHeight) {
        left = pickerLeft;
        top = pickerTop;
        width = pickerWidth;
        height = pickerHeight;
    }

    public void mark(ItemStack stack) {
        marked = stack == null ? ItemStack.EMPTY : stack;
    }

    public boolean showsInventory() {
        return inventory;
    }

    // WHY: прокрутка списка всех предметов не принадлежит инвентарю: без сброса хода инвентарь
    // WHY: въезжал сверху, будто его самого прокрутили
    public void showInventory(boolean wanted) {
        inventory = wanted;
        scroll = 0;
        scrollGlide.snap(0.0f);
        if (!wanted) refilter();
    }

    public void search(String text) {
        query = text.toLowerCase(Locale.ROOT).trim();
        scroll = 0;
        scrollGlide.snap(0.0f);
        refilter();
    }

    // WHY: реестр предметов перебирается один раз на запрос, а не в кадре: в сборке их тысячи
    private void refilter() {
        if (catalog.isEmpty()) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR) catalog.add(new ItemStack(item));
            }
        }
        found.clear();
        for (ItemStack stack : catalog) {
            if (query.isEmpty() || matches(stack)) found.add(stack);
        }
    }

    private boolean matches(ItemStack stack) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return name.contains(query) || id.contains(query);
    }

    public void scrollBy(int step) {
        if (inventory) return;
        int rows = (found.size() + COLUMNS - 1) / COLUMNS;
        int last = Math.max(0, rows - visibleRows());
        scroll = Math.max(0, Math.min(last, scroll + step));
    }

    private int visibleRows() {
        return Math.max(1, (int) (height / CELL));
    }

    public boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    public Pick pick(double mouseX, double mouseY) {
        int count = inventory ? INVENTORY_ORDER.length : found.size();
        for (int place = 0; place < count; place++) {
            float x = cellX(place);
            float y = cellY(place);
            if (mouseX < x || mouseX >= x + CELL || mouseY < y || mouseY >= y + CELL) continue;
            return pickAt(place);
        }
        return null;
    }

    private Pick pickAt(int place) {
        if (!inventory) return new Pick(-1, found.get(place));

        Inventory items = player();
        int slot = INVENTORY_ORDER[place];
        ItemStack stack = items == null ? ItemStack.EMPTY : items.getItem(slot);
        return stack.isEmpty() ? null : new Pick(slot, stack);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRender.clip(graphics, left, top, width, height);
        try {
            int count = inventory ? INVENTORY_ORDER.length : found.size();
            float lag = inventory ? 0.0f : scrollGlide.to(scroll, UiFrame.delta()) - scroll;
            for (int place = 0; place < count; place++) {
                float y = cellY(place) - lag * CELL;
                if (y + CELL < top || y > top + height) continue;
                paintCell(graphics, stackAt(place), cellX(place), y, mouseX, mouseY);
            }
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
    }

    private ItemStack stackAt(int place) {
        if (!inventory) return found.get(place);
        Inventory items = player();
        return items == null ? ItemStack.EMPTY : items.getItem(INVENTORY_ORDER[place]);
    }

    private void paintCell(GuiGraphics graphics, ItemStack stack, float x, float y, int mouseX, int mouseY) {
        boolean hovered = !stack.isEmpty() && mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
        UiGlass.sunken(graphics, x + 1.0f, y + 1.0f, CELL - 2.0f, CELL - 2.0f, CELL_RADIUS, hovered ? 1.0f : 0.7f);
        if (hovered || (!stack.isEmpty() && stack == marked)) {
            UiRender.rim(graphics, x + 1.0f, y + 1.0f, CELL - 2.0f, CELL - 2.0f, CELL_RADIUS, 1.0f,
                    UiTheme.alpha(UiAccent.color(), 0.8f));
        }
        if (stack.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(x + 2.0f, y + 2.0f, 0.0f);
        try {
            graphics.renderItem(stack, 0, 0);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    public ItemStack hovered(double mouseX, double mouseY) {
        Pick pick = pick(mouseX, mouseY);
        return pick == null ? ItemStack.EMPTY : pick.stack();
    }

    private float gridLeft() {
        return left + (width - COLUMNS * CELL) / 2.0f;
    }

    private float cellX(int place) {
        return gridLeft() + (place % COLUMNS) * CELL;
    }

    private float cellY(int place) {
        int row = place / COLUMNS;
        if (inventory) return top + UiMetrics.GAP + row * CELL + (row >= 3 ? HOTBAR_GAP : 0) + (row >= 4 ? HOTBAR_GAP : 0);
        return top + (row - scroll) * CELL;
    }

    private static Inventory player() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getInventory();
    }

    private static int[] inventoryOrder() {
        int[] order = new int[37];
        for (int index = 0; index < 27; index++) {
            order[index] = 9 + index;
        }
        for (int index = 0; index < 9; index++) {
            order[27 + index] = index;
        }
        order[36] = OFFHAND;
        return order;
    }
}
