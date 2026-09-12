package com.persiki84.zones;

import java.util.Locale;

public enum ZoneType {
    BASE,
    SHOP,
    CAPTURE_POINT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isCreatableByCommand() {
        return this != CAPTURE_POINT;
    }

    public static ZoneType byId(String id) {
        if (id == null) return null;
        if (id.equalsIgnoreCase("safezone") || id.equalsIgnoreCase("spawn")) return BASE;

        for (ZoneType type : values()) {
            if (type.id().equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
