package com.persiki84.zones.mark;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MarkRegistry {
    private static final Map<String, MapMark> marks = new ConcurrentHashMap<>();

    private static ServerLevel currentLevel;

    private MarkRegistry() {}

    public static void bind(ServerLevel level) {
        currentLevel = level;
        marks.clear();
        for (MapMark mark : MarkStorage.load(level)) {
            marks.put(mark.id(), mark);
        }
    }

    public static void unbind() {
        marks.clear();
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
        if (currentLevel == null) return;
        MarkStorage.save(currentLevel, marks.values());
    }
}
