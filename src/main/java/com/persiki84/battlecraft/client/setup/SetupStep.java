package com.persiki84.battlecraft.client.setup;

public enum SetupStep {
    VISUALS("visuals"),
    TINT("tint"),
    GLASS_THEME("glassTheme"),
    INK("ink"),
    FONT("font"),
    TEXT("text"),
    SOUND("sound"),
    VOICE("voice"),
    DISCORD("discord");

    private final String id;

    SetupStep(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return "battlecraft.setup." + id + ".title";
    }

    public String noteKey() {
        return "battlecraft.setup." + id + ".note";
    }

    public static SetupStep byId(String id) {
        if (id == null) return null;
        for (SetupStep step : values()) {
            if (step.id.equals(id.trim())) return step;
        }
        return null;
    }
}
