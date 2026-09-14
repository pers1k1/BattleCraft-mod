package com.persiki84.battlecraft.sound;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public enum RadioCue {
    KEY_DOWN("key_down", 0.72f, 1.0f, 70L),
    KEY_UP("key_up", 0.50f, 1.0f, 70L),
    INCOMING_OPEN("incoming_open", 0.45f, 1.0f, 90L),
    INCOMING_CLOSE("incoming_close", 0.55f, 1.0f, 90L),
    POWER_ON("power_on", 0.55f, 1.0f, 160L),
    POWER_OFF("power_off", 0.55f, 1.0f, 160L);

    private final String sample;
    private final float gain;
    private final float pitch;
    private final long intervalMs;

    RadioCue(String sample, float gain, float pitch, long intervalMs) {
        this.sample = sample;
        this.gain = gain;
        this.pitch = pitch;
        this.intervalMs = intervalMs;
    }

    public String path() {
        return "radio." + sample;
    }

    public float gain() {
        return gain;
    }

    public float pitch() {
        return pitch;
    }

    public long intervalMs() {
        return intervalMs;
    }

    public SoundEvent event() {
        return BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(BattleCraftMod.MOD_ID, path()));
    }
}
