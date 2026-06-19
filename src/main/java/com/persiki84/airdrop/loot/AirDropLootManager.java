package com.persiki84.airdrop.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
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
import java.util.*;

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
        File folder = configDir.resolve("airdrop_loot").toFile();
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File f : files) {
            try (FileReader reader = new FileReader(f)) {
                JsonElement json = JsonParser.parseReader(reader);
                if (json.isJsonArray()) {
                    List<LootEntry> list = new ArrayList<>();
                    for (JsonElement e : json.getAsJsonArray()) {
                        JsonObject obj = e.getAsJsonObject();
                        ResourceLocation id = new ResourceLocation(obj.get("item").getAsString());
                        int min = obj.get("min").getAsInt();
                        int max = obj.get("max").getAsInt();
                        float chance = obj.get("chance").getAsFloat();

                        String nbt = obj.has("nbt") ? obj.get("nbt").getAsString() : null;

                        list.add(new LootEntry(id, min, max, chance, nbt));
                    }
                    String name = f.getName().replace(".json", "");
                    TABLES.put(new ResourceLocation("airdrop", name), list);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static List<LootEntry> getTable(ResourceLocation id) {
        return TABLES.getOrDefault(id, Collections.emptyList());
    }

    public static void setTable(ResourceLocation id, List<LootEntry> list) {
        TABLES.put(id, list);
    }

    public static void saveTable(ResourceLocation id, Path configDir) {
        File folder = configDir.resolve("airdrop_loot").toFile();
        if (!folder.exists()) folder.mkdirs();

        File file = new File(folder, id.getPath() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            JsonArray arr = new JsonArray();
            for (LootEntry e : getTable(id)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("item", e.itemId().toString());
                obj.addProperty("min", e.min());
                obj.addProperty("max", e.max());
                obj.addProperty("chance", e.chance());
                if (e.nbt() != null && !e.nbt().isEmpty()) {
                    obj.addProperty("nbt", e.nbt());
                }
                arr.add(obj);
            }
            GSON.toJson(arr, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void fillInventory(RandomSource rand, Container container, ResourceLocation tableId) {
        List<LootEntry> pool = getTable(tableId);
        if (pool == null || pool.isEmpty()) return;

        container.clearContent();

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) slots.add(i);
        java.util.Collections.shuffle(slots);

        int slotIndex = 0;

        for (LootEntry e : pool) {
            if (rand.nextFloat() <= e.chance()) {
                int count = e.min();
                if (e.max() > e.min()) {
                    count += rand.nextInt(e.max() - e.min() + 1);
                }

                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(e.itemId()), count);

                if (e.nbt() != null && !e.nbt().isEmpty()) {
                    try {
                        CompoundTag tag = TagParser.parseTag(e.nbt());
                        stack.setTag(tag);
                    } catch (Exception ex) {
                        System.err.println("AirDrop: Failed to parse NBT for item " + e.itemId());
                    }
                }

                if (slotIndex < slots.size()) {
                    container.setItem(slots.get(slotIndex++), stack);
                }
            }
        }
    }
}
