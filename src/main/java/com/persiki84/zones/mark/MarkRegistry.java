package com.persiki84.zones.mark;

import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MarkRegistry {
    private static final Map<String, MapMark> marks = new ConcurrentHashMap<>();

    private static ServerLevel currentLevel;
    private static boolean loadFailed;

    private MarkRegistry() {}

    public static void bind(ServerLevel level) {
        currentLevel = level;
        marks.clear();
        loadFailed = false;
        for (MapMark mark : readStored(level)) {
            marks.put(mark.id(), mark);
        }
    }

    private static List<MapMark> readStored(ServerLevel level) {
        try {
            return MarkStorage.load(level);
        } catch (IOException unreadable) {
            loadFailed = true;
            return List.of();
        }
    }

    public static void unbind() {
        marks.clear();
        loadFailed = false;
        currentLevel = null;
    }

    public static Collection<MapMark> all() {
        return marks.values();
    }

    public static List<String> ids() {
        return new ArrayList<>(marks.keySet());
    }

    public static MapMark byId(String id) {
        return id == null ? null : marks.get(id);
    }

    public static boolean exists(String id) {
        return byId(id) != null;
    }

    public static void upsert(MapMark mark) {
        marks.put(mark.id(), mark);
        persist();
    }

    public static boolean remove(String id) {
        if (marks.remove(id) == null) return false;

        persist();
        return true;
    }

    public static void persist() {
        loadFailed = false;
        save();
    }

    // WHY: после проваленного чтения в памяти пусто, и запись при остановке затёрла бы файл,
    // WHY: если его не удалось отодвинуть; запрет снимает первая правка оператора через persist()
    public static void flush() {
        if (!loadFailed) save();
    }

    private static void save() {
        if (currentLevel == null) return;
        MarkStorage.save(currentLevel, marks.values());
    }
}
