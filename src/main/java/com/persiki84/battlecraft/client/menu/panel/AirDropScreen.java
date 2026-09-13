package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PanelScreen;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.gunsmith.GunStat;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AirDropScreen extends PanelScreen {
    private static final String COMMAND = "airdrop";
    private static final String CONFIG = "airdrop config";
    private static final int MAX_RADIUS = 30000;
    private static final int MAX_INTERVAL = 86400;
    private static final int MAX_FLIGHT = 300;
    private static final int MAX_WARN = 3600;
    private static final int MAX_STACK = 64;
    private static final int PERCENT = 100;
    private static final int MAX_COORDINATE = 30000000;
    private static final int DEFAULT_CHANCE = 50;

    private int newMin = 1;
    private int newMax = 1;
    private int newChance = DEFAULT_CHANCE;
    private int pickedGun;
    private int pickedEntry;

    public AirDropScreen() {
        super(Component.translatable("airdrop.menu.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.AIRDROP;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("airdrop.menu.tab.drop"), this::dropRows),
                new Page(Component.translatable("airdrop.menu.tab.timing"), this::timingRows),
                new Page(Component.translatable("airdrop.menu.tab.loot"), this::lootRows),
                new Page(Component.translatable("airdrop.menu.tab.entry"), this::entryRows),
                new Page(Component.translatable("airdrop.menu.tab.attachments"), this::attachRows),
                new Page(Component.translatable("airdrop.menu.tab.actions"), this::actionRows));
    }

    private int value(String key) {
        return MenuData.state(menuId()).getInt(key);
    }

    private List<AbstractWidget> dropRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(toggle("airdrop.menu.enabled", () -> MenuData.state(menuId()).getBoolean("modEnabled"),
                on -> send(COMMAND + " toggle mod " + on)));
        rows.add(toggle("airdrop.menu.auto_spawn", () -> MenuData.state(menuId()).getBoolean("autoSpawnEnabled"),
                on -> send(COMMAND + " toggle spawn " + on)));
        rows.add(number("airdrop.menu.radius", () -> value("spawnRadius"),
                set -> send(CONFIG + " radius " + set), 10, MAX_RADIUS, 50));
        rows.add(number("airdrop.menu.chance", () -> value("chancePercent"),
                set -> send(CONFIG + " chance " + set), 0, PERCENT, 5));
        rows.add(reading("airdrop.menu.center",
                () -> Component.literal(value("centerX") + " / " + value("centerZ"))));
        rows.add(action("airdrop.menu.center_here", "airdrop.menu.action.here",
                () -> send(CONFIG + " center here")));
        rows.add(number("airdrop.menu.center_x", () -> value("centerX"),
                set -> send(CONFIG + " center " + set + " " + value("centerZ")),
                -MAX_COORDINATE, MAX_COORDINATE, 1));
        rows.add(number("airdrop.menu.center_z", () -> value("centerZ"),
                set -> send(CONFIG + " center " + value("centerX") + " " + set),
                -MAX_COORDINATE, MAX_COORDINATE, 1));
        return rows;
    }

    private List<AbstractWidget> timingRows() {
        return List.of(
                number("airdrop.menu.interval", () -> value("intervalSeconds"),
                        set -> send(CONFIG + " interval " + set), 5, MAX_INTERVAL, 30),
                number("airdrop.menu.flight", () -> value("flightSeconds"),
                        set -> send(CONFIG + " flight_time " + set), 0, MAX_FLIGHT, 5),
                number("airdrop.menu.open_delay", () -> value("openDelaySeconds"),
                        set -> send(CONFIG + " open_delay " + set), 0, MAX_FLIGHT, 1),
                number("airdrop.menu.despawn_empty", () -> value("despawnEmptySeconds"),
                        set -> send(CONFIG + " despawn_empty " + set), 0, MAX_INTERVAL, 30),
                number("airdrop.menu.despawn_filled", () -> value("despawnFilledSeconds"),
                        set -> send(CONFIG + " despawn_filled " + set), 0, MAX_INTERVAL, 30),
                number("airdrop.menu.warn", () -> value("warnSeconds"),
                        set -> send(CONFIG + " warn_time " + set), 0, MAX_WARN, 10));
    }

    private List<AbstractWidget> lootRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(number("airdrop.menu.new_min", () -> newMin, value -> newMin = value, 1, MAX_STACK, 1));
        rows.add(number("airdrop.menu.new_max", () -> newMax, value -> newMax = value, 1, MAX_STACK, 1));
        rows.add(number("airdrop.menu.new_chance", () -> newChance, value -> newChance = value, 1, PERCENT, 5));
        rows.add(action("airdrop.menu.add_hand", "airdrop.menu.action.add", this::addFromHand));

        List<CompoundTag> entries = lootEntries();
        for (CompoundTag entry : entries) {
            rows.add(lootRow(entry));
        }
        if (!entries.isEmpty()) rows.add(clearListRow());
        return rows;
    }

    private ActionRow clearListRow() {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("airdrop.menu.loot_clear"),
                () -> Component.translatable("airdrop.menu.action.clear_all"),
                () -> send(COMMAND + " loot clear")).alerting();
    }

    private List<AbstractWidget> entryRows() {
        List<CompoundTag> entries = lootEntries();
        if (entries.isEmpty()) return List.of(reading("airdrop.menu.loot_empty", Component::empty));

        pickedEntry = Math.floorMod(pickedEntry, entries.size());
        CompoundTag entry = entries.get(pickedEntry);
        int index = entry.getInt("index");

        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(pick("airdrop.menu.entry", labels(entries), () -> pickedEntry, this::selectEntry)
                .icon(() -> stackOf(entry)));
        addCountRows(rows, entry, index);
        addOrderRows(rows, entries.size(), index);
        rows.add(action("airdrop.menu.entry_duplicate", "airdrop.menu.action.duplicate",
                () -> send(COMMAND + " loot duplicate " + index)));
        rows.add(action("airdrop.menu.entry_clear_nbt", "airdrop.menu.action.clear_nbt",
                () -> send(COMMAND + " loot edit " + index + " clear_nbt")));
        rows.add(removeEntryRow(index));
        return rows;
    }

    private ActionRow removeEntryRow(int index) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("airdrop.menu.entry_remove"),
                () -> Component.translatable("airdrop.menu.action.remove"),
                () -> send(COMMAND + " loot remove " + index)).alerting();
    }

    private void addCountRows(List<AbstractWidget> rows, CompoundTag entry, int index) {
        rows.add(number("airdrop.menu.entry_min", () -> entry.getInt("min"),
                value -> editCount(index, value, Math.max(value, entry.getInt("max"))), 1, MAX_STACK, 1));
        rows.add(number("airdrop.menu.entry_max", () -> entry.getInt("max"),
                value -> editCount(index, Math.min(value, entry.getInt("min")), value), 1, MAX_STACK, 1));
        rows.add(number("airdrop.menu.entry_chance", () -> entry.getInt("chance"),
                value -> send(COMMAND + " loot edit " + index + " chance " + value), 1, PERCENT, 5));
    }

    private void editCount(int index, int min, int max) {
        send(COMMAND + " loot edit " + index + " count " + min + " " + max);
    }

    private void addOrderRows(List<AbstractWidget> rows, int count, int index) {
        ActionRow up = action("airdrop.menu.entry_up", "airdrop.menu.action.up",
                () -> swapEntries(index, index - 1));
        up.active = index > 0;
        ActionRow down = action("airdrop.menu.entry_down", "airdrop.menu.action.down",
                () -> swapEntries(index, index + 1));
        down.active = index < count - 1;
        rows.add(up);
        rows.add(down);
    }

    private void swapEntries(int from, int to) {
        pickedEntry = to;
        send(COMMAND + " loot swap " + from + " " + to);
    }

    private void selectEntry(int picked) {
        pickedEntry = picked;
        rebuild();
    }

    private void addFromHand() {
        if (newMax < newMin) {
            MenuFeedback.show(Component.translatable("airdrop.loot.error_max_min"), true);
            return;
        }
        send(COMMAND + " loot hand " + newMin + " " + newMax + " " + newChance);
    }

    private List<AbstractWidget> attachRows() {
        List<CompoundTag> guns = armedEntries();
        if (guns.isEmpty()) return List.of(reading("airdrop.menu.no_guns", Component::empty));

        pickedGun = Math.floorMod(pickedGun, guns.size());
        CompoundTag entry = guns.get(pickedGun);

        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(pick("airdrop.menu.gun", labels(guns), () -> pickedGun, this::selectGun)
                .icon(() -> stackOf(entry)));
        addSlotRows(rows, entry);
        addStatRows(rows, entry);
        return rows;
    }

    private void addStatRows(List<AbstractWidget> rows, CompoundTag entry) {
        for (GunStat stat : GunSmith.stats(stackOf(entry))) {
            ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable(stat.label()), () -> Component.literal(stat.value()), () -> {});
            row.active = false;
            rows.add(row);
        }
    }

    private void selectGun(int picked) {
        pickedGun = picked;
        rebuild();
    }

    private void addSlotRows(List<AbstractWidget> rows, CompoundTag entry) {
        List<GunSlot> slots = GunSmith.slots(stackOf(entry));
        if (slots.isEmpty()) {
            rows.add(reading("airdrop.menu.no_attachments", Component::empty));
            return;
        }

        int index = entry.getInt("index");
        for (GunSlot slot : slots) {
            rows.add(slotRow(index, slot));
            if (!slot.installed().isEmpty()) rows.add(notesRow(index, slot));
        }
    }

    private PickRow slotRow(int index, GunSlot slot) {
        List<String> values = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        values.add("");
        labels.add(Component.translatable("airdrop.menu.installed_none"));

        for (GunSlot.GunOption option : slot.options()) {
            values.add(option.value());
            labels.add(Component.literal(option.label()));
        }

        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(slot.label()),
                labels, () -> Math.max(0, values.indexOf(installedIn(index, slot.id()))),
                picked -> applySlot(index, slot, values.get(picked)))
                .icon(() -> GunSmith.preview(installedIn(index, slot.id())));
    }

    private AbstractWidget notesRow(int index, GunSlot slot) {
        return reading("airdrop.menu.effect", () -> GunSmith.notesLabel(installedIn(index, slot.id())));
    }

    private String installedIn(int index, String slotId) {
        for (CompoundTag entry : lootEntries()) {
            if (entry.getInt("index") != index) continue;

            for (GunSlot slot : GunSmith.slots(stackOf(entry))) {
                if (slot.id().equals(slotId)) return slot.installed();
            }
        }
        return "";
    }

    private void applySlot(int index, GunSlot slot, String value) {
        if (value.isEmpty()) {
            send(COMMAND + " loot detach " + index + " " + slot.id());
            return;
        }
        send(COMMAND + " loot attach " + index + " \"" + value + "\"");
    }

    private List<CompoundTag> armedEntries() {
        List<CompoundTag> guns = new ArrayList<>();
        for (CompoundTag entry : lootEntries()) {
            if (entry.getBoolean("gun")) guns.add(entry);
        }
        return guns;
    }

    private static ItemStack stackOf(CompoundTag entry) {
        return ItemStack.of(entry.getCompound("stack"));
    }

    private static List<Component> labels(List<CompoundTag> entries) {
        List<Component> labels = new ArrayList<>();
        for (CompoundTag entry : entries) {
            labels.add(stackOf(entry).getHoverName());
        }
        return labels;
    }

    private List<CompoundTag> lootEntries() {
        ListTag stored = MenuData.state(menuId()).getList("loot", Tag.TAG_COMPOUND);
        List<CompoundTag> entries = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            entries.add(stored.getCompound(index));
        }
        return entries;
    }

    private AbstractWidget lootRow(CompoundTag entry) {
        int index = entry.getInt("index");
        String label = stackOf(entry).getHoverName().getString() + "  " + entry.getInt("min")
                + "-" + entry.getInt("max") + "  " + entry.getInt("chance") + "%";

        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.literal(label),
                () -> Component.translatable("airdrop.menu.action.remove"),
                () -> send(COMMAND + " loot remove " + index));
        return row.alerting();
    }

    private List<AbstractWidget> actionRows() {
        return List.of(
                action("airdrop.menu.spawn_now", "airdrop.menu.action.drop", () -> send(COMMAND + " now")),
                action("airdrop.menu.spawn_here", "airdrop.menu.action.here",
                        () -> send(COMMAND + " spawn ~ ~ ~")),
                action("airdrop.menu.reload", "airdrop.menu.action.reload", () -> send(COMMAND + " reload")),
                new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                        Component.translatable("airdrop.menu.kill_all"),
                        () -> Component.translatable("airdrop.menu.action.remove"),
                        () -> send(COMMAND + " kill_all")).alerting());
    }
}
