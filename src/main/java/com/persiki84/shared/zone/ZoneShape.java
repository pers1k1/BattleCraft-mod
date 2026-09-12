package com.persiki84.shared.zone;

import java.util.Locale;

public enum ZoneShape {
    CIRCLE,
    SQUARE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ZoneShape byId(String id) {
        for (ZoneShape shape : values()) {
            if (shape.id().equalsIgnoreCase(id)) return shape;
        }
        return null;
    }
}
