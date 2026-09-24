package com.persiki84.shared.menu;

public enum MenuKind {
    MENU("battlecraft.presence.menu"),
    SHOP("battlecraft.presence.shop"),
    ADMIN("battlecraft.presence.admin"),
    CUSTOM("battlecraft.presence.custom"),
    MAP("battlecraft.presence.map");

    public static final int CLOSED = -1;

    private static final MenuKind[] ORDER = values();

    private final String key;

    MenuKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static MenuKind byIndex(int index) {
        return index < 0 || index >= ORDER.length ? null : ORDER[index];
    }

    public static int index(MenuKind kind) {
        return kind == null ? CLOSED : kind.ordinal();
    }
}
