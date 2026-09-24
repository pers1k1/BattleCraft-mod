package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.MenuData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// WHY: снимок разбирается один раз на приход, а не в кадре: разбор это сотни стаков с NBT, и
// WHY: редактор спрашивает таблицы каждый кадр ради сетки и шансов
public final class LootSnapshot {
    public record Table(LootTable table, boolean airdrop, int caches) {}

    private static final Map<LootEntry, ItemStack> stacks = new IdentityHashMap<>();
    private static CompoundTag parsedFrom;
    private static int parsedHash;
    private static List<Table> parsed = List.of();

    private LootSnapshot() {}

    public static List<Table> all() {
        CompoundTag state = MenuData.state(ModuleMenuStates.LOOT);
        if (state == parsedFrom) return parsed;

        int hash = state.hashCode();
        parsedFrom = state;
        if (hash == parsedHash && !parsed.isEmpty()) return parsed;

        parsedHash = hash;
        stacks.clear();
        parsed = parse(state);
        return parsed;
    }

    public static boolean arrived() {
        return !MenuData.state(ModuleMenuStates.LOOT).isEmpty();
    }

    private static List<Table> parse(CompoundTag state) {
        List<Table> tables = new ArrayList<>();
        ListTag list = state.getList("tables", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag stored = list.getCompound(index);
            tables.add(new Table(LootTable.fromTag(stored), stored.getBoolean("airdrop"), stored.getInt("caches")));
        }
        return tables;
    }

    // WHY: стак записи собирается разбором NBT, и в кадре это сотни разборов на сетку плиток
    public static ItemStack stack(LootEntry entry) {
        return stacks.computeIfAbsent(entry, LootEntry::template);
    }

    public static List<LootTable> tables() {
        List<LootTable> tables = new ArrayList<>();
        for (Table table : all()) {
            tables.add(table.table());
        }
        return tables;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Table table : all()) {
            names.add(table.table().name());
        }
        return names;
    }

    public static Table find(String name) {
        for (Table table : all()) {
            if (table.table().name().equals(name)) return table;
        }
        return null;
    }
}
