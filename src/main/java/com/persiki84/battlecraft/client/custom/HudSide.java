package com.persiki84.battlecraft.client.custom;

import java.util.Locale;

public enum HudSide {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    public boolean horizontal() {
        return this == LEFT || this == RIGHT;
    }
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HudSide byId(String id) {
        if (id == null) return null;
        for (HudSide side : values()) {
            if (side.id().equalsIgnoreCase(id.trim())) return side;
        }
        return null;
    }
}
