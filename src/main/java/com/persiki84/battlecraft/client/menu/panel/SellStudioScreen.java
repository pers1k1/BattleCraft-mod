package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.zones.client.menu.ItemTurntable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// WHY: скупка правится так же, как магазин: цены лежат плитками предметов, выбранная плитка
// WHY: показывает предмет и его цену, новые вещи тащатся с полки, а валюта сама видна предметом
public final class SellStudioScreen extends StudioScreen {
    private static final String PRICES = "prices";
    private static final String CURRENCY = "currency";
    private static final String COMMAND = "sell ";
    private static final int ROW = 22;
    private static final int MAX_PRICE = 100000;
    private static final int ART = 110;
    private static final float TURNTABLE_HEIGHT = 70.0f;
    private static final float LINE_SCALE = 0.72f;
    private static final String DROP = "drop-price";
    private static final Component SURE = Component.translatable("studio.sure");
    private static final Component DELETE = Component.translatable("studio.delete");

    private final ItemTurntable turntable = new ItemTurntable();
    private final UiButton modeButton;
    private final List<UiButton> header;
    private final HeadingRow priceHeading = heading("studio.sell.group.price");
    private final HeadingRow guardHeading = heading("studio.sell.group.currency");
    private final HeadingRow shelfHeading = heading("studio.sell.group.new");
    private final NumberRow priceRow;
    private final NumberRow newPriceRow;
    private final ToggleRow guardRow;
    private final ActionRow removeRow;
    private final HeadingRow bulkHeading = heading("studio.bulk.group");
    private final NumberRow bulkPriceRow;
    private List<SellTile> bulkTiles = List.of();
    private String nodeKey = PRICES;
    private String selectedItem;
    private boolean adding;
    private int newPrice = 10;
    private CompoundTag builtFor;
    private List<StudioNav.Node> nodes = List.of();
    private List<SellTile> tiles = List.of();
    private SellTile currency;

    record SellTile(String item, int price, ItemStack icon, Component name, Component corner, Component plate)
            implements StudioTile {
        static SellTile of(String item, int price, boolean currency) {
            return new SellTile(item, price, stackOf(item), Names.item(item),
                    currency ? Component.translatable("studio.sell.currency_mark") : Component.empty(),
                    currency ? Component.literal(item) : Component.translatable("studio.sell.price", price));
        }

        @Override
        public boolean faded() {
            return icon.isEmpty();
        }
    }

    public SellStudioScreen() {
        this(null);
    }

    public SellStudioScreen(Screen parent) {
        super(Component.translatable("studio.sell.title"), parent);
        modeButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> switchMode())
                .hint("studio.sell.mode.hint");
        header = List.of(modeButton);
        priceRow = new NumberRow(0, 0, 10, ROW, Component.translatable("sellmod.menu.price"), this::selectedPrice,
                value -> later("price", priceCommand(value)), 1, MAX_PRICE, 1);
        priceRow.hint("studio.sell.price_row.hint");
        bulkPriceRow = new NumberRow(0, 0, 10, ROW, Component.translatable("studio.bulk.price"),
                () -> bulkTiles.isEmpty() ? 0 : bulkTiles.get(0).price(), this::bulkPrice, 1, MAX_PRICE, 1);
        bulkPriceRow.hint("studio.bulk.price.hint");
        newPriceRow = new NumberRow(0, 0, 10, ROW, Component.translatable("studio.sell.new_price"), () -> newPrice,
                value -> newPrice = value, 1, MAX_PRICE, 1);
        newPriceRow.hint("studio.sell.new_price.hint");
        guardRow = new ToggleRow(0, 0, 10, ROW, Component.translatable("sellmod.menu.currency_guard"),
                () -> state().getBoolean("currencyGuard"), value -> send(COMMAND + "currencyguard " + value));
        guardRow.hint("sellmod.menu.currency_guard.hint");
        removeRow = new ActionRow(0, 0, 10, ROW, Component.translatable("studio.sell.remove"),
                () -> armed(DROP) ? SURE : DELETE, this::pressRemove).alerting();
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private static ItemStack stackOf(String item) {
        ResourceLocation key = ResourceLocation.tryParse(item);
        Item found = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return found == Items.AIR ? ItemStack.EMPTY : new ItemStack(found);
    }

    private static CompoundTag state() {
        return MenuData.state(ModuleMenuStates.SELL);
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.SELL;
    }

    @Override
    protected Object stamp() {
        return state();
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        tiles = buildTiles(state);
        String currencyId = state.getString("currency");
        currency = currencyId.isEmpty() ? null : SellTile.of(currencyId, 0, true);
        nodes = List.of(
                StudioNav.Node.item(PRICES, Component.translatable("studio.sell.node.prices"),
                        Component.literal(String.valueOf(tiles.size())), 0, false),
                StudioNav.Node.item(CURRENCY, Component.translatable("studio.sell.node.currency"), Component.empty(),
                        0, false));
    }

    private static List<SellTile> buildTiles(CompoundTag state) {
        ListTag stored = state.getList("prices", Tag.TAG_COMPOUND);
        List<SellTile> built = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag entry = stored.getCompound(index);
            built.add(SellTile.of(entry.getString("item"), entry.getInt("price"), false));
        }
        return built;
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        ensureBuilt();
        return nodes;
    }

    @Override
    protected String selectedNode() {
        return nodeKey;
    }

    @Override
    protected void selectNode(StudioNav.Node picked) {
        nodeKey = picked.key();
        selectedItem = null;
        adding = CURRENCY.equals(nodeKey) && adding;
        grid.reset();
        turntable.rest();
    }

    private boolean currencyNode() {
        return CURRENCY.equals(nodeKey);
    }

    private SellTile selected() {
        if (currencyNode()) return currencyTile();
        if (selectedItem == null) return null;
        ensureBuilt();
        for (SellTile tile : tiles) {
            if (tile.item().equals(selectedItem)) return tile;
        }
        return null;
    }

    private SellTile currencyTile() {
        ensureBuilt();
        return currency;
    }

    private int selectedPrice() {
        SellTile tile = selected();
        return tile == null ? 0 : tile.price();
    }

    private String priceCommand(int value) {
        SellTile tile = selected();
        return tile == null || currencyNode() ? "" : COMMAND + "price set " + tile.item() + " " + value;
    }

    private void later(String key, String command) {
        if (!command.isEmpty()) commitLater(key, command);
    }

    @Override
    protected Component stats() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        SellTile shown = currencyTile();
        return Component.translatable("studio.sell.stats", tiles.size(),
                shown == null ? Component.empty() : shown.name());
    }

    @Override
    protected List<UiButton> headerButtons() {
        String key = currencyNode() ? (adding ? "studio.mode.done" : "studio.sell.change_currency")
                : (adding ? "studio.mode.done" : "studio.sell.add");
        modeButton.setMessage(Component.translatable(key));
        return header;
    }

    private void switchMode() {
        flushCommits();
        adding = !adding;
        turntable.rest();
        layout();
    }

    @Override
    protected Object inspectorSubject() {
        if (adding) return "shelf:" + nodeKey;
        SellTile tile = selected();
        return tile == null ? "node:" + nodeKey : "item:" + nodeKey + "/" + tile.item();
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        if (adding) {
            if (!currencyNode()) {
                stack.add(sized(shelfHeading, width), x);
                stack.add(sized(newPriceRow, width), x);
            }
            stack.skip(4);
            return;
        }
        if (currencyNode()) {
            stack.add(sized(guardHeading, width), x);
            stack.add(sized(guardRow, width), x);
        } else if (selected() != null) {
            stack.add(sized(priceHeading, width), x);
            stack.add(sized(priceRow, width), x);
            stack.add(sized(removeRow, width), x, 4);
        }
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    private void pressRemove() {
        if (selected() != null && confirm(DROP)) removeSelected();
    }

    private void removeSelected() {
        SellTile tile = selected();
        send(COMMAND + "price remove " + tile.item());
        selectedItem = neighbour(tile.item());
        layout();
    }

    private String neighbour(String removed) {
        List<SellTile> shown = tiles;
        for (int index = 0; index < shown.size(); index++) {
            if (!shown.get(index).item().equals(removed)) continue;
            if (index > 0) return shown.get(index - 1).item();
            return shown.size() > 1 ? shown.get(1).item() : null;
        }
        return null;
    }

    @Override
    protected void deleteSelected() {
        if (!currencyNode() && selected() != null) removeSelected();
    }

    @Override
    protected int artHeight() {
        return adding || selected() == null ? 0 : ART;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        SellTile tile = selected();
        if (tile == null || adding) return;
        turntable.render(graphics, tile.icon(), left, top, width, TURNTABLE_HEIGHT);
        float y = top + TURNTABLE_HEIGHT + 4.0f;
        UiRender.textTrackedFit(graphics, this.font, tile.name(), left + width / 2.0f, y, 12.0f, width, 0.85f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        Component detail = currencyNode() ? Component.translatable("studio.sell.art.currency")
                : Component.translatable("studio.sell.art.price", tile.price());
        artLine(graphics, detail, left, width, y + 15.0f, appear);
        artLine(graphics, Component.literal(tile.item()), left, width, y + 26.0f, appear);
    }

    private void artLine(GuiGraphics graphics, Component text, float left, float width, float y, float appear) {
        UiRender.textTrackedFit(graphics, this.font, text, left + width / 2.0f, y, 10.0f, width, LINE_SCALE, 0.0f,
                UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    @Override
    protected boolean pressArt(double mouseX, double mouseY) {
        if (artHeight() == 0 || !turntable.over(mouseX, mouseY)) return false;
        turntable.beginDrag();
        return true;
    }

    @Override
    protected boolean dragArt(double dragX, double dragY) {
        if (!turntable.dragging()) return false;
        turntable.drag(dragX, dragY);
        return true;
    }

    @Override
    protected void releaseArt() {
        turntable.endDrag();
    }

    @Override
    protected boolean scrollArt(double mouseX, double mouseY, double amount) {
        if (artHeight() == 0 || !turntable.over(mouseX, mouseY)) return false;
        turntable.magnify(amount);
        return true;
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        if (currencyNode()) {
            SellTile currency = currencyTile();
            return currency == null ? List.of() : List.of(currency);
        }
        return tiles;
    }

    @Override
    protected int selectedTile() {
        if (currencyNode()) return currencyTile() == null ? -1 : 0;
        List<SellTile> shown = tiles;
        for (int index = 0; index < shown.size(); index++) {
            if (shown.get(index).item().equals(selectedItem)) return index;
        }
        return -1;
    }

    @Override
    protected void selectTile(int index) {
        adding = false;
        turntable.rest();
        if (!currencyNode()) selectedItem = index < 0 ? null : tiles.get(index).item();
    }

    @Override
    protected boolean shelfOpen() {
        return adding;
    }

    // WHY: скупка платит за предмет по идентификатору, теги в цене не участвуют: из инвентаря и
    // WHY: из реестра предмет уходит одинаково, именем в реестре
    @Override
    protected void addPick(ItemShelf.Pick pick, int slot) {
        String item = BuiltInRegistries.ITEM.getKey(pick.stack().getItem()).toString();
        if (currencyNode()) {
            send(COMMAND + "setcurrency " + item);
            adding = false;
            layout();
            return;
        }
        send(COMMAND + "price set " + item + " " + newPrice);
        selectedItem = item;
    }

    @Override
    protected List<StudioMenu.Action> tileActions(int index) {
        if (currencyNode()) return List.of(StudioMenu.Action.of("studio.menu.change_currency", this::openShelf));
        return List.of(StudioMenu.Action.danger("studio.menu.remove_price", this::removeSelected));
    }

    @Override
    protected String tileKey(int index) {
        ensureBuilt();
        if (currencyNode() || index >= tiles.size()) return null;
        return tiles.get(index).item();
    }

    private List<SellTile> tilesAt(List<Integer> indices) {
        List<SellTile> picked = new ArrayList<>();
        for (int index : indices) {
            if (index < tiles.size()) picked.add(tiles.get(index));
        }
        return picked;
    }

    @Override
    protected List<StudioMenu.Action> bulkActions(List<Integer> indices) {
        return List.of(new StudioMenu.Action(Component.translatable("studio.menu.remove_prices", indices.size()),
                () -> removeAll(indices), true, true, true));
    }

    private void removeAll(List<Integer> indices) {
        deleteMarked(indices);
        marks.clear();
        layout();
    }

    @Override
    protected void deleteMarked(List<Integer> indices) {
        for (SellTile tile : tilesAt(indices)) {
            send(COMMAND + "price remove " + tile.item());
        }
        selectedItem = null;
    }

    @Override
    protected void buildBulk(StudioStack stack, int x, int width, List<Integer> indices) {
        bulkTiles = tilesAt(indices);
        stack.add(sized(bulkHeading, width), x);
        stack.add(sized(bulkPriceRow, width), x);
    }

    private void bulkPrice(int price) {
        for (SellTile tile : bulkTiles) {
            later("price:" + tile.item(), COMMAND + "price set " + tile.item() + " " + price);
        }
    }

    @Override
    protected List<StudioMenu.Action> nodeActions(StudioNav.Node picked) {
        return canvasActions();
    }

    @Override
    protected List<StudioMenu.Action> canvasActions() {
        String key = currencyNode() ? "studio.menu.change_currency" : "studio.menu.add";
        return List.of(StudioMenu.Action.of(key, this::openShelf));
    }

    private void openShelf() {
        flushCommits();
        adding = true;
        turntable.rest();
        layout();
    }

    @Override
    protected Component emptyCanvas() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable(currencyNode() ? "studio.sell.empty_currency" : "studio.sell.empty");
    }

    @Override
    protected Component shelfHint() {
        return Component.translatable(currencyNode() ? "studio.sell.shelf.currency" : "studio.shelf.hint");
    }

    @Override
    public void tick() {
        if (ticks % 20 == 0) MenuData.request(ModuleMenuStates.SELL);
        super.tick();
    }
}
