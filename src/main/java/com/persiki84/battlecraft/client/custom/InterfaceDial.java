package com.persiki84.battlecraft.client.custom;

import com.persiki84.battlecraft.client.hud.HudConfig;

import java.util.function.Supplier;

public enum InterfaceDial {
    SOUND_VOLUME("soundVolume", 1.0f, 0.0f, 2.0f, HudConfig::soundVolume, HudConfig::soundVolume),
    VISUALIZER_GAIN("visualizerGain", 1.0f, 0.25f, 2.0f, HudConfig::visualizerGain, HudConfig::visualizerGain),
    VISUALIZER_SPEED("visualizerSpeed", 1.0f, 0.25f, 2.0f, HudConfig::visualizerSpeed, HudConfig::visualizerSpeed);

    public interface Setter {
        void set(float value);
    }

    private final String id;
    private final float fallback;
    private final float minimum;
    private final float maximum;
    private final Supplier<Float> reader;
    private final Setter setter;

    InterfaceDial(String id, float fallback, float minimum, float maximum,
                  Supplier<Float> reader, Setter setter) {
        this.id = id;
        this.fallback = fallback;
        this.minimum = minimum;
        this.maximum = maximum;
        this.reader = reader;
        this.setter = setter;
    }

    public String id() {
        return id;
    }

    public float fallback() {
        return fallback;
    }

    public float clamp(float value) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public float get() {
        return reader.get();
    }

    public void set(float value) {
        setter.set(clamp(value));
    }

    public void reset() {
        set(fallback);
    }
}
