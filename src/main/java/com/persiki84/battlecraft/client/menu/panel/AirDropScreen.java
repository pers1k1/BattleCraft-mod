package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.airdrop.cache.CacheTier;
import com.persiki84.airdrop.cache.LootCache;
import com.persiki84.airdrop.cache.RefillMode;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.battlecraft.client.menu.panel.loot.LootEditorScreen;
import com.persiki84.battlecraft.client.menu.panel.loot.LootGunBench;
import com.persiki84.battlecraft.client.menu.panel.loot.LootSnapshot;
import com.persiki84.shared.client.menu.gunsmith.GunsmithScreen;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PanelScreen;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.gunsmith.GunSmith;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

import static com.persiki84.airdrop.config.AirDropLimits.*;

public class AirDropScreen extends PanelScreen {
    private static final String COMMAND = "airdrop";
    private static final String CONFIG = "airdrop config";
    private static final String TOGGLE = "airdrop toggle";
    private static final int GUNS_TAB = 3;

    private int pickedCache;
    private int shownLoot;
    private int cachesBeforeAdd = -1;

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
                new Page(Component.translatable("airdrop.menu.tab.caches"), this::cacheRows),
                new Page(Component.translatable("airdrop.menu.tab.attachments"), this::gunRows),
                new Page(Component.translatable("airdrop.menu.tab.actions"), this::actionRows));
    }

    // WHY: вкладка обвесов живёт на снимке редактора лута, а не аирдропа, и без своей сверки
    // WHY: установленный обвес не появлялся до переключения вкладок
    @Override
    public void tick() {
        super.tick();
        if (activeTab() != GUNS_TAB) return;

        MenuData.request(ModuleMenuStates.LOOT);
        int lootHash = MenuData.state(ModuleMenuStates.LOOT).hashCode();
        if (lootHash != shownLoot && !typingInRow()) {
            shownLoot = lootHash;
            rebuild();
        }
    }

    private CompoundTag state() {
        return MenuData.state(menuId());
    }

    private int value(String key) {
        return state().getInt(key);
    }

    private AbstractWidget flag(String label, String key, String command) {
        return toggle(label, () -> state().getBoolean(key), on -> send(TOGGLE + " " + command + " " + on));
    }

    private List<AbstractWidget> dropRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(heading(Component.translatable("airdrop.menu.group.state")));
        rows.add(flag("airdrop.menu.enabled", "modEnabled", "mod"));
        rows.add(flag("airdrop.menu.auto_spawn", "autoSpawnEnabled", "spawn"));
        rows.add(flag("airdrop.menu.match_only", "matchOnly", "match_only"));
        rows.add(flag("airdrop.menu.clear_on_end", "clearOnMatchEnd", "clear_on_end"));
        rows.add(flag("airdrop.menu.announce", "announceCoords", "announce"));
        rows.add(heading(Component.translatable("airdrop.menu.group.loot")));
        rows.add(tablePick());
        rows.add(action("airdrop.menu.open_editor", "airdrop.menu.action.open",
                () -> openEditor(state().getString("lootTable"))));
        addAreaRows(rows);
        return rows;
    }

    private void addAreaRows(List<AbstractWidget> rows) {
        rows.add(heading(Component.translatable("airdrop.menu.group.area")));
        rows.add(flag("airdrop.menu.world_center", "centerAtWorldSpawn", "world_center"));
        rows.add(action("airdrop.menu.center_here", "airdrop.menu.action.here", () -> send(CONFIG + " center here")));
        rows.add(number("airdrop.menu.center_x", () -> value("centerX"),
                set -> send(CONFIG + " center " + set + " " + value("centerZ")), -COORDINATE, COORDINATE, 1));
        rows.add(number("airdrop.menu.center_z", () -> value("centerZ"),
                set -> send(CONFIG + " center " + value("centerX") + " " + set), -COORDINATE, COORDINATE, 1));
        rows.add(number("airdrop.menu.radius", () -> value("spawnRadius"),
                set -> send(CONFIG + " radius " + set), RADIUS_MIN, RADIUS_MAX, 50));
        rows.add(number("airdrop.menu.height", () -> value("height"),
                set -> send(CONFIG + " height " + set), HEIGHT_MIN, HEIGHT_MAX, 10));
    }

    private PickRow tablePick() {
        List<String> names = tableNames();
        return pick("airdrop.menu.loot_table", literal(names),
                () -> Math.max(0, names.indexOf(state().getString("lootTable"))),
                picked -> send(CONFIG + " table " + names.get(picked)));
    }

    private List<AbstractWidget> timingRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(number("airdrop.menu.interval", () -> value("intervalSeconds"),
                set -> send(CONFIG + " interval " + set), INTERVAL_MIN, INTERVAL_MAX, 30));
        rows.add(number("airdrop.menu.chance", () -> value("chancePercent"),
                set -> send(CONFIG + " chance " + set), 0, PERCENT, 5));
        rows.add(number("airdrop.menu.flight", () -> value("flightSeconds"),
                set -> send(CONFIG + " flight_time " + set), FLIGHT_MIN, FLIGHT_MAX, 5));
        rows.add(number("airdrop.menu.open_delay", () -> value("openDelaySeconds"),
                set -> send(CONFIG + " open_delay " + set), 0, OPEN_DELAY_MAX, 1));
        rows.add(number("airdrop.menu.despawn_empty", () -> value("despawnEmptySeconds"),
                set -> send(CONFIG + " despawn_empty " + set), DESPAWN_MIN, DESPAWN_MAX, 30));
        rows.add(number("airdrop.menu.despawn_filled", () -> value("despawnFilledSeconds"),
                set -> send(CONFIG + " despawn_filled " + set), DESPAWN_MIN, DESPAWN_MAX, 30));
        rows.add(number("airdrop.menu.warn", () -> value("warnSeconds"),
                set -> send(CONFIG + " warn_time " + set), 0, WARN_MAX, 10));
        return rows;
    }

    private List<AbstractWidget> cacheRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(heading(Component.translatable("airdrop.menu.group.caches")));
        rows.add(flag("airdrop.menu.cache_clear", "cacheClear", "cache_clear"));
        rows.add(flag("airdrop.menu.cache_lock", "cacheLock", "cache_lock"));
        rows.add(action("airdrop.menu.cache_add", "airdrop.menu.action.add", this::addLookedAt));
        rows.add(action("airdrop.menu.cache_fill_all", "airdrop.menu.action.fill", () -> send(COMMAND + " cache fill_all")));

        List<CompoundTag> caches = caches();
        if (caches.isEmpty()) {
            rows.add(reading("airdrop.menu.cache_none", Component::empty));
            return rows;
        }
        pickedCache = followCache(caches.size());
        addCacheRows(rows, caches, caches.get(pickedCache));
        return rows;
    }

    // WHY: после добавления выбор встаёт на новый тайник, после снятия - на соседа: возврат к
    // WHY: первому заставлял бы искать только что добавленное по всему списку
    private int followCache(int count) {
        if (cachesBeforeAdd >= 0 && count > cachesBeforeAdd) {
            cachesBeforeAdd = -1;
            return count - 1;
        }
        return Math.max(0, Math.min(pickedCache, count - 1));
    }

    private void addCacheRows(List<AbstractWidget> rows, List<CompoundTag> caches, CompoundTag cache) {
        String prefix = COMMAND + " cache " + cache.getInt("id") + " ";
        rows.add(heading(Component.translatable("airdrop.menu.group.cache")));
        rows.add(pick("airdrop.menu.cache", cacheLabels(caches), () -> pickedCache, picked -> {
            pickedCache = picked;
            rebuild();
        }));
        rows.add(cacheTablePick(cache, prefix));
        rows.add(action("airdrop.menu.cache_edit_table", "airdrop.menu.action.open",
                () -> openEditor(cache.getString("table"))));
        rows.add(enumPick("airdrop.menu.cache_tier", tierLabels(), CacheTier.of(cache.getString("tier")).ordinal(),
                picked -> send(prefix + "tier " + CacheTier.values()[picked].id())));
        RefillMode mode = RefillMode.of(cache.getString("refill"));
        rows.add(enumPick("airdrop.menu.cache_refill", refillLabels(), mode.ordinal(),
                picked -> send(prefix + "refill " + RefillMode.values()[picked].id())));
        if (mode.timed()) {
            rows.add(number("airdrop.menu.cache_refill_seconds", () -> cache.getInt("refillSeconds"),
                    set -> send(prefix + "refill " + mode.id() + " " + set), LootCache.REFILL_MIN, LootCache.REFILL_MAX, 30));
        }
        addCacheActions(rows, cache, prefix);
    }

    private void addCacheActions(List<AbstractWidget> rows, CompoundTag cache, String prefix) {
        if (cache.getBoolean("missing")) rows.add(reading("airdrop.menu.cache_missing", Component::empty));
        rows.add(action("airdrop.menu.cache_fill", "airdrop.menu.action.fill", () -> send(prefix + "fill")));
        rows.add(action("airdrop.menu.cache_empty", "airdrop.menu.action.empty", () -> send(prefix + "empty")));
        rows.add(action("airdrop.menu.cache_tp", "airdrop.menu.action.tp", () -> send(prefix + "tp")));
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("airdrop.menu.cache_remove"),
                () -> Component.translatable("airdrop.menu.action.remove"),
                () -> send(prefix + "remove")).alerting());
    }

    private PickRow cacheTablePick(CompoundTag cache, String prefix) {
        List<String> names = tableNames();
        return pick("airdrop.menu.cache_table", literal(names),
                () -> Math.max(0, names.indexOf(cache.getString("table"))),
                picked -> send(prefix + "table " + names.get(picked)));
    }

    private PickRow enumPick(String label, List<Component> labels, int current, java.util.function.IntConsumer apply) {
        return pick(label, labels, () -> current, apply);
    }

    // WHY: контейнер выбирается прицелом, а не координатами: сервер проверяет место заново,
    // WHY: клиент только называет блок, на который смотрит игрок
    private void addLookedAt() {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            MenuFeedback.show(Component.translatable("airdrop.menu.cache_aim"), true);
            return;
        }
        BlockPos pos = block.getBlockPos();
        cachesBeforeAdd = caches().size();
        send(COMMAND + " cache add " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private List<CompoundTag> caches() {
        ListTag stored = state().getList("caches", Tag.TAG_COMPOUND);
        List<CompoundTag> caches = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            caches.add(stored.getCompound(index));
        }
        return caches;
    }

    private static List<Component> cacheLabels(List<CompoundTag> caches) {
        List<Component> labels = new ArrayList<>();
        for (CompoundTag cache : caches) {
            BlockPos pos = BlockPos.of(cache.getLong("pos"));
            labels.add(Component.translatable("airdrop.menu.cache_label", cache.getInt("id"),
                    pos.getX(), pos.getY(), pos.getZ(), cache.getString("table")));
        }
        return labels;
    }

    private static List<Component> tierLabels() {
        List<Component> labels = new ArrayList<>();
        for (CacheTier tier : CacheTier.values()) {
            labels.add(Component.translatable(tier.label()));
        }
        return labels;
    }

    private static List<Component> refillLabels() {
        List<Component> labels = new ArrayList<>();
        for (RefillMode mode : RefillMode.values()) {
            labels.add(Component.translatable(mode.label()));
        }
        return labels;
    }

    private List<String> tableNames() {
        ListTag stored = state().getList("tables", Tag.TAG_STRING);
        List<String> names = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            names.add(stored.getString(index));
        }
        return names;
    }

    private static List<Component> literal(List<String> names) {
        List<Component> labels = new ArrayList<>();
        for (String name : names) {
            labels.add(Component.literal(name));
        }
        return labels;
    }

    private void openEditor(String table) {
        Minecraft.getInstance().setScreen(new LootEditorScreen(table, this));
    }

    // WHY: обвесы правятся в мастерской, где видно оружие и сами обвесы; вкладка ведёт в неё
    // WHY: на нужном стволе нужной таблицы
    private List<AbstractWidget> gunRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (LootTable table : LootSnapshot.tables()) {
            addTableGuns(rows, table);
        }
        if (rows.isEmpty()) rows.add(reading("airdrop.menu.no_guns", Component::empty));
        return rows;
    }

    private void addTableGuns(List<AbstractWidget> rows, LootTable table) {
        LootGunBench bench = new LootGunBench(table.name());
        boolean headed = false;
        for (int index = 0; index < table.entries().size(); index++) {
            ItemStack stack = LootSnapshot.stack(table.entries().get(index));
            if (!GunSmith.isGun(stack)) continue;
            if (!headed) {
                rows.add(heading(Component.literal(table.name())));
                headed = true;
            }
            int entry = index;
            rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, stack.getHoverName(),
                    () -> Component.translatable("gunsmith.open"),
                    () -> GunsmithScreen.open(bench, bench.positionOf(entry), this)));
        }
    }

    private List<AbstractWidget> actionRows() {
        return List.of(
                action("airdrop.menu.spawn_now", "airdrop.menu.action.drop", () -> send(COMMAND + " now")),
                action("airdrop.menu.spawn_here", "airdrop.menu.action.here", () -> send(COMMAND + " spawn")),
                action("airdrop.menu.reload", "airdrop.menu.action.reload", () -> send(COMMAND + " reload")),
                new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                        Component.translatable("airdrop.menu.kill_all"),
                        () -> Component.translatable("airdrop.menu.action.remove"),
                        () -> send(COMMAND + " kill_all")).alerting());
    }
}
