package com.persiki84.battlecraft.announce;

import java.util.Locale;

public enum AnnounceStyle {
    NOTICE,
    BANNER,
    BOTH;

    public static final AnnounceStyle DEFAULT = BANNER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "battlecraft.announce.style." + id();
    }

    public boolean noticed() {
        return this != BANNER;
    }

    public boolean bannered() {
        return this != NOTICE;
    }

    public static AnnounceStyle byId(String id) {
        if (id == null || id.isEmpty()) return DEFAULT;

        for (AnnounceStyle style : values()) {
            if (style.id().equalsIgnoreCase(id)) return style;
        }
        return DEFAULT;
    }

    public static AnnounceStyle byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) return DEFAULT;
        return values()[ordinal];
    }
}
