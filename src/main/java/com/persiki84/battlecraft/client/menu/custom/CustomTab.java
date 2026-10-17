package com.persiki84.battlecraft.client.menu.custom;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum CustomTab {
    GLASS,
    COLORS,
    INTERFACE,
    PRESETS;

    private final String titleKey = "battlecraft.custom.tab." + name().toLowerCase(Locale.ROOT);
    private final Component title = Component.translatable(titleKey);

    public String titleKey() {
        return titleKey;
    }

    public Component title() {
        return title;
    }

    public String nodeKey() {
        return "tab:" + name();
    }
}
