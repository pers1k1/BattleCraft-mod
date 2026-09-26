package com.persiki84.shared.client.menu.studio;

import com.persiki84.shared.client.menu.SearchField;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StudioShelf {
    private static final int CONTROL = 16;
    private static final int SPACING = 6;
    private static final long DOUBLE_CLICK_MS = 320L;

    private final ItemShelf items = new ItemShelf();
    private final SearchField search;
    private final UiButton inventory;
    private final UiButton every;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private ItemShelf.Pick lastPick;
    private long lastPickAt;
    private int searchTop;
    private final Runnable relayout;

    public StudioShelf(Runnable relayout) {
        this.relayout = relayout;
        search = new SearchField(Component.translatable("studio.shelf.search"), items::search);
        inventory = new UiButton(0, 0, 10, CONTROL, Component.translatable("studio.shelf.inventory"),
                pressed -> show(true)).lit();
        every = new UiButton(0, 0, 10, CONTROL, Component.translatable("studio.shelf.all"),
                pressed -> show(false)).lit().hint("studio.shelf.all.hint");
    }

    // WHY: доступность кнопок источника и поле поиска ставятся раскладкой экрана: без неё обе
    // WHY: кнопки застывали в прежнем виде, и вернуться к инвентарю было нечем
    private void show(boolean inventoryWanted) {
        items.showInventory(inventoryWanted);
        if (!inventoryWanted) items.search(search.query());
        relayout.run();
    }

    public List<AbstractWidget> place(int x, int y, int width) {
        widgets.clear();
        int half = (width - SPACING) / 2;
        inventory.setWidth(half);
        every.setWidth(width - half - SPACING);
        inventory.setPosition(x, y);
        every.setPosition(x + half + SPACING, y);
        inventory.active = !items.showsInventory();
        every.active = items.showsInventory();
        widgets.add(inventory);
        widgets.add(every);
        searchTop = y + CONTROL + SPACING;
        if (!items.showsInventory()) {
            search.place(x, searchTop, width);
            widgets.add(search.box());
        }
        return widgets;
    }

    public int itemsTop() {
        return searchTop + (items.showsInventory() ? 0 : CONTROL + SPACING);
    }

    public boolean typing() {
        return !items.showsInventory() && search.typing();
    }

    public void render(GuiGraphics graphics, float left, float width, float bottom, int mouseX, int mouseY) {
        if (!items.showsInventory()) search.render(graphics);
        float top = itemsTop();
        items.place(left, top, width, bottom - top);
        items.render(graphics, mouseX, mouseY);
    }

    public boolean over(double mouseX, double mouseY) {
        return items.over(mouseX, mouseY);
    }

    public ItemShelf.Pick pick(double mouseX, double mouseY) {
        return items.pick(mouseX, mouseY);
    }

    public ItemStack hovered(double mouseX, double mouseY) {
        return items.hovered(mouseX, mouseY);
    }

    public void scrollBy(int step) {
        items.scrollBy(step);
    }

    // WHY: двойной щелчок по предмету добавляет его в конец без переноса: так добавляют подряд
    // WHY: десяток вещей, не таская каждую через весь экран
    public boolean doubled(ItemShelf.Pick pick) {
        long now = System.currentTimeMillis();
        boolean same = lastPick != null && pick != null && lastPick.slot() == pick.slot()
                && ItemStack.isSameItemSameTags(lastPick.stack(), pick.stack());
        boolean doubled = same && now - lastPickAt < DOUBLE_CLICK_MS;
        lastPick = doubled ? null : pick;
        lastPickAt = now;
        return doubled;
    }
}
