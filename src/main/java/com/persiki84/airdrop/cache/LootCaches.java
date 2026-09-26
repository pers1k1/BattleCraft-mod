package com.persiki84.airdrop.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.airdrop.AirDropFiles;
import com.persiki84.airdrop.AirDropMod;
import com.persiki84.shared.JsonRead;
import com.persiki84.shared.WorldFiles;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class LootCaches {
    public static final int LIMIT = 512;

    private static final String FOLDER = "battlecraft";
    private static final String FILE = "loot_caches.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final TreeMap<Integer, LootCache> byId = new TreeMap<>();
    // WHY: позицию спрашивают воронки каждый тик, поэтому ключ измерения это интернированный
    // WHY: ResourceKey, а ключ позиции - голый long: ни строки измерения, ни упаковки Long на запрос
    private static final Map<ResourceKey<Level>, Long2ObjectMap<LootCache>> byPlace = new HashMap<>();
    private static Path file;
    private static int matchSerial = 1;
    private static int nextId = 1;
    private static boolean dirty;
    private static boolean unreadable;

    private LootCaches() {}

    public static void load(MinecraftServer server) {
        clear();
        file = WorldFiles.pathFor(server.overworld(), FOLDER, FILE);
        if (file == null || !Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (json.isJsonObject()) read(json.getAsJsonObject());
        } catch (Exception error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot read loot caches: {}", error.toString());
            unreadable = true;
        }
        if (unreadable) quarantine();
    }

    // WHY: после битого чтения список пуст, и первое же сохранение записало бы пустоту поверх
    // WHY: всех тайников: файл откладывается, а запись ждёт настоящей правки - нового тайника
    private static void quarantine() {
        Path broken = file;
        clear();
        file = broken;
        unreadable = true;
        AirDropFiles.setAside(broken);
    }

    private static void read(JsonObject root) {
        Float serial = JsonRead.number(root, "matchSerial");
        matchSerial = serial == null ? 1 : Math.max(1, serial.intValue());
        JsonElement list = root.get("caches");
        if (list == null || !list.isJsonArray()) return;

        for (JsonElement element : list.getAsJsonArray()) {
            LootCache cache = element.isJsonObject() ? LootCache.fromJson(element.getAsJsonObject()) : null;
            if (cache != null) index(cache);
        }
        nextId = byId.isEmpty() ? 1 : byId.lastKey() + 1;
    }

    public static void clear() {
        byId.clear();
        byPlace.clear();
        file = null;
        matchSerial = 1;
        nextId = 1;
        dirty = false;
        unreadable = false;
    }

    private static void index(LootCache cache) {
        byId.put(cache.id(), cache);
        ResourceKey<Level> dimension = dimensionKey(cache.dimension());
        if (dimension == null) return;

        byPlace.computeIfAbsent(dimension, key -> new Long2ObjectOpenHashMap<>()).put(cache.pos().asLong(), cache);
    }

    public static LootCache add(Level level, BlockPos pos) {
        LootCache cache = new LootCache(nextId++, dimensionOf(level), pos);
        index(cache);
        unreadable = false;
        markDirty();
        return cache;
    }

    public static LootCache remove(int id) {
        LootCache cache = byId.remove(id);
        if (cache == null) return null;

        Long2ObjectMap<LootCache> places = byPlace.get(dimensionKey(cache.dimension()));
        if (places != null) places.remove(cache.pos().asLong());
        markDirty();
        return cache;
    }

    public static LootCache get(int id) {
        return byId.get(id);
    }

    public static List<LootCache> all() {
        return new ArrayList<>(byId.values());
    }

    public static Iterable<LootCache> view() {
        return byId.values();
    }

    public static int size() {
        return byId.size();
    }

    public static LootCache at(Level level, BlockPos pos) {
        Long2ObjectMap<LootCache> places = byPlace.get(level.dimension());
        return places == null ? null : places.get(pos.asLong());
    }

    public static boolean anyIn(Level level) {
        Long2ObjectMap<LootCache> places = byPlace.get(level.dimension());
        return places != null && !places.isEmpty();
    }

    public static boolean holds(Level level, long packedPos) {
        Long2ObjectMap<LootCache> places = byPlace.get(level.dimension());
        return places != null && places.containsKey(packedPos);
    }

    public static int usingTable(String table) {
        int count = 0;
        for (LootCache cache : byId.values()) {
            if (cache.table().equals(table)) count++;
        }
        return count;
    }

    public static int serial() {
        return matchSerial;
    }

    public static void nextMatch() {
        matchSerial++;
        markDirty();
    }

    public static void markDirty() {
        dirty = true;
    }

    public static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }

    public static ResourceKey<Level> dimensionKey(String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    public static void saveIfDirty() {
        if (dirty) save();
    }

    public static void save() {
        if (file == null || unreadable) return;

        dirty = false;
        Path temporary = file.resolveSibling(FILE + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(document(), writer);
            }
            WorldFiles.moveIntoPlace(temporary, file);
        } catch (IOException error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot write loot caches: {}", error.toString());
        }
    }

    private static JsonObject document() {
        JsonObject root = new JsonObject();
        root.addProperty("matchSerial", matchSerial);
        JsonArray list = new JsonArray();
        for (LootCache cache : byId.values()) {
            list.add(cache.toJson());
        }
        root.add("caches", list);
        return root;
    }
}
