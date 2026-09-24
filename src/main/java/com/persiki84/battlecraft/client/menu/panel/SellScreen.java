package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PanelScreen;
import com.persiki84.shared.client.menu.pick.ItemPickerScreen;
import com.persiki84.shared.Names;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SellScreen extends PanelScreen {
    private static final String COMMAND = "sell";
    private static final int MAX_PRICE = 100000;
    private static final int DEFAULT_PRICE = 10;


    public SellScreen() {
        super(Component.translatable("sellmod.menu.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.SELL;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("sellmod.menu.tab.prices"), this::priceRows),
                new Page(Component.translatable("sellmod.menu.tab.remove"), this::removeRows),
                new Page(Component.translatable("sellmod.menu.tab.add"), this::addRows));
    }

    private List<AbstractWidget> removeRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (CompoundTag entry : prices()) {
            String item = entry.getString("item");
            rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Names.item(item),
                    () -> Component.translatable("sellmod.menu.action.remove"),
                    () -> send(COMMAND + " price remove " + item)).alerting());
        }
        if (rows.isEmpty()) rows.add(reading("sellmod.menu.empty", Component::empty));
        return rows;
    }

    private List<AbstractWidget> priceRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (CompoundTag entry : prices()) {
            rows.add(priceRow(entry.getString("item")));
        }
        if (rows.isEmpty()) {
            rows.add(reading("sellmod.menu.empty", Component::empty));
        }
        return rows;
    }

    private List<CompoundTag> prices() {
        ListTag stored = MenuData.state(menuId()).getList("prices", Tag.TAG_COMPOUND);
        List<CompoundTag> entries = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            entries.add(stored.getCompound(index));
        }
        return entries;
    }

    private NumberRow priceRow(String item) {
        return new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Names.item(item),
                () -> priceOf(item), value -> send(COMMAND + " price set " + item + " " + value),
                0, MAX_PRICE, 1);
    }

    private int priceOf(String item) {
        for (CompoundTag entry : prices()) {
            if (entry.getString("item").equals(item)) return entry.getInt("price");
        }
        return 0;
    }

    // WHY: предмет для цены и валюты выбирается глазами из инвентаря или реестра с поиском:
    // WHY: держать его в руке или набирать идентификатор было неудобно и ошибочно
    private List<AbstractWidget> addRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(pickRow("sellmod.menu.currency", () -> Names.item(MenuData.state(menuId()).getString("currency")),
                this::pickCurrency));
        rows.add(pickRow("sellmod.menu.set_price", () -> Component.translatable("sellmod.menu.action.pick"),
                this::pickPrice));
        return rows;
    }

    private ActionRow pickRow(String label, java.util.function.Supplier<Component> value, Runnable run) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label), value, run);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    private void pickCurrency() {
        ItemPickerScreen.open(Component.translatable("sellmod.menu.pick.currency"), this,
                ItemPickerScreen.Options.itemOnly(), choice -> send(COMMAND + " setcurrency " + choice.itemId()));
    }

    private void pickPrice() {
        ItemPickerScreen.open(Component.translatable("sellmod.menu.pick.price"), this,
                ItemPickerScreen.Options.counted(Component.translatable("sellmod.menu.price"), 0, MAX_PRICE, DEFAULT_PRICE),
                choice -> send(COMMAND + " price set " + choice.itemId() + " " + choice.amount()));
    }

}
