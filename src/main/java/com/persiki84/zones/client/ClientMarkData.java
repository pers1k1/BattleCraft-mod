package com.persiki84.zones.client;

import com.persiki84.zones.mark.MapMark;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientMarkData {
    private static final Map<String, MapMark> marks = new LinkedHashMap<>();

    private ClientMarkData() {}

    public static void replaceAll(Collection<MapMark> incoming) {
        marks.clear();
        for (MapMark mark : incoming) {
            marks.put(mark.id(), mark);
        }
    }

    public static void clear() {
        marks.clear();
    }

    public static Collection<MapMark> all() {
        return marks.values();
    }

    public static MapMark byId(String id) {
        return marks.get(id);
    }
}
