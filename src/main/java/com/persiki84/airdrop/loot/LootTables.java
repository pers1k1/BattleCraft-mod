package com.persiki84.airdrop.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.persiki84.airdrop.AirDropFiles;
import com.persiki84.airdrop.AirDropMod;
import com.persiki84.shared.WorldFiles;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class LootTables {
    public static final String AIRDROP = "global";
    public static final int NAME_LIMIT = 24;

    private static final String FOLDER = "airdrop_loot";
    private static final String SUFFIX = ".json";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1," + NAME_LIMIT + "}");
    private static final Set<String> RESERVED = Set.of("tables", "create", "delete", "copy");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, LootTable> TABLES = new TreeMap<>();
    private static final Set<String> UNREADABLE = new HashSet<>();
    private static Path folder;

    private LootTables() {}

    public static void reload(Path configDir) {
        folder = configDir.resolve(FOLDER);
        TABLES.clear();
        UNREADABLE.clear();
        readFolder();
        if (!TABLES.containsKey(AIRDROP)) TABLES.put(AIRDROP, LootTable.empty(AIRDROP));
    }

    private static void readFolder() {
        try {
            Files.createDirectories(folder);
        } catch (IOException error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot create loot folder: {}", error.toString());
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*" + SUFFIX)) {
            for (Path file : files) {
                readTable(file);
            }
        } catch (IOException error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot list loot tables: {}", error.toString());
        }
    }

    private static void readTable(Path file) {
        String fileName = file.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - SUFFIX.length());
        if (!validName(name)) return;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            TABLES.put(name, LootTable.fromJson(name, json));
        } catch (Exception error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot read loot table {}: {}", fileName, error.toString());
            UNREADABLE.add(name);
        }
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches() && !RESERVED.contains(name);
    }

    public static List<String> names() {
        return new ArrayList<>(TABLES.keySet());
    }

    public static List<LootTable> all() {
        return new ArrayList<>(TABLES.values());
    }

    public static LootTable get(String name) {
        return TABLES.get(name);
    }

    public static LootTable getOrEmpty(String name) {
        LootTable table = TABLES.get(name);
        return table == null ? LootTable.empty(name) : table;
    }

    public static boolean exists(String name) {
        return TABLES.containsKey(name);
    }

    // WHY: вместо нечитаемой таблицы в памяти стоит пустая или её нет вовсе, и первая правка
    // WHY: записала бы эту пустоту поверх файла: битый файл до правки не трогается, а на правке
    // WHY: сначала уезжает в сторону, чтобы его содержимое можно было восстановить руками
    public static void put(LootTable table) {
        if (UNREADABLE.remove(table.name()) && folder != null) AirDropFiles.setAside(fileOf(table.name()));
        TABLES.put(table.name(), table);
        save(table);
    }

    public static boolean delete(String name) {
        if (AIRDROP.equals(name) || TABLES.remove(name) == null) return false;

        try {
            Files.deleteIfExists(fileOf(name));
        } catch (IOException error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot delete loot table {}: {}", name, error.toString());
        }
        return true;
    }

    // WHY: таблица пишется во временный файл и подменяется целиком: оборванная запись оставляла
    // WHY: полфайла, и на следующем чтении таблица пропадала вся. Кодировка явная - системная
    // WHY: на Windows это cp1251, и имена предметов в NBT доезжали до Linux-сервера битыми
    private static void save(LootTable table) {
        if (folder == null) return;

        Path target = fileOf(table.name());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(folder);
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(table.toJson(), writer);
            }
            WorldFiles.moveIntoPlace(temporary, target);
        } catch (IOException error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot write loot table {}: {}", table.name(), error.toString());
        }
    }

    private static Path fileOf(String name) {
        return folder.resolve(name + SUFFIX);
    }
}
