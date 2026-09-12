package com.persiki84.battlecraft;

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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MatchStore {
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "match.dat";

    private MatchStore() {}

    public static final class Snapshot {
        private final BattleCraftManager.GamePhase phase;
        private final long elapsedMillis;
        private final Set<UUID> ready;

        private Snapshot(BattleCraftManager.GamePhase phase, long elapsedMillis, Set<UUID> ready) {
            this.phase = phase;
            this.elapsedMillis = elapsedMillis;
            this.ready = ready;
        }

        public BattleCraftManager.GamePhase phase() { return phase; }
        public long elapsedMillis() { return elapsedMillis; }
        public Set<UUID> ready() { return ready; }
    }

    public static void save(MinecraftServer server, BattleCraftManager.GamePhase phase,
                            long elapsedMillis, Set<UUID> ready) {
        if (server == null) return;

        Path folder = server.getWorldPath(LevelResource.ROOT).resolve(FOLDER);
        Path target = folder.resolve(FILE);
        Path temporary = folder.resolve(FILE + ".tmp");

        try {
            Files.createDirectories(folder);
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile())) {
                NbtIo.writeCompressed(snapshot(phase, elapsedMillis, ready), stream);
            }
            WorldFiles.moveIntoPlace(temporary, target);
        } catch (Exception failure) {
            BattleCraftMod.LOGGER.error("Failed to save match state", failure);
        }
    }

    private static CompoundTag snapshot(BattleCraftManager.GamePhase phase, long elapsedMillis, Set<UUID> ready) {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", phase.name());
        tag.putLong("elapsed", Math.max(0L, elapsedMillis));

        ListTag list = new ListTag();
        for (UUID player : ready) {
            CompoundTag row = new CompoundTag();
            row.putUUID("id", player);
            list.add(row);
        }
        tag.put("ready", list);
        return tag;
    }

    public static Snapshot load(MinecraftServer server) {
        if (server == null) return null;

        Path target = server.getWorldPath(LevelResource.ROOT).resolve(FOLDER).resolve(FILE);
        if (!Files.exists(target)) return null;

        try (FileInputStream stream = new FileInputStream(target.toFile())) {
            return read(NbtIo.readCompressed(stream));
        } catch (Exception failure) {
            BattleCraftMod.LOGGER.error("Failed to load match state", failure);
            return null;
        }
    }

    private static Snapshot read(CompoundTag tag) {
        BattleCraftManager.GamePhase phase = phaseOf(tag.getString("phase"));
        Set<UUID> ready = new HashSet<>();

        ListTag list = tag.getList("ready", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag row = list.getCompound(index);
            if (row.hasUUID("id")) ready.add(row.getUUID("id"));
        }
        return new Snapshot(phase, tag.getLong("elapsed"), ready);
    }

    private static BattleCraftManager.GamePhase phaseOf(String name) {
        for (BattleCraftManager.GamePhase phase : BattleCraftManager.GamePhase.values()) {
            if (phase.name().equals(name)) {
                return phase == BattleCraftManager.GamePhase.ENDED ? BattleCraftManager.GamePhase.LOBBY : phase;
            }
        }
        return BattleCraftManager.GamePhase.LOBBY;
    }
}
