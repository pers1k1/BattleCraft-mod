package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PanelScreen;
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

    private int heldPrice = DEFAULT_PRICE;

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
                new Page(Component.translatable("sellmod.menu.tab.hand"), this::handRows));
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

    private List<AbstractWidget> handRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(reading("sellmod.menu.currency",
                () -> Names.item(MenuData.state(menuId()).getString("currency"))));
        rows.add(reading("sellmod.menu.held", SellScreen::heldLabel));
        rows.add(number("sellmod.menu.held_price", () -> heldPrice, value -> heldPrice = value,
                0, MAX_PRICE, 1));
        rows.add(action("sellmod.menu.set_price", "sellmod.menu.action.apply",
                () -> withHeld(id -> send(COMMAND + " price set " + id + " " + heldPrice))));
        rows.add(action("sellmod.menu.set_currency", "sellmod.menu.action.apply",
                () -> withHeld(id -> send(COMMAND + " setcurrency " + id))));
        rows.add(removePriceRow());
        return rows;
    }

    private ActionRow removePriceRow() {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("sellmod.menu.remove_price"),
                () -> Component.translatable("sellmod.menu.action.remove"),
                () -> withHeld(id -> send(COMMAND + " price remove " + id))).alerting();
    }

    private void withHeld(java.util.function.Consumer<String> action) {
        String id = heldItem();
        if (id.isEmpty()) return;

        action.accept(id);
    }

    private static Component heldLabel() {
        String id = heldItem();
        return id.isEmpty() ? Component.translatable("sellmod.menu.empty_hand") : Names.item(id);
    }

}
