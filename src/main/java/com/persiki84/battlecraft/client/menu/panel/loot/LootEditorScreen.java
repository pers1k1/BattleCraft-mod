package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.SearchField;
import com.persiki84.shared.client.menu.gunsmith.GunsmithScreen;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTitle;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.zones.client.menu.ItemTurntable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY: виджеты редактора создаются один раз и переставляются, а не пересоздаются на каждый
// WHY: снимок: доступность кнопок, прокрутка цифр и нажатие живут в самом виджете, и новая копия
// WHY: на каждое обновление сбрасывала бы их, из-за чего смена таблицы шла без единой анимации
public class LootEditorScreen extends GlassScreen {
    private static final int SIDEBAR_WIDTH = 128;
    private static final int GRID_WIDTH = 322;
    private static final int SIDE_WIDTH = 240;
    private static final int GAP = 8;
    private static final int HEADER = 76;
    private static final int PANEL_LIMIT = 330;
    private static final int TITLE_TOP = 12;
    private static final int TABLE_ROW = 18;
    private static final int ROW = 22;
    private static final int ROW_GAP = 4;
    private static final int CONTROL = 16;
    private static final int HEADER_ROW = 20;
    private static final int STATS_WIDTH = 270;
    private static final int HEADER_BUTTON = 130;
    private static final float RADIUS = 9.0f;
    private static final float FIT_MARGIN = 10.0f;
    private static final float BOX_HEIGHT = 74.0f;
    private static final float LINE_SCALE = 0.72f;
    private static final int DRAG_START = 4;
    private static final int SNAPSHOT_TICKS = 20;
    private static final int SETTLE_TICKS = 40;
    private static final int COMMIT_TICKS = 6;
    private static final int WANTED_TICKS = 60;
    private static final long ARM_MILLIS = 3000L;
    private static final int PERCENT_SCALE = 100;
    private static final int DEFAULT_PERCENT = 50;

    private final LootGrid grid = new LootGrid();
    private final ItemShelf picker = new ItemShelf();
    private final LootTrial trial = new LootTrial();
    private final ItemTurntable turntable = new ItemTurntable();
    private final Map<String, UiButton> tableButtons = new HashMap<>();
    private final Map<String, String> due = new HashMap<>();
    private final Screen parent;
    private String tableName;
    private String wantedTable;
    private int wantedSince;
    private int selected = -1;
    private boolean adding;
    private int sideScroll;
    private int ticks;
    private int pendingSince = -1;
    private int commitAt = -1;
    private long deleteArmedAt;
    private List<LootSnapshot.Table> shownState;
    private int pressIndex = -1;
    private ItemShelf.Pick pressPick;
    private boolean carryingTile;
    private boolean carryingPick;
    private double pressX;
    private double pressY;
    private double pointerX;
    private double pointerY;

    private SearchField search;
    private UiButton rollButton;
    private UiButton modeButton;
    private UiButton inventoryButton;
    private UiButton everyButton;
    private UiButton copyButton;
    private UiButton removeButton;
    private UiButton gunsButton;
    private NumberRow chanceRow;
    private NumberRow minRow;
    private NumberRow maxRow;
    private NumberRow rulesMinRow;
    private NumberRow rulesMaxRow;
    private ActionRow tableGunsRow;
    private FieldRow nameRow;
    private ActionRow createRow;
    private ActionRow deleteRow;

    public LootEditorScreen(String table, Screen parent) {
        super(Component.translatable("airdrop.editor.title"));
        this.tableName = table;
        this.parent = parent;
        MenuData.invalidate(ModuleMenuStates.LOOT);
        MenuData.request(ModuleMenuStates.LOOT);
    }

    @Override
    protected void closing() {
        flushCommits();
        if (parent == null) {
            super.closing();
            return;
        }
        if (parent instanceof GlassScreen glass) glass.reenter();
        Minecraft.getInstance().setScreen(parent);
    }

    // WHY: только что созданная или выбранная таблица ждёт ответа сервера и не подменяется
    // WHY: запасной: иначе выбор слетал на global до прихода снимка с новой таблицей
    private LootSnapshot.Table current() {
        List<LootSnapshot.Table> all = LootSnapshot.all();
        if (all.isEmpty()) return null;

        LootSnapshot.Table found = tableName == null ? null : LootSnapshot.find(tableName);
        if (found != null) return found;
        if (tableName != null && tableName.equals(wantedTable) && ticks - wantedSince < WANTED_TICKS) return null;

        LootSnapshot.Table fallback = LootSnapshot.find(LootTables.AIRDROP);
        tableName = (fallback == null ? all.get(0) : fallback).table().name();
        return LootSnapshot.find(tableName);
    }

    private LootTable table() {
        LootSnapshot.Table current = current();
        return current == null ? LootTable.empty(tableName == null ? "" : tableName) : current.table();
    }

    private LootEntry selectedEntry() {
        LootTable table = table();
        return table.contains(selected) ? table.entries().get(selected) : null;
    }

    private String base() {
        return "airdrop loot " + tableName + " ";
    }

    private void send(String command) {
        MenuCommands.run(command, ModuleMenuStates.LOOT);
        pendingSince = ticks;
    }

    // WHY: число из строки уходит на сервер после паузы в наборе: удержание стрелки шагает
    // WHY: десятки раз в секунду, и каждая команда писала бы таблицу на диск и снимок всем
    private void commitLater(String key, String command) {
        due.put(key, command);
        commitAt = ticks + COMMIT_TICKS;
    }

    private void flushCommits() {
        for (String command : due.values()) {
            send(command);
        }
        due.clear();
        commitAt = -1;
    }

    private int contentWidth() {
        return SIDEBAR_WIDTH + GRID_WIDTH + SIDE_WIDTH + GAP * 2;
    }

    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    private int contentTop() {
        return Math.max(HEADER, (this.height - panelHeight()) / 2);
    }

    private int panelHeight() {
        return Math.min(this.height - HEADER - GAP * 2, PANEL_LIMIT);
    }

    private int gridLeft() {
        return contentLeft() + SIDEBAR_WIDTH + GAP;
    }

    private int sideLeft() {
        return gridLeft() + GRID_WIDTH + GAP;
    }

    private int sideRowsLeft() {
        return sideLeft() + (int) UiMetrics.PAD;
    }

    private int sideRowsWidth() {
        return SIDE_WIDTH - (int) UiMetrics.PAD * 2;
    }

    private int headerTop() {
        return contentTop() - HEADER_ROW - GAP;
    }

    private int statsLeft() {
        return (this.width - (STATS_WIDTH + HEADER_BUTTON * 2 + GAP * 2)) / 2;
    }

    @Override
    protected float revealTop() {
        return contentTop();
    }

    @Override
    protected float revealSpan() {
        return panelHeight();
    }

    @Override
    protected float contentScale() {
        float needed = contentWidth() + FIT_MARGIN * 2.0f;
        return needed <= this.width ? 1.0f : this.width / needed;
    }

    @Override
    protected void init() {
        if (search == null) createWidgets();
        layout();
    }

    private void createWidgets() {
        search = new SearchField(Component.translatable("airdrop.editor.search"), picker::search);
        rollButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.translatable("airdrop.editor.roll"),
                pressed -> trial.roll(table())).hint("airdrop.editor.roll.hint");
        modeButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> switchMode());
        int half = (sideRowsWidth() - ROW_GAP) / 2;
        inventoryButton = new UiButton(0, 0, half, CONTROL, Component.translatable("airdrop.editor.source.inventory"),
                pressed -> showSource(true)).lit();
        everyButton = new UiButton(0, 0, half, CONTROL, Component.translatable("airdrop.editor.source.all"),
                pressed -> showSource(false)).lit().hint("airdrop.editor.source.all.hint");
        createEntryWidgets();
        createTableWidgets();
    }

    private void createEntryWidgets() {
        chanceRow = row(new NumberRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.chance"),
                () -> entryValue(entry -> Math.round(entry.chance() * 100.0f * PERCENT_SCALE)),
                value -> commitLater("chance", base() + selected + " chance " + LootChance.argument(value / (float) PERCENT_SCALE)),
                1, (int) LootChance.HIGHEST * PERCENT_SCALE, PERCENT_SCALE / 2).scaledBy(PERCENT_SCALE).trimmed(),
                "airdrop.editor.chance");
        minRow = row(new NumberRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.min"),
                () -> entryValue(LootEntry::min), value -> commitCount(true, value), 1, LootEntry.MAX_COUNT, 1),
                "airdrop.editor.min");
        maxRow = row(new NumberRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.max"),
                () -> entryValue(LootEntry::max), value -> commitCount(false, value), 1, LootEntry.MAX_COUNT, 1),
                "airdrop.editor.max");
        int third = (sideRowsWidth() - ROW_GAP * 2) / 3;
        copyButton = new UiButton(0, 0, third, CONTROL, Component.translatable("airdrop.editor.copy"),
                pressed -> send(base() + selected + " duplicate"));
        removeButton = new UiButton(0, 0, third, CONTROL, Component.translatable("airdrop.editor.remove"),
                pressed -> removeSelected());
        gunsButton = new UiButton(0, 0, third, CONTROL, Component.translatable("airdrop.editor.guns"),
                pressed -> openGuns(selected));
    }

    private void createTableWidgets() {
        rulesMinRow = row(new NumberRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.rules_min"),
                () -> table().minItems(), value -> commitRules(value, table().maxItems()), 0, LootTable.ITEMS_LIMIT, 1),
                "airdrop.editor.rules_min");
        rulesMaxRow = row(new NumberRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.rules_max"),
                () -> table().maxItems(), value -> commitRules(table().minItems(), value), 0, LootTable.ITEMS_LIMIT, 1)
                .floorLabel(Component.translatable("airdrop.editor.rules_max.none")), "airdrop.editor.rules_max");
        tableGunsRow = row(new ActionRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.guns_table"),
                () -> Component.translatable("gunsmith.open"), () -> openGuns(-1)), "airdrop.editor.guns_table");
        nameRow = new FieldRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.new_table"),
                Component.translatable("airdrop.editor.new_table.hint"), "", LootTables.NAME_LIMIT, text -> {});
        nameRow.box().setFilter(text -> text.chars().allMatch(LootEditorScreen::nameChar));
        createRow = row(new ActionRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.create"),
                () -> Component.translatable("airdrop.editor.create.value"), this::createTable), "airdrop.editor.create");
        deleteRow = row(new ActionRow(0, 0, sideRowsWidth(), ROW, Component.translatable("airdrop.editor.delete"),
                () -> Component.translatable(armed() ? "airdrop.editor.delete.sure" : "airdrop.editor.delete.value"),
                this::deleteTable).alerting(), "airdrop.editor.delete");
    }

    private static <T extends MenuRow> T row(T row, String label) {
        row.hint(label + ".hint");
        return row;
    }

    private int entryValue(java.util.function.ToIntFunction<LootEntry> read) {
        LootEntry entry = selectedEntry();
        return entry == null ? 0 : read.applyAsInt(entry);
    }

    private static boolean nameChar(int symbol) {
        return symbol == '_' || (symbol >= 'a' && symbol <= 'z') || (symbol >= '0' && symbol <= '9');
    }

    private void layout() {
        boolean searching = search.typing();
        boolean naming = nameRow.capturing();
        clearWidgets();
        placeSidebar();
        placeHeader();
        if (adding) {
            placePicker();
        } else if (selectedEntry() != null) {
            placeEntry();
        } else {
            placeTable();
        }
        if (searching && adding && !picker.showsInventory()) setFocused(search.box());
        if (naming && !adding && selectedEntry() == null) setFocused(nameRow);
    }

    private void placeSidebar() {
        int left = contentLeft() + (int) UiMetrics.GAP;
        int width = SIDEBAR_WIDTH - (int) UiMetrics.GAP * 2;
        List<LootSnapshot.Table> all = LootSnapshot.all();
        int capacity = Math.max(1, (panelHeight() - (int) UiMetrics.PAD * 2) / TABLE_ROW);
        sideScroll = Math.max(0, Math.min(sideScroll, all.size() - capacity));
        int y = contentTop() + (int) UiMetrics.PAD;
        for (int index = sideScroll; index < Math.min(all.size(), sideScroll + capacity); index++) {
            placeTableButton(all.get(index), left, y, width);
            y += TABLE_ROW;
        }
    }

    private void placeTableButton(LootSnapshot.Table entry, int left, int y, int width) {
        String name = entry.table().name();
        UiButton button = tableButtons.computeIfAbsent(name, key ->
                new UiButton(0, 0, width, TABLE_ROW - 2, Component.empty(), pressed -> openTable(key)).lit());
        button.setPosition(left, y);
        button.setMessage(Component.literal(name + "  " + entry.table().entries().size()));
        button.active = !name.equals(tableName);
        button.hint(usage(entry));
        addRenderableWidget(button);
    }

    private static Component usage(LootSnapshot.Table entry) {
        if (entry.airdrop()) return Component.translatable("airdrop.editor.used.airdrop", entry.caches());
        return Component.translatable("airdrop.editor.used.caches", entry.caches());
    }

    private void placeHeader() {
        int left = statsLeft() + STATS_WIDTH + GAP;
        rollButton.setPosition(left, headerTop());
        rollButton.active = !table().entries().isEmpty();
        modeButton.setPosition(left + HEADER_BUTTON + GAP, headerTop());
        modeButton.setMessage(Component.translatable(adding ? "airdrop.editor.mode.done" : "airdrop.editor.mode.add"));
        addRenderableWidget(rollButton);
        addRenderableWidget(modeButton);
    }

    private void placePicker() {
        int left = sideRowsLeft();
        int top = contentTop() + (int) UiMetrics.PAD;
        inventoryButton.setPosition(left, top);
        inventoryButton.active = !picker.showsInventory();
        everyButton.setPosition(left + inventoryButton.getWidth() + ROW_GAP, top);
        everyButton.active = picker.showsInventory();
        addRenderableWidget(inventoryButton);
        addRenderableWidget(everyButton);
        if (picker.showsInventory()) return;

        search.place(left, top + CONTROL + 6, sideRowsWidth());
        addRenderableWidget(search.box());
    }

    private void placeEntry() {
        int bottom = contentTop() + panelHeight() - (int) UiMetrics.PAD;
        int y = bottom - CONTROL - ROW_GAP - (ROW + ROW_GAP) * 3;
        placeRow(chanceRow, y);
        placeRow(minRow, y + ROW + ROW_GAP);
        placeRow(maxRow, y + (ROW + ROW_GAP) * 2);
        placeEntryButtons(bottom - CONTROL);
    }

    private void placeRow(MenuRow row, int y) {
        row.setX(sideRowsLeft());
        row.setY(y);
        addRenderableWidget(row);
    }

    private void placeEntryButtons(int y) {
        boolean gun = GunSmith.isGun(LootSnapshot.stack(selectedEntry()));
        int step = copyButton.getWidth() + ROW_GAP;
        copyButton.setPosition(sideRowsLeft(), y);
        removeButton.setPosition(sideRowsLeft() + step, y);
        gunsButton.setPosition(sideRowsLeft() + step * 2, y);
        gunsButton.active = gun;
        addRenderableWidget(copyButton);
        addRenderableWidget(removeButton);
        addRenderableWidget(gunsButton);
    }

    private void placeTable() {
        int y = contentTop() + (int) UiMetrics.PAD + 30;
        placeRow(rulesMinRow, y);
        placeRow(rulesMaxRow, y + ROW + ROW_GAP);
        tableGunsRow.active = tableName != null && !new LootGunBench(tableName).guns().isEmpty();
        placeRow(tableGunsRow, y + (ROW + ROW_GAP) * 2);
        int bottom = contentTop() + panelHeight() - (int) UiMetrics.PAD;
        placeRow(nameRow, bottom - ROW * 3 - ROW_GAP * 2);
        placeRow(createRow, bottom - ROW * 2 - ROW_GAP);
        LootSnapshot.Table current = current();
        deleteRow.active = current != null && !current.airdrop() && current.caches() == 0;
        placeRow(deleteRow, bottom - ROW);
    }

    private void openTable(String name) {
        flushCommits();
        tableName = name;
        selected = -1;
        adding = false;
        grid.reset();
        turntable.rest();
        trial.close();
        layout();
    }

    private void switchMode() {
        adding = !adding;
        turntable.rest();
        layout();
    }

    private void showSource(boolean inventory) {
        picker.showInventory(inventory);
        if (!inventory) picker.search(search.query());
        layout();
    }

    private void openGuns(int entryIndex) {
        if (tableName == null) return;
        LootGunBench bench = new LootGunBench(tableName);
        GunsmithScreen.open(bench, entryIndex < 0 ? 0 : bench.positionOf(entryIndex), this);
    }

    private void commitCount(boolean lower, int value) {
        LootEntry entry = selectedEntry();
        if (entry == null) return;
        int min = lower ? value : Math.min(entry.min(), value);
        int max = lower ? Math.max(entry.max(), value) : value;
        commitLater("count", base() + selected + " count " + min + " " + max);
    }

    private void commitRules(int min, int max) {
        int low = max != LootTable.UNCAPPED ? Math.min(min, max) : min;
        commitLater("rules", base() + "items " + low + " " + max);
    }

    private void createTable() {
        String name = nameRow.value();
        if (!LootTables.validName(name)) {
            MenuFeedback.show(Component.translatable("airdrop.loot.bad_name", LootTables.NAME_LIMIT), true);
            return;
        }
        if (LootSnapshot.find(name) != null) {
            MenuFeedback.show(Component.translatable("airdrop.loot.table_exists", name), true);
            return;
        }
        send("airdrop loot create " + name);
        nameRow.box().setValue("");
        wantedTable = name;
        wantedSince = ticks;
        openTable(name);
    }

    private boolean armed() {
        return System.currentTimeMillis() - deleteArmedAt < ARM_MILLIS;
    }

    // WHY: удаление таблицы необратимо, поэтому идёт вторым нажатием в течение трёх секунд, а после
    // WHY: удаления выбор встаёт на соседнюю таблицу, а не прыгает к первой
    private void deleteTable() {
        if (!armed()) {
            deleteArmedAt = System.currentTimeMillis();
            return;
        }
        deleteArmedAt = 0L;
        String removed = tableName;
        send("airdrop loot delete " + removed);
        openTable(neighbourTable(removed));
    }

    private String neighbourTable(String removed) {
        List<String> names = LootSnapshot.names();
        int index = names.indexOf(removed);
        if (index > 0) return names.get(index - 1);
        return names.size() > 1 ? names.get(1) : LootTables.AIRDROP;
    }

    private void removeSelected() {
        if (selectedEntry() == null) return;

        flushCommits();
        send(base() + selected + " remove");
        int left = table().entries().size() - 1;
        selected = left <= 0 ? -1 : Math.max(0, Math.min(selected, left - 1));
        layout();
    }

    @Override
    public void tick() {
        ticks++;
        if (commitAt >= 0 && ticks >= commitAt && !typingRow()) flushCommits();
        if (ticks % SNAPSHOT_TICKS == 0) MenuData.request(ModuleMenuStates.LOOT);
        if (pendingSince >= 0 && ticks - pendingSince > SETTLE_TICKS) {
            pendingSince = -1;
            grid.settle();
        }
        List<LootSnapshot.Table> state = LootSnapshot.all();
        if (state == shownState || isDragging() || carryingTile || carryingPick || typingRow()) return;

        shownState = state;
        pendingSince = -1;
        grid.settle();
        if (selected >= table().entries().size()) selected = table().entries().size() - 1;
        layout();
    }

    private boolean typingRow() {
        return getFocused() instanceof MenuRow row && row.capturing();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        pointerX = mouseX;
        pointerY = mouseY;
        UiTitle.render(graphics, this.font, getTitle(), this.width / 2.0f, TITLE_TOP, 1.0f, 0.0f, UiAccent.text());
        renderStats(graphics);
        int top = contentTop();
        int height = panelHeight();
        UiGlass.window(graphics, contentLeft(), top, SIDEBAR_WIDTH, height, RADIUS, 1.0f);
        UiGlass.window(graphics, gridLeft(), top, GRID_WIDTH, height, RADIUS, 1.0f);
        UiGlass.window(graphics, sideLeft(), top, SIDE_WIDTH, height, RADIUS, 1.0f);
        UiGlass.layer(graphics);
        if (adding && !picker.showsInventory()) search.render(graphics);
        renderGrid(graphics, mouseX, mouseY);
        renderSide(graphics, mouseX, mouseY);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderCarriedPick(graphics);
        trial.render(graphics, this.width / 2.0f, top + height / 2.0f, mouseX, mouseY);
        MenuFeedback.render(graphics, this.width / 2.0f, top + height + GAP);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderStats(GuiGraphics graphics) {
        LootTable table = table();
        Component text = LootSnapshot.arrived() && current() != null
                ? Component.translatable("airdrop.editor.stats", table.name(), table.entries().size(),
                        LootChance.shown(LootOdds.positions(table)), LootChance.shown(LootOdds.items(table)))
                : Component.translatable("battlecraft.menu.waiting");
        UiGlass.panel(graphics, statsLeft(), headerTop(), STATS_WIDTH, HEADER_ROW, UiMetrics.radius(HEADER_ROW), 1.0f);
        UiRender.textTrackedFit(graphics, this.font, text, statsLeft() + STATS_WIDTH / 2.0f, headerTop(), HEADER_ROW,
                STATS_WIDTH - UiMetrics.PAD_WIDE * 2.0f, 0.8f, 0.0f, UiAccent.text(), false);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        grid.place(gridLeft(), contentTop() + UiMetrics.PAD, GRID_WIDTH, panelHeight() - UiMetrics.PAD * 2.0f);
        LootTable table = table();
        if (table.entries().isEmpty()) {
            String key = LootSnapshot.arrived() && current() != null ? "airdrop.editor.empty" : "battlecraft.menu.waiting";
            renderWrapped(graphics, Component.translatable(key), gridLeft() + GRID_WIDTH / 2.0f,
                    contentTop() + panelHeight() / 2.0f, GRID_WIDTH);
            return;
        }
        grid.render(graphics, table, selected, mouseX, mouseY, carryingTile);
    }

    private void renderSide(GuiGraphics graphics, int mouseX, int mouseY) {
        if (adding) {
            renderPicker(graphics, mouseX, mouseY);
            return;
        }
        LootEntry entry = selectedEntry();
        if (entry != null) {
            renderEntry(graphics, entry);
            return;
        }
        renderTableHead(graphics);
    }

    private void renderPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = contentTop() + (int) UiMetrics.PAD + CONTROL + 6 + (picker.showsInventory() ? 0 : CONTROL + 6);
        picker.place(sideLeft() + UiMetrics.PAD, top, SIDE_WIDTH - UiMetrics.PAD * 2.0f,
                contentTop() + panelHeight() - top - UiMetrics.PAD - 14.0f);
        picker.render(graphics, mouseX, mouseY);
        line(graphics, Component.translatable("airdrop.editor.picker.hint"), sideLeft() + UiMetrics.PAD,
                SIDE_WIDTH - UiMetrics.PAD * 2.0f, contentTop() + panelHeight() - UiMetrics.PAD - 11.0f);
    }

    private void renderTableHead(GuiGraphics graphics) {
        LootSnapshot.Table current = current();
        float left = sideLeft() + UiMetrics.PAD;
        float width = SIDE_WIDTH - UiMetrics.PAD * 2.0f;
        float top = contentTop() + UiMetrics.PAD;
        Component name = Component.literal(tableName == null ? "" : tableName);
        UiRender.textTrackedFit(graphics, this.font, name, left + width / 2.0f, top, 14.0f, width, 0.9f, 0.0f,
                UiAccent.text(), false);
        if (current != null) line(graphics, usage(current), left, width, top + 15.0f);
    }

    private void renderEntry(GuiGraphics graphics, LootEntry entry) {
        ItemStack stack = LootSnapshot.stack(entry);
        float left = sideLeft() + UiMetrics.PAD_WIDE;
        float width = SIDE_WIDTH - UiMetrics.PAD_WIDE * 2.0f;
        float top = contentTop() + UiMetrics.PAD_WIDE;
        turntable.render(graphics, stack, left, top, width, BOX_HEIGHT);
        float y = top + BOX_HEIGHT + UiMetrics.GAP;
        Component name = stack.isEmpty() ? Component.translatable("airdrop.editor.unknown", entry.itemId().toString())
                : stack.getHoverName();
        UiRender.textTrackedFit(graphics, this.font, name, left + width / 2.0f, y, 12.0f, width, 0.85f, 0.0f,
                UiAccent.text(), false);
        line(graphics, Component.translatable("airdrop.editor.real",
                LootChance.shown(LootOdds.appears(table(), selected) * 100.0f)), left, width, y + 15.0f);
        line(graphics, Component.literal(entry.itemId().toString()), left, width, y + 26.0f);
    }

    private void line(GuiGraphics graphics, Component text, float left, float width, float y) {
        UiRender.textTrackedFit(graphics, this.font, text, left + width / 2.0f, y, 10.0f, width, LINE_SCALE, 0.0f,
                UiAccent.textDim(), false);
    }

    private void renderWrapped(GuiGraphics graphics, Component text, float centerX, float centerY, float width) {
        List<FormattedCharSequence> lines = UiRender.split(graphics, this.font, text, LINE_SCALE,
                (int) ((width - UiMetrics.PAD_WIDE * 2.0f) / LINE_SCALE));
        float step = this.font.lineHeight * LINE_SCALE + UiMetrics.GAP;
        float y = centerY - lines.size() * step / 2.0f;
        for (FormattedCharSequence sequence : lines) {
            UiRender.textCentered(graphics, this.font, Component.literal(UiRender.flatten(sequence)), centerX, y,
                    LINE_SCALE, UiAccent.textDim(), false);
            y += step;
        }
    }

    private void renderCarriedPick(GuiGraphics graphics) {
        if (!carryingPick || pressPick == null) return;

        graphics.pose().pushPose();
        graphics.pose().translate(pointerX - 12.0f, pointerY - 12.0f, 200.0f);
        graphics.pose().scale(1.5f, 1.5f, 1.0f);
        try {
            graphics.renderItem(pressPick.stack(), 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (carryingTile || carryingPick) return;

        ItemStack stack = hoveredStack(mouseX, mouseY);
        if (stack.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 400.0f);
        graphics.renderTooltip(this.font, stack, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private ItemStack hoveredStack(int mouseX, int mouseY) {
        if (trial.open()) return trial.hovered(mouseX, mouseY);
        if (adding && picker.over(mouseX, mouseY)) return picker.hovered(mouseX, mouseY);
        if (!grid.over(mouseX, mouseY)) return ItemStack.EMPTY;

        int index = grid.pick(mouseX, mouseY, table().entries().size());
        return index < 0 ? ItemStack.EMPTY : LootSnapshot.stack(table().entries().get(index));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leaving()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        if (trial.open()) {
            if (!trial.contains(x, y)) {
                trial.close();
            } else if (button == 0) {
                trial.roll(table());
            }
            return true;
        }
        if (button == 0 && !adding && selectedEntry() != null && turntable.over(x, y)) {
            turntable.beginDrag();
            return true;
        }
        if (button == 0 && grid.over(x, y)) return pressGrid(x, y);
        if (button == 0 && adding && picker.over(x, y)) return pressPicker(x, y);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // WHY: щелчок по пустому месту сетки снимает выбор: так справа открываются настройки таблицы
    private boolean pressGrid(double x, double y) {
        int index = grid.pick(x, y, table().entries().size());
        if (index < 0) {
            if (selected >= 0 && !adding) select(-1);
            return true;
        }
        pressIndex = index;
        pressX = x;
        pressY = y;
        if (index != selected || adding) {
            adding = false;
            select(index);
        }
        return true;
    }

    private void select(int index) {
        flushCommits();
        selected = index;
        turntable.rest();
        layout();
    }

    private boolean pressPicker(double x, double y) {
        pressPick = picker.pick(x, y);
        pressX = x;
        pressY = y;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (leaving()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        if (turntable.dragging()) {
            turntable.drag(dragX, dragY);
            return true;
        }
        if (pressIndex >= 0) return dragTile(x, y);
        if (pressPick != null) {
            carryingPick = carryingPick || moved(x, y);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean dragTile(double x, double y) {
        if (!carryingTile && moved(x, y)) {
            carryingTile = true;
            grid.carry(pressIndex, x, y);
        }
        if (carryingTile) grid.carryTo(x, y, table().entries().size());
        return true;
    }

    private boolean moved(double x, double y) {
        return Math.abs(x - pressX) > DRAG_START || Math.abs(y - pressY) > DRAG_START;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        turntable.endDrag();
        double x = localX(mouseX);
        double y = localY(mouseY);
        if (carryingTile) dropTile();
        if (pressPick != null) dropPick(x, y);
        pressIndex = -1;
        pressPick = null;
        carryingTile = false;
        carryingPick = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dropTile() {
        int[] move = grid.drop();
        if (move[0] < 0 || move[0] == move[1]) {
            grid.settle();
            return;
        }
        flushCommits();
        send(base() + move[0] + " move " + move[1]);
        selected = move[1];
        layout();
    }

    // WHY: предмет добавляется в конец таблицы и сразу переносится туда, где его отпустили:
    // WHY: команды доходят до сервера по порядку, поэтому номер новой записи равен прежнему размеру
    private void dropPick(double x, double y) {
        int total = table().entries().size();
        boolean overGrid = grid.over(x, y);
        if (carryingPick && !overGrid) return;

        flushCommits();
        send(addCommand(pressPick));
        int wanted = overGrid ? grid.slotAt(x, y, total) : total;
        if (wanted < total) send(base() + total + " move " + wanted);
    }

    private String addCommand(ItemShelf.Pick pick) {
        if (pick.fromInventory()) {
            int count = Math.max(1, Math.min(LootEntry.MAX_COUNT, pick.stack().getCount()));
            return base() + "slot " + pick.slot() + " " + count + " " + count + " " + DEFAULT_PERCENT;
        }
        return base() + "add " + BuiltInRegistries.ITEM.getKey(pick.stack().getItem()) + " 1 1 " + DEFAULT_PERCENT;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving() || trial.open()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        int step = amount > 0 ? -1 : 1;
        if (x < contentLeft() + SIDEBAR_WIDTH) {
            sideScroll = Math.max(0, sideScroll + step);
            layout();
        } else if (grid.over(x, y)) {
            grid.scrollBy(step, table().entries().size());
        } else if (adding && picker.over(x, y)) {
            picker.scrollBy(step);
        } else if (!adding && turntable.over(x, y)) {
            turntable.magnify(amount);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && trial.open()) {
            trial.close();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && nameRow.capturing()) {
            createTable();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE && !typingRow() && !search.typing() && selectedEntry() != null) {
            removeSelected();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
