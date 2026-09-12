package com.persiki84.battlecraft.client.custom;

import java.util.Locale;

public enum HudAlign {
    FREE,
    START,
    CENTER,
    END;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HudAlign byId(String id) {
        if (id == null) return FREE;
        for (HudAlign align : values()) {
            if (align.id().equalsIgnoreCase(id.trim())) return align;
        }
        return FREE;
    }
}
