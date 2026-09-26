package com.persiki84.zones;

import com.google.gson.Gson;
import com.persiki84.shared.WorldFiles;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class ZoneStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ENTRY_LIST = new TypeToken<List<StoredZone>>() {}.getType();
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "zones.json";

    private ZoneStorage() {}

    public static List<Zone> load(ServerLevel level) throws IOException {
        List<Zone> zones = new ArrayList<>();
        Path path = WorldFiles.pathFor(level, FOLDER, FILE);
        if (path == null || !Files.exists(path)) return zones;

        try {
            readInto(path, zones);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to read {}", path, e);
            UnreadableFiles.setAside(path, LOGGER);
            throw new IOException("Unreadable " + path, e);
        }
        return zones;
    }

    private static void readInto(Path path, List<Zone> zones) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<StoredZone> stored = GSON.fromJson(reader, ENTRY_LIST);
            if (stored == null) return;
            for (StoredZone entry : stored) {
                Zone zone = entry.toZone();
                if (zone != null) zones.add(zone);
            }
        }
    }

    public static void save(ServerLevel level, Collection<Zone> zones) {
        Path path = WorldFiles.pathFor(level, FOLDER, FILE);
        if (path == null) return;

        List<StoredZone> stored = new ArrayList<>();
        for (Zone zone : zones) {
            if (zone.source() == ZoneSource.STORED) stored.add(StoredZone.of(zone));
        }

        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(FILE + ".tmp");
            writeJson(temporary, stored);
            WorldFiles.moveIntoPlace(temporary, path);
        } catch (IOException e) {
            LOGGER.error("Failed to write {}", path, e);
        }
    }

    private static void writeJson(Path target, List<StoredZone> stored) throws IOException {
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            GSON.toJson(stored, ENTRY_LIST, writer);
        }
    }

    private static final class StoredZone {
        private String id;
        private String shape;
        private int x;
        private int y;
        private int z;
        private double size;
        private double heightUp;
        private double heightDown;
        private String type;
        private String ownerTeam;
        private int color;
        private int[] spawnAnchor;
        private Map<String, Boolean> rules;
        private String dimension;
        private int markerRange;
        private Boolean hiddenInside;

        private static StoredZone of(Zone zone) {
            ZoneArea area = zone.area();
            StoredZone entry = new StoredZone();
            entry.id = zone.id();
            entry.shape = area.shape().id();
            entry.x = area.center().getX();
            entry.y = area.center().getY();
            entry.z = area.center().getZ();
            entry.size = area.size();
            entry.heightUp = area.heightUp();
            entry.heightDown = area.heightDown();
            entry.type = zone.type().id();
            entry.ownerTeam = zone.ownerTeam();
            entry.color = zone.color();
            BlockPos anchor = zone.spawnAnchor();
            if (anchor != null) entry.spawnAnchor = new int[] { anchor.getX(), anchor.getY(), anchor.getZ() };
            Map<String, Boolean> overrides = zone.rules().overrides();
            if (!overrides.isEmpty()) entry.rules = overrides;
            if (zone.dimension() != null) entry.dimension = zone.dimension().toString();
            entry.markerRange = zone.markerRange();
            entry.hiddenInside = zone.hiddenInside();
            return entry;
        }

        private Zone toZone() {
            ZoneType zoneType = ZoneType.byId(type);
            if (id == null || id.isEmpty() || zoneType == null) {
                LOGGER.warn("Skipping malformed zone entry: id={} type={}", id, type);
                return null;
            }
            ZoneShape zoneShape = ZoneShape.byId(shape);
            ZoneArea area = new ZoneArea(
                    zoneShape == null ? ZoneShape.CIRCLE : zoneShape,
                    new BlockPos(x, y, z),
                    size,
                    heightUp,
                    heightDown);
            Zone zone = new Zone(id, area, zoneType, ownerTeam, color, ZoneSource.STORED);
            if (spawnAnchor != null && spawnAnchor.length == 3) {
                zone.setSpawnAnchor(new BlockPos(spawnAnchor[0], spawnAnchor[1], spawnAnchor[2]));
            }
            zone.setRules(ZoneRules.ofOverrides(rules));
            zone.setDimension(dimension == null ? null : ResourceLocation.tryParse(dimension));
            zone.setMarkerRange(markerRange);
            zone.setHiddenInside(hiddenInside == null || hiddenInside);
            return zone;
        }
    }
}
