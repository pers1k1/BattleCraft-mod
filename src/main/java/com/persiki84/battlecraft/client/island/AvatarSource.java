package com.persiki84.battlecraft.client.island;

public enum AvatarSource {
    DISCORD("discord"),
    SKIN("skin"),
    PICTURE("picture");

    public static final AvatarSource FALLBACK = DISCORD;

    private final String id;

    AvatarSource(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "battlecraft.custom.island.avatar_source." + id;
    }

    public static AvatarSource byId(String id) {
        for (AvatarSource source : values()) {
            if (source.id.equals(id)) return source;
        }
        return FALLBACK;
    }
}
