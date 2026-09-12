package com.persiki84.zones.client;

import com.persiki84.zones.Zone;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientZoneData {
    private static final Map<String, Zone> zones = new LinkedHashMap<>();

    private ClientZoneData() {}

    public static void replaceAll(Collection<Zone> incoming) {
        zones.clear();
        for (Zone zone : incoming) {
            zones.put(zone.id(), zone);
        }
    }

    public static void upsert(Zone zone) {
        zones.put(zone.id(), zone);
    }

    public static void remove(String id) {
        zones.remove(id);
    }

    public static void clear() {
        zones.clear();
    }

    public static Collection<Zone> all() {
        return zones.values();
    }

    public static Zone byId(String id) {
        return zones.get(id);
    }
}
