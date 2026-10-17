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
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ItemShelf {
    public static final int OFFHAND = 40;

    private static final int COLUMNS = 9;
    private static final int INVENTORY_ROWS = 5;
    private static final int[] INVENTORY_ORDER = inventoryOrder();
    private static final float BASE_CELL = 20.0f;
    private static final float CELL_RADIUS = 4.0f;
    private static final float CELL_INSET = 1.0f;
    private static final float ICON_INSET = 2.0f;
    private static final float HOVER_SPEED = 18.0f;
    private static final float HOTBAR_GAP = 4.0f;

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
    private float cell = BASE_CELL;
    private float unit = 1.0f;
    private int scroll;
    private ItemStack marked = ItemStack.EMPTY;

    public void place(float pickerLeft, float pickerTop, float pickerWidth, float pickerHeight) {
        left = pickerLeft;
        top = pickerTop;
        width = pickerWidth;
        height = pickerHeight;
        fit();
    }

    // WHY: ячейки растягиваются на всю ширину полки, а не стоят по двадцать единиц посередине с пустыми
    // WHY: полями по краям. Потолок по высоте держит инвентарь целиком: его не прокручивают
    private void fit() {
        float acrossWidth = width / COLUMNS;
        float hotbarShare = 2.0f * HOTBAR_GAP / BASE_CELL;
        float acrossHeight = (height - UiMetrics.GAP) / (INVENTORY_ROWS + hotbarShare);
        cell = Math.max(1.0f, Math.min(acrossWidth, Math.max(BASE_CELL, acrossHeight)));
        unit = cell / BASE_CELL;
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
        if (catalog.isEmpty()) fillCatalog();
        found.clear();
        for (ItemStack stack : catalog) {
            if (query.isEmpty() || matches(stack)) found.add(stack);
        }
    }

    // WHY: один предмет реестра бывает десятком разных вещей по тегу: у TACZ каждый ствол это
    // WHY: tacz:modern_kinetic_gun с GunId. Список творческого режима несёт эти варианты с тегами,
    // WHY: а реестр добирает предметы, которых нет ни в одной вкладке
    private void fillCatalog() {
        Set<Item> covered = new HashSet<>();
        for (ItemStack stack : creativeStacks()) {
            if (stack.isEmpty()) continue;
            catalog.add(stack.copyWithCount(1));
            covered.add(stack.getItem());
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR && !covered.contains(item)) catalog.add(new ItemStack(item));
        }
    }

    private static Collection<ItemStack> creativeStacks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return List.of();
        CreativeModeTabs.tryRebuildTabContents(minecraft.player.connection.enabledFeatures(),
                minecraft.player.canUseGameMasterBlocks(), minecraft.level.registryAccess());
        return CreativeModeTabs.searchTab().getDisplayItems();
    }

    // WHY: предмет уходит командой вместе с тегом в синтаксисе аргумента предмета, иначе вариант
    // WHY: превращается в голый предмет реестра, а ствол TACZ без GunId - в пустую заглушку
    public static String spec(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return stack.hasTag() ? id + stack.getTag() : id;
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
        return Math.max(1, (int) (height / cell));
    }

    public boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    public Pick pick(double mouseX, double mouseY) {
        int place = placeAt(mouseX, mouseY);
        return place < 0 ? null : pickAt(place);
    }

    private int placeAt(double mouseX, double mouseY) {
        if (!over(mouseX, mouseY)) return -1;

        int count = inventory ? INVENTORY_ORDER.length : found.size();
        for (int place = 0; place < count; place++) {
            float x = cellX(place);
            float y = cellY(place);
            if (mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell) return place;
        }
        return -1;
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
                float y = cellY(place) - lag * cell;
                if (y + cell < top || y > top + height) continue;
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
        boolean hovered = !stack.isEmpty() && mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell;
        float inset = CELL_INSET * unit;
        float size = cell - inset * 2.0f;
        float radius = CELL_RADIUS * unit;
        UiGlass.sunken(graphics, x + inset, y + inset, size, size, radius, hovered ? 1.0f : 0.7f);
        if (hovered || (!stack.isEmpty() && stack == marked)) {
            UiRender.rim(graphics, x + inset, y + inset, size, size, radius, 1.0f, UiTheme.alpha(UiAccent.color(), 0.8f));
        }
        if (stack.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(x + ICON_INSET * unit, y + ICON_INSET * unit, 0.0f);
        graphics.pose().scale(unit, unit, 1.0f);
        try {
            graphics.renderItem(stack, 0, 0);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    // WHY: подсказка спрашивает предмет каждый кадр, поэтому без записи выбора: только ячейка и её стак
    public ItemStack hovered(double mouseX, double mouseY) {
        int place = placeAt(mouseX, mouseY);
        return place < 0 ? ItemStack.EMPTY : stackAt(place);
    }

    private float gridLeft() {
        return left + (width - COLUMNS * cell) / 2.0f;
    }

    private float cellX(int place) {
        return gridLeft() + (place % COLUMNS) * cell;
    }

    private float cellY(int place) {
        int row = place / COLUMNS;
        if (!inventory) return top + (row - scroll) * cell;

        float hotbar = HOTBAR_GAP * unit;
        return top + UiMetrics.GAP + row * cell + (row >= 3 ? hotbar : 0.0f) + (row >= 4 ? hotbar : 0.0f);
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
