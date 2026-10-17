package com.persiki84.battlecraft.craft;

import java.util.Locale;

public enum CraftPhase {
    ALWAYS("always"),
    MATCH("match"),
    LOBBY("lobby");

    private final String id;

    CraftPhase(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return "battlecraft.craft.phase." + id;
    }

    public boolean activeWhen(boolean matchRunning) {
        return switch (this) {
            case ALWAYS -> true;
            case MATCH -> matchRunning;
            case LOBBY -> !matchRunning;
        };
    }

    public static CraftPhase byId(String id) {
        if (id == null) return null;
        String wanted = id.toLowerCase(Locale.ROOT);
        for (CraftPhase phase : values()) {
            if (phase.id.equals(wanted)) return phase;
        }
        return null;
    }

    public static CraftPhase byOrdinal(int ordinal) {
        CraftPhase[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ALWAYS;
    }
}
