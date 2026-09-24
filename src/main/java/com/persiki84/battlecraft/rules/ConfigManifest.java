package com.persiki84.battlecraft.rules;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.WorldFiles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManifest {
    public static final int MAX_FILES = 512;
    public static final int MAX_PATH = 128;
    public static final long MISSING = 0L;

    private static final String FOLDER = "battlecraft";
    private static final String FILE = "configs.dat";
    private static final String ENTRIES = "files";
    private static final String PATH = "path";
    private static final String HASH = "hash";

    private static final Map<String, Long> expected = new LinkedHashMap<>();

    private ConfigManifest() {}

    public static void load(MinecraftServer server) {
        expected.clear();
        if (server == null) return;

        Path target = file(server);
        if (!Files.exists(target)) return;

        try (FileInputStream stream = new FileInputStream(target.toFile())) {
            read(NbtIo.readCompressed(stream));
        } catch (Exception failure) {
            BattleCraftMod.LOGGER.error("Failed to load config manifest", failure);
        }
    }

    public static boolean empty() {
        return expected.isEmpty();
    }

    public static int size() {
        return expected.size();
    }

    public static List<String> paths() {
        return new ArrayList<>(expected.keySet());
    }

    public static long hash(String path) {
        Long stored = expected.get(path);
        return stored == null ? MISSING : stored;
    }

    public static void replace(MinecraftServer server, Map<String, Long> fresh) {
        expected.clear();
        for (Map.Entry<String, Long> entry : fresh.entrySet()) {
            if (expected.size() >= MAX_FILES) break;
            if (ConfigScope.watched(entry.getKey())) expected.put(entry.getKey(), entry.getValue());
        }
        save(server);
    }

    public static boolean forget(MinecraftServer server, String path) {
        if (expected.remove(path) == null) return false;

        save(server);
        return true;
    }

    public static void clear(MinecraftServer server) {
        expected.clear();
        save(server);
    }

    private static void save(MinecraftServer server) {
        if (server == null) return;

        Path folder = server.getWorldPath(LevelResource.ROOT).resolve(FOLDER);
        Path target = folder.resolve(FILE);
        Path temporary = folder.resolve(FILE + ".tmp");

        try {
            Files.createDirectories(folder);
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile())) {
                NbtIo.writeCompressed(snapshot(), stream);
            }
            WorldFiles.moveIntoPlace(temporary, target);
        } catch (Exception failure) {
            BattleCraftMod.LOGGER.error("Failed to save config manifest", failure);
        }
    }

    private static CompoundTag snapshot() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> entry : expected.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString(PATH, entry.getKey());
            row.putLong(HASH, entry.getValue());
            list.add(row);
        }
        tag.put(ENTRIES, list);
        return tag;
    }

    private static void read(CompoundTag tag) {
        ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        int skipped = 0;
        for (int index = 0; index < list.size() && expected.size() < MAX_FILES; index++) {
            CompoundTag row = list.getCompound(index);
            String path = row.getString(PATH);
            if (ConfigScope.watched(path)) {
                expected.put(path, row.getLong(HASH));
            } else {
                skipped++;
            }
        }
        BattleCraftMod.LOGGER.info("Config manifest: {} files watched, {} personal or own files skipped, own mods {}",
                expected.size(), skipped, ConfigScope.ownIds());
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FOLDER).resolve(FILE);
    }
}
