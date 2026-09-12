package com.persiki84.battlecraft.client.custom;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum ConfigSection {
    GLASS,
    COLORS,
    INTERFACE,
    HUD;

    public static final Set<ConfigSection> ALL =
            Collections.unmodifiableSet(EnumSet.allOf(ConfigSection.class));

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "battlecraft.custom.section." + id();
    }
}
