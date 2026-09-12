package com.persiki84.shared.client.ui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.Locale;

public enum UiSoundScheme {
    NOTES("notes", null),
    FUTURE("future", "future"),
    POP("pop", "pop");

    public static final UiSoundScheme FALLBACK = NOTES;

    private final String id;
    private final String folder;

    UiSoundScheme(String id, String folder) {
        this.id = id;
        this.folder = folder;
    }

    public String id() {
        return id;
    }

    public boolean sampled() {
        return folder != null;
    }

    public String translationKey() {
        return "battlecraft.sound." + id;
    }

    public String noteKey() {
        return "battlecraft.sound." + id + ".note";
    }

    public SoundEvent event(UiCue cue) {
        if (folder == null) return null;
        return BuiltInRegistries.SOUND_EVENT.get(
                new ResourceLocation("battlecraft", "ui." + folder + "." + cue.sample()));
    }

    public static UiSoundScheme byId(String id) {
        if (id == null) return FALLBACK;
        for (UiSoundScheme scheme : values()) {
            if (scheme.id.equalsIgnoreCase(id.trim().toLowerCase(Locale.ROOT))) return scheme;
        }
        return FALLBACK;
    }
}
