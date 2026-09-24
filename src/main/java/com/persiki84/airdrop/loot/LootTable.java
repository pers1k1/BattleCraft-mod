package com.persiki84.airdrop.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.persiki84.shared.JsonRead;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record LootTable(String name, int minItems, int maxItems, List<LootEntry> entries) {
    public static final int ITEMS_LIMIT = 54;
    public static final int UNCAPPED = 0;

    public LootTable {
        entries = List.copyOf(entries);
        maxItems = Math.max(UNCAPPED, Math.min(ITEMS_LIMIT, maxItems));
        minItems = Math.max(0, Math.min(ITEMS_LIMIT, minItems));
        if (maxItems != UNCAPPED && minItems > maxItems) minItems = maxItems;
    }

    public static LootTable empty(String name) {
        return new LootTable(name, 0, UNCAPPED, List.of());
    }

    public LootTable withEntries(List<LootEntry> changed) {
        return new LootTable(name, minItems, maxItems, changed);
    }

    public LootTable withRules(int newMin, int newMax) {
        return new LootTable(name, newMin, newMax, entries);
    }

    public LootTable renamed(String newName) {
        return new LootTable(newName, minItems, maxItems, entries);
    }

    public List<LootEntry> editable() {
        return new ArrayList<>(entries);
    }

    public boolean contains(int index) {
        return index >= 0 && index < entries.size();
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("minItems", minItems);
        object.addProperty("maxItems", maxItems);
        JsonArray list = new JsonArray();
        for (LootEntry entry : entries) {
            list.add(entry.toJson());
        }
        object.add("entries", list);
        return object;
    }

    // WHY: до 23.09.2026 файл таблицы был голым массивом записей, и такие файлы лежат на серверах:
    // WHY: они читаются как таблица без правил, а битая запись теряет только себя
    public static LootTable fromJson(String name, JsonElement json) {
        if (json.isJsonArray()) return new LootTable(name, 0, UNCAPPED, entriesOf(json.getAsJsonArray()));
        if (!json.isJsonObject()) return empty(name);

        JsonObject object = json.getAsJsonObject();
        Float min = JsonRead.number(object, "minItems");
        Float max = JsonRead.number(object, "maxItems");
        JsonElement list = object.get("entries");
        List<LootEntry> entries = list != null && list.isJsonArray() ? entriesOf(list.getAsJsonArray()) : List.of();
        return new LootTable(name, min == null ? 0 : min.intValue(), max == null ? UNCAPPED : max.intValue(), entries);
    }

    private static List<LootEntry> entriesOf(JsonArray array) {
        List<LootEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;

            LootEntry entry = LootEntry.fromJson(element.getAsJsonObject());
            if (entry != null) entries.add(entry);
        }
        return entries;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putInt("minItems", minItems);
        tag.putInt("maxItems", maxItems);
        ListTag list = new ListTag();
        for (LootEntry entry : entries) {
            list.add(entry.toTag());
        }
        tag.put("entries", list);
        return tag;
    }

    public static LootTable fromTag(CompoundTag tag) {
        List<LootEntry> entries = new ArrayList<>();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            LootEntry entry = LootEntry.fromTag(list.getCompound(index));
            if (entry != null) entries.add(entry);
        }
        return new LootTable(tag.getString("name"), tag.getInt("minItems"), tag.getInt("maxItems"), entries);
    }
}
