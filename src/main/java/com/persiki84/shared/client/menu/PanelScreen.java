package com.persiki84.shared.client.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public abstract class PanelScreen extends ManagerScreen {
    protected static final int ROW_MARGIN = 24;

    private int tab;
    private int shownState;

    protected PanelScreen(Component title) {
        super(title);
    }

    protected abstract String menuId();

    protected abstract List<Page> pages();

    @Override
    protected List<Component> tabs() {
        List<Component> titles = new ArrayList<>();
        for (Page page : pages()) {
            titles.add(page.title());
        }
        return titles;
    }

    @Override
    protected int activeTab() {
        return tab;
    }

    public void openOn(int index) {
        tab = Math.max(0, index);
    }

    @Override
    protected void pickTab(int index) {
        tab = index;
        rowScroll = 0;
    }

    @Override
    protected void buildBody() {
        List<Page> pages = pages();
        if (pages.isEmpty()) return;

        tab = Math.max(0, Math.min(tab, pages.size() - 1));
        place(pages.get(tab).rows().get());
    }

    @Override
    protected List<AbstractWidget> rowsOf(int page) {
        List<Page> pages = pages();
        if (page < 0 || page >= pages.size()) return null;
        return pages.get(page).rows().get();
    }

    @Override
    protected int desiredRows() {
        return shownRows;
    }

    @Override
    protected int rowsLeft() {
        return contentLeft() + ROW_MARGIN;
    }

    @Override
    protected int rowsWidth() {
        return CONTENT_WIDTH - ROW_MARGIN * 2;
    }

    protected NumberRow number(String label, IntSupplier value, IntConsumer apply,
                               int minimum, int maximum, int step) {
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                value, apply, minimum, maximum, step);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    protected ToggleRow toggle(String label, BooleanSupplier value, Consumer<Boolean> apply) {
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), value, apply);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    protected PickRow pick(String label, List<Component> options, IntSupplier index, IntConsumer apply) {
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                options, index, apply);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    protected ActionRow action(Component label, Component value, Runnable run) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, label, () -> value, run);
    }

    protected void send(String command) {
        MenuCommands.run(command, menuId());
    }

    @Override
    protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (MenuData.state(menuId()).isEmpty()) {
            renderHint(graphics, Component.translatable("battlecraft.menu.waiting"),
                    contentTop() + contentHeight() / 2.0f);
        }
    }

    // WHY: снимок приходит по таймеру, а набранное живёт в самой строке: пересборка посреди
    // WHY: набора числа стирала ввод молча, и в команду уходило прежнее значение строки
    @Override
    public void tick() {
        MenuData.request(menuId());

        List<Page> pages = pages();
        if (pages.isEmpty() || tab >= pages.size() || typingInRow()) return;

        int stateHash = MenuData.state(menuId()).hashCode();
        if (pages.get(tab).rows().get().size() != sourceRows || stateHash != shownState) {
            shownState = stateHash;
            rebuild();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving()) return true;

        return scrollRows(amount > 0 ? -1 : 1) || true;
    }

    public record Page(Component title, Supplier<List<AbstractWidget>> rows) {}
}
