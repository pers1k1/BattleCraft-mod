package com.persiki84.airdrop.loot;

import com.persiki84.airdrop.AirDropMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AirDropLootManager {
    private static final Map<ResourceLocation, List<LootEntry>> TABLES = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record LootEntry(ResourceLocation itemId, int min, int max, float chance, String nbt) {
        public LootEntry(ResourceLocation itemId, int min, int max, float chance) {
            this(itemId, min, max, chance, null);
        }
    }

    public static void reload(Path configDir) {
        TABLES.clear();
        File folder = folder(configDir);

        File[] files = folder.listFiles((directory, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            List<LootEntry> parsed = readTable(file);
            if (parsed != null) TABLES.put(new ResourceLocation("airdrop", file.getName().replace(".json", "")), parsed);
        }
    }

    private static File folder(Path configDir) {
        File folder = configDir.resolve("airdrop_loot").toFile();
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    private static List<LootEntry> readTable(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonArray()) return null;

            List<LootEntry> list = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray()) {
                list.add(readEntry(element.getAsJsonObject()));
            }
            return list;
        } catch (Exception error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot read loot table {}: {}", file.getName(), error.toString());
            return null;
        }
    }

    private static LootEntry readEntry(JsonObject object) {
        return new LootEntry(new ResourceLocation(object.get("item").getAsString()),
                object.get("min").getAsInt(), object.get("max").getAsInt(),
                object.get("chance").getAsFloat(),
                object.has("nbt") ? object.get("nbt").getAsString() : null);
    }

    public static List<LootEntry> getTable(ResourceLocation id) {
        return TABLES.getOrDefault(id, Collections.emptyList());
    }

    public static void setTable(ResourceLocation id, List<LootEntry> list) {
        TABLES.put(id, list);
    }

    public static void saveTable(ResourceLocation id, Path configDir) {
        File file = new File(folder(configDir), id.getPath() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            JsonArray array = new JsonArray();
            for (LootEntry entry : getTable(id)) {
                array.add(writeEntry(entry));
            }
            GSON.toJson(array, writer);
        } catch (Exception error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot write loot table {}: {}", file.getName(), error.toString());
        }
    }

    private static JsonObject writeEntry(LootEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("item", entry.itemId().toString());
        object.addProperty("min", entry.min());
        object.addProperty("max", entry.max());
        object.addProperty("chance", entry.chance());
        if (entry.nbt() != null && !entry.nbt().isEmpty()) object.addProperty("nbt", entry.nbt());
        return object;
    }

    public static ItemStack stackOf(LootEntry entry, int count) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(entry.itemId()), count);
        if (entry.nbt() == null || entry.nbt().isEmpty()) return stack;

        try {
            stack.setTag(TagParser.parseTag(entry.nbt()));
        } catch (Exception error) {
            AirDropMod.LOGGER.warn("[airdrop] bad nbt for {}: {}", entry.itemId(), error.toString());
        }
        return stack;
    }

    public static LootEntry describing(LootEntry entry, ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String nbt = stack.hasTag() ? stack.getTag().toString() : null;
        return new LootEntry(id, entry.min(), entry.max(), entry.chance(), nbt);
    }

    public static void fillInventory(RandomSource rand, Container container, ResourceLocation tableId) {
        List<LootEntry> pool = getTable(tableId);
        if (pool == null || pool.isEmpty()) return;

        container.clearContent();
        List<Integer> slots = shuffledSlots(container, rand);

        int slotIndex = 0;
        for (LootEntry entry : pool) {
            if (rand.nextFloat() > entry.chance() || slotIndex >= slots.size()) continue;
            container.setItem(slots.get(slotIndex++), stackOf(entry, rolledCount(entry, rand)));
        }
    }

    private static List<Integer> shuffledSlots(Container container, RandomSource rand) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            slots.add(slot);
        }
        for (int index = slots.size() - 1; index > 0; index--) {
            Collections.swap(slots, index, rand.nextInt(index + 1));
        }
        return slots;
    }

    private static int rolledCount(LootEntry entry, RandomSource rand) {
        if (entry.max() <= entry.min()) return entry.min();
        return entry.min() + rand.nextInt(entry.max() - entry.min() + 1);
    }
}
