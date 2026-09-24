package com.persiki84.airdrop.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.airdrop.AirDropMod;
import com.persiki84.shared.JsonRead;
import com.persiki84.shared.WorldFiles;
import net.minecraft.core.BlockPos;
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
    private static final Map<String, Map<Long, LootCache>> byPlace = new HashMap<>();
    private static Path file;
    private static int matchSerial = 1;
    private static int nextId = 1;
    private static boolean dirty;

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
        }
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
    }

    private static void index(LootCache cache) {
        byId.put(cache.id(), cache);
        byPlace.computeIfAbsent(cache.dimension(), key -> new HashMap<>()).put(cache.pos().asLong(), cache);
    }

    public static LootCache add(Level level, BlockPos pos) {
        LootCache cache = new LootCache(nextId++, dimensionOf(level), pos);
        index(cache);
        markDirty();
        return cache;
    }

    public static LootCache remove(int id) {
        LootCache cache = byId.remove(id);
        if (cache == null) return null;

        Map<Long, LootCache> places = byPlace.get(cache.dimension());
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
        Map<Long, LootCache> places = byPlace.get(dimensionOf(level));
        return places == null ? null : places.get(pos.asLong());
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

    public static void saveIfDirty() {
        if (dirty) save();
    }

    public static void save() {
        if (file == null) return;

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
