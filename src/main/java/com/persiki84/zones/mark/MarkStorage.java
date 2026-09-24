package com.persiki84.zones.mark;

import com.google.gson.Gson;
import com.persiki84.shared.WorldFiles;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
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

public final class MarkStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ENTRY_LIST = new TypeToken<List<StoredMark>>() {}.getType();
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "marks.json";

    private MarkStorage() {}

    public static List<MapMark> load(ServerLevel level) {
        List<MapMark> marks = new ArrayList<>();
        Path path = WorldFiles.pathFor(level, FOLDER, FILE);
        if (path == null || !Files.exists(path)) return marks;

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<StoredMark> stored = GSON.fromJson(reader, ENTRY_LIST);
            if (stored == null) return marks;

            for (StoredMark entry : stored) {
                MapMark mark = entry.toMark();
                if (mark != null) marks.add(mark);
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Failed to read {}", path, error);
        }
        return marks;
    }

    public static void save(ServerLevel level, Collection<MapMark> marks) {
        Path path = WorldFiles.pathFor(level, FOLDER, FILE);
        if (path == null) return;

        List<StoredMark> stored = new ArrayList<>();
        for (MapMark mark : marks) {
            stored.add(StoredMark.of(mark));
        }
        write(path, stored);
    }

    private static void write(Path path, List<StoredMark> stored) {
        Path temporary = path.resolveSibling(FILE + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, ENTRY_LIST, writer);
            }
            WorldFiles.moveIntoPlace(temporary, path);
        } catch (IOException error) {
            LOGGER.error("Failed to write {}", path, error);
        }
    }

    private static final class StoredMark {
        private String id;
        private int x;
        private int y;
        private int z;
        private String dimension;
        private String label;
        private List<String> lines;
        private String kind;
        private Integer scale;
        private Integer markerRange;
        private Integer hideRadius;
        private String hideShape;
        private Integer hideHeight;
        private Boolean hideShown;
        private Integer hideColor;
        private int color;
        private Boolean inWorld;
        private List<String> teams;

        private static StoredMark of(MapMark mark) {
            StoredMark stored = new StoredMark();
            stored.id = mark.id();
            stored.x = mark.position().getX();
            stored.y = mark.position().getY();
            stored.z = mark.position().getZ();
            stored.dimension = mark.dimension().toString();
            stored.label = mark.label();
            stored.lines = new ArrayList<>(mark.lines());
            stored.kind = mark.kind().id();
            stored.scale = mark.scalePercent();
            stored.markerRange = mark.markerRange();
            stored.hideRadius = mark.hideZone().radius();
            stored.hideShape = mark.hideZone().shape().id();
            stored.hideHeight = mark.hideZone().height();
            stored.hideShown = mark.hideZone().shown();
            stored.hideColor = mark.hideZone().color();
            stored.color = mark.color();
            stored.inWorld = mark.inWorld();
            stored.teams = new ArrayList<>(mark.teams());
            return stored;
        }

        private MapMark toMark() {
            if (id == null || id.isEmpty()) return null;

            ResourceLocation world = ResourceLocation.tryParse(dimension == null ? "" : dimension);
            MapMark mark = new MapMark(id, new BlockPos(x, y, z),
                    world == null ? new ResourceLocation("minecraft", "overworld") : world,
                    label, color == 0 ? MapMark.DEFAULT_COLOR : color);

            // WHY: у файла прошлой версии есть только label, и он обязан остаться единственной
            // WHY: строкой надписи: пустой список стёр бы подписи всех существующих меток
            if (lines != null && !lines.isEmpty()) mark.restoreLines(lines);
            mark.setKind(MarkKind.byId(kind == null ? MarkKind.DEFAULT.id() : kind));
            mark.setScalePercent(scale == null ? MapMark.SCALE_FULL : scale);
            mark.setMarkerRange(markerRange == null ? MapMark.KIND_RANGE : markerRange);
            mark.setHideZone(new MarkHideZone(
                    hideRadius == null ? MarkHideZone.OFF : hideRadius,
                    ZoneShape.byId(hideShape == null ? "" : hideShape),
                    hideHeight == null ? MarkHideZone.WHOLE_COLUMN : hideHeight,
                    hideShown != null && hideShown,
                    hideColor == null ? MarkHideZone.MARK_COLOR : hideColor));
            mark.setInWorld(inWorld == null || inWorld);
            if (teams != null) {
                for (String team : teams) {
                    if (team != null && !team.isEmpty()) mark.allow(team);
                }
            }
            return mark;
        }
    }
}
