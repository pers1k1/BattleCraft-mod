package com.persiki84.zones;

import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ZoneRegistry {
    private static final Map<String, Zone> zones = new ConcurrentHashMap<>();
    private static ServerLevel currentLevel;
    private static int forbiddenRules;
    private static boolean loadFailed;

    private ZoneRegistry() {}

    public static void bind(ServerLevel level) {
        currentLevel = level;
        zones.clear();
        loadFailed = false;
        boolean adopted = false;
        for (Zone zone : readStored(level)) {
            adopted |= adopt(zone, level);
            zones.put(zone.id(), zone);
        }
        refreshForbiddenRules();
        if (adopted) ZoneStorage.save(level, zones.values());
    }

    private static List<Zone> readStored(ServerLevel level) {
        try {
            return ZoneStorage.load(level);
        } catch (IOException unreadable) {
            loadFailed = true;
            return List.of();
        }
    }

    // WHY: до 19.09.2026 у зоны не было мира, и записанные тогда зоны действовали во всех сразу.
    // WHY: Они заводились в основном мире, поэтому при первом чтении получают именно его
    private static boolean adopt(Zone zone, ServerLevel level) {
        if (zone.dimension() != null) return false;

        zone.setDimension(level.dimension().location());
        return true;
    }

    public static void unbind() {
        zones.clear();
        forbiddenRules = 0;
        loadFailed = false;
        currentLevel = null;
    }

    public static boolean anyZoneForbids(ZoneRule rule) {
        return (forbiddenRules & (1 << rule.ordinal())) != 0;
    }

    private static void refreshForbiddenRules() {
        int mask = 0;
        for (Zone zone : zones.values()) {
            for (ZoneRule rule : ZoneRule.values()) {
                if (!zone.allows(rule)) mask |= 1 << rule.ordinal();
            }
        }
        forbiddenRules = mask;
    }

    public static Collection<Zone> all() {
        return zones.values();
    }

    public static List<String> ids() {
        return new ArrayList<>(zones.keySet());
    }

    public static Zone byId(String id) {
        return zones.get(id);
    }

    public static boolean exists(String id) {
        return zones.containsKey(id);
    }

    public static void upsert(Zone zone) {
        zones.put(zone.id(), zone);
        persist();
    }

    public static boolean remove(String id) {
        if (zones.remove(id) == null) return false;
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
        refreshForbiddenRules();
        ZoneStorage.save(currentLevel, zones.values());
    }

}
