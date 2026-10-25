package com.persiki84.battlecraft.mode;

import javax.annotation.Nullable;

// WHY: пресетов ровно два, как решил владелец: основной с рудниками и быстрый захват без них.
// WHY: Порядок перечисления это порядок плиток в лобби и узлов студии
public enum ModeId {
    MAIN("main"),
    FAST("fast");

    private final String id;

    ModeId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "battlecraft.mode." + id;
    }

    @Nullable
    public static ModeId byId(@Nullable String id) {
        if (id == null) return null;
        for (ModeId mode : values()) {
            if (mode.id.equals(id)) return mode;
        }
        return null;
    }
}
