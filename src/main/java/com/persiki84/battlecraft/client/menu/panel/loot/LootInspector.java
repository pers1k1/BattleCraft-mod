package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.gunsmith.GunsmithScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.zones.client.menu.ItemTurntable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class LootInspector {
    private static final int ROW = 22;
    private static final int CONTROL = 16;
    private static final int GAP = 4;
    private static final int PERCENT_SCALE = 100;
    private static final int DEFAULT_PERCENT = 50 * PERCENT_SCALE;
    private static final int ENTRY_ART = 110;
    private static final int TABLE_ART = 44;
    private static final float TURNTABLE_HEIGHT = 70.0f;
    private static final float LINE_SCALE = 0.72f;
    private static final String DROP_ENTRY = "drop-entry";
    private static final String DROP_TABLE = "drop-table";
    private static final Component SURE = Component.translatable("studio.sure");
    private static final Component DELETE = Component.translatable("studio.delete");

    private final AirDropStudioScreen screen;
    private final ItemTurntable turntable = new ItemTurntable();
    private HeadingRow entryHeading;
    private NumberRow chanceRow;
    private NumberRow minRow;
    private NumberRow maxRow;
    private UiButton copyButton;
    private UiButton gunsButton;
    private UiButton removeButton;
    private HeadingRow rulesHeading;
    private NumberRow rulesMinRow;
    private NumberRow rulesMaxRow;
    private ActionRow tableGunsRow;
    private ActionRow airdropRow;
    private HeadingRow createHeading;
    private FieldRow nameRow;
    private ActionRow createRow;
    private ActionRow deleteRow;
    private HeadingRow shelfHeading;
    private NumberRow newChanceRow;
    private int newPercent = DEFAULT_PERCENT;
    private boolean focusName;
    private boolean shownArmed;

    LootInspector(AirDropStudioScreen screen) {
        this.screen = screen;
        createEntryRows();
        createTableRows();
        createMakingRows();
    }

    private void createEntryRows() {
        entryHeading = heading("studio.loot.group.entry");
        chanceRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.chance"),
                () -> entryValue(entry -> Math.round(entry.chance() * 100.0f * PERCENT_SCALE)),
                value -> screen.later("chance", screen.base() + screen.entryIndex() + " chance "
                        + LootChance.argument(value / (float) PERCENT_SCALE)),
                1, (int) LootChance.HIGHEST * PERCENT_SCALE, PERCENT_SCALE / 2).scaledBy(PERCENT_SCALE).trimmed(),
                "airdrop.editor.chance");
        minRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.min"),
                () -> entryValue(LootEntry::min), value -> commitCount(true, value), 1, LootEntry.MAX_COUNT, 1),
                "airdrop.editor.min");
        maxRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.max"),
                () -> entryValue(LootEntry::max), value -> commitCount(false, value), 1, LootEntry.MAX_COUNT, 1),
                "airdrop.editor.max");
        copyButton = new UiButton(0, 0, 10, CONTROL, Component.translatable("studio.copy"), pressed -> screen.copyEntry())
                .hint("studio.loot.copy.hint");
        gunsButton = new UiButton(0, 0, 10, CONTROL, Component.translatable("studio.guns"), pressed -> openGuns(true))
                .hint("studio.guns.hint");
        removeButton = new UiButton(0, 0, 10, CONTROL, DELETE, pressed -> pressRemove());
    }

    private void createTableRows() {
        rulesHeading = heading("studio.loot.group.rules");
        rulesMinRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.rules_min"),
                () -> screen.table().minItems(), value -> commitRules(value, screen.table().maxItems()),
                0, LootTable.ITEMS_LIMIT, 1), "airdrop.editor.rules_min");
        rulesMaxRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.rules_max"),
                () -> screen.table().maxItems(), value -> commitRules(screen.table().minItems(), value),
                0, LootTable.ITEMS_LIMIT, 1).floorLabel(Component.translatable("airdrop.editor.rules_max.none")),
                "airdrop.editor.rules_max");
        tableGunsRow = hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.guns_table"),
                () -> Component.translatable("gunsmith.open"), () -> openGuns(false)), "airdrop.editor.guns_table");
        airdropRow = hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("studio.loot.make_airdrop"),
                () -> Component.translatable("studio.loot.make_airdrop.value"),
                () -> screen.now("airdrop config table " + screen.tableName())), "studio.loot.make_airdrop");
        deleteRow = new ActionRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.delete"),
                () -> screen.isArmed(DROP_TABLE) ? SURE : DELETE, this::pressDeleteTable).alerting();
        deleteRow.hint("airdrop.editor.delete.hint");
    }

    private void createMakingRows() {
        createHeading = heading("studio.loot.group.new_table");
        nameRow = new FieldRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.new_table"),
                Component.translatable("airdrop.editor.new_table.hint"), "", LootTables.NAME_LIMIT, text -> {});
        nameRow.box().setFilter(text -> text.chars().allMatch(LootInspector::nameChar));
        createRow = hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("airdrop.editor.create"),
                () -> Component.translatable("airdrop.editor.create.value"), this::createTable), "airdrop.editor.create");
        shelfHeading = heading("studio.loot.group.new_entries");
        newChanceRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("studio.loot.new_chance"),
                () -> newPercent, value -> newPercent = value, 1, (int) LootChance.HIGHEST * PERCENT_SCALE,
                PERCENT_SCALE / 2).scaledBy(PERCENT_SCALE).trimmed(), "studio.loot.new_chance");
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private static <T extends MenuRow> T hinted(T row, String key) {
        row.hint(key + ".hint");
        return row;
    }

    private static boolean nameChar(int symbol) {
        return symbol == '_' || (symbol >= 'a' && symbol <= 'z') || (symbol >= '0' && symbol <= '9');
    }

    float newPercent() {
        return newPercent / (float) PERCENT_SCALE;
    }

    void rest() {
        turntable.rest();
    }

    void focusName() {
        focusName = true;
        screen.relayout();
    }

    private int entryValue(java.util.function.ToIntFunction<LootEntry> read) {
        LootEntry entry = screen.entry();
        return entry == null ? 0 : read.applyAsInt(entry);
    }

    private void commitCount(boolean lower, int value) {
        LootEntry entry = screen.entry();
        if (entry == null) return;
        int min = lower ? value : Math.min(entry.min(), value);
        int max = lower ? Math.max(entry.max(), value) : value;
        screen.later("count", screen.base() + screen.entryIndex() + " count " + min + " " + max);
    }

    private void commitRules(int min, int max) {
        int low = max != LootTable.UNCAPPED ? Math.min(min, max) : min;
        screen.later("rules", screen.base() + "items " + low + " " + max);
    }

    private void openGuns(boolean entryOnly) {
        String table = screen.tableName();
        if (table == null) return;
        LootGunBench bench = new LootGunBench(table);
        GunsmithScreen.open(bench, entryOnly ? bench.positionOf(screen.entryIndex()) : 0, screen);
    }

    private void createTable() {
        screen.createTable(nameRow.value());
        nameRow.box().setValue("");
    }

    private void pressRemove() {
        if (screen.confirmed(DROP_ENTRY)) {
            screen.removeEntry();
            return;
        }
        tick();
    }

    private void pressDeleteTable() {
        if (screen.confirmed(DROP_TABLE)) screen.deleteTable();
    }

    void tick() {
        boolean armed = screen.isArmed(DROP_ENTRY);
        if (armed == shownArmed) return;
        shownArmed = armed;
        removeButton.setMessage(armed ? SURE : DELETE);
    }

    void build(StudioStack stack, int x, int width) {
        if (screen.adding()) {
            stack.add(sized(shelfHeading, width), x);
            stack.add(sized(newChanceRow, width), x);
            stack.skip(GAP);
        } else if (screen.entry() != null) {
            entry(stack, x, width);
        } else {
            table(stack, x, width);
        }
    }

    private void entry(StudioStack stack, int x, int width) {
        stack.add(sized(entryHeading, width), x);
        stack.add(sized(chanceRow, width), x);
        stack.add(sized(minRow, width), x);
        stack.add(sized(maxRow, width), x);
        int third = (width - GAP * 2) / 3;
        gunsButton.active = GunSmith.isGun(LootSnapshot.stack(screen.entry()));
        stack.add(sized(copyButton, third), x, GAP * 2);
        stack.beside(sized(gunsButton, third), x + third + GAP);
        stack.beside(sized(removeButton, width - third * 2 - GAP * 2), x + (third + GAP) * 2);
    }

    private void table(StudioStack stack, int x, int width) {
        LootSnapshot.Table current = screen.currentTable();
        stack.add(sized(rulesHeading, width), x);
        stack.add(sized(rulesMinRow, width), x);
        stack.add(sized(rulesMaxRow, width), x);
        tableGunsRow.active = current != null && !new LootGunBench(current.table().name()).guns().isEmpty();
        stack.add(sized(tableGunsRow, width), x);
        if (current != null && !current.airdrop()) stack.add(sized(airdropRow, width), x);
        stack.add(sized(createHeading, width), x, GAP);
        stack.add(sized(nameRow, width), x);
        stack.add(sized(createRow, width), x);
        if (current != null && !current.airdrop() && current.caches() == 0) stack.add(sized(deleteRow, width), x, GAP);
        if (focusName) takeNameFocus();
    }

    private void takeNameFocus() {
        focusName = false;
        screen.setFocused(nameRow);
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    int artHeight() {
        if (screen.adding()) return 0;
        return screen.entry() != null ? ENTRY_ART : TABLE_ART;
    }

    void renderArt(GuiGraphics graphics, float left, float top, float width, float appear) {
        LootEntry entry = screen.entry();
        if (entry != null) {
            renderEntryArt(graphics, entry, left, top, width, appear);
            return;
        }
        LootSnapshot.Table current = screen.currentTable();
        if (current == null) return;
        UiRender.textTrackedFit(graphics, font(), Component.literal(current.table().name()), left + width / 2.0f, top,
                14.0f, width, 0.95f, 0.0f, UiTheme.alpha(UiAccent.text(), appear), false);
        line(graphics, usage(current), left, width, top + 16.0f, appear);
        line(graphics, Component.translatable("studio.loot.art.entries", current.table().entries().size()),
                left, width, top + 27.0f, appear);
    }

    private static Component usage(LootSnapshot.Table entry) {
        if (entry.airdrop()) return Component.translatable("airdrop.editor.used.airdrop", entry.caches());
        return Component.translatable("airdrop.editor.used.caches", entry.caches());
    }

    private void renderEntryArt(GuiGraphics graphics, LootEntry entry, float left, float top, float width,
                                float appear) {
        ItemStack stack = LootSnapshot.stack(entry);
        turntable.render(graphics, stack, left, top, width, TURNTABLE_HEIGHT);
        float y = top + TURNTABLE_HEIGHT + 4.0f;
        Component name = stack.isEmpty() ? Component.translatable("airdrop.editor.unknown", entry.itemId().toString())
                : stack.getHoverName();
        UiRender.textTrackedFit(graphics, font(), name, left + width / 2.0f, y, 12.0f, width, 0.85f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        line(graphics, Component.translatable("airdrop.editor.real",
                LootChance.shown(LootOdds.appears(screen.table(), screen.entryIndex()) * 100.0f)), left, width,
                y + 15.0f, appear);
        line(graphics, Component.literal(entry.itemId().toString()), left, width, y + 26.0f, appear);
    }

    private static void line(GuiGraphics graphics, Component text, float left, float width, float y, float appear) {
        UiRender.textTrackedFit(graphics, font(), text, left + width / 2.0f, y, 10.0f, width, LINE_SCALE, 0.0f,
                UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    boolean pressArt(double mouseX, double mouseY) {
        if (screen.entry() == null || !turntable.over(mouseX, mouseY)) return false;
        turntable.beginDrag();
        return true;
    }

    boolean dragArt(double dragX, double dragY) {
        if (!turntable.dragging()) return false;
        turntable.drag(dragX, dragY);
        return true;
    }

    void releaseArt() {
        turntable.endDrag();
    }

    boolean scrollArt(double mouseX, double mouseY, double amount) {
        if (screen.entry() == null || !turntable.over(mouseX, mouseY)) return false;
        turntable.magnify(amount);
        return true;
    }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }
}
