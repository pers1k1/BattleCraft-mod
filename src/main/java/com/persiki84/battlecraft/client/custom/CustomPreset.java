package com.persiki84.battlecraft.client.custom;

import com.persiki84.shared.client.ui.UiQuality;

import java.util.EnumMap;
import java.util.Map;

public enum CustomPreset {
    DEW("liquid", UiQuality.Mode.LIQUID, true),
    DEW_DIM("liquid_dim", UiQuality.Mode.LIQUID, true),
    CLEAR("clear", UiQuality.Mode.LIQUID, true),
    CLEAR_DIM("clear_dim", UiQuality.Mode.LIQUID, true),
    OUTWARD("outward", UiQuality.Mode.LIQUID, true),
    REGLASS("reglass", UiQuality.Mode.LIQUID, true),
    CLASSIC("classic", UiQuality.Mode.LIQUID, true),
    CRYSTAL("crystal", UiQuality.Mode.LIQUID, false),
    LENS("lens", UiQuality.Mode.LIQUID, false),
    PRISM("prism", UiQuality.Mode.LIQUID, false),
    FROSTED("frosted", UiQuality.Mode.LIQUID, false),
    BLUR("blur", UiQuality.Mode.BLUR, false),
    PLAIN("plain", UiQuality.Mode.PLAIN, false);

    private static final float DIM_BRIGHTNESS = 0.72f;

    private final String id;
    private final UiQuality.Mode mode;
    private final boolean liquid;

    CustomPreset(String id, UiQuality.Mode mode, boolean liquid) {
        this.id = id;
        this.mode = mode;
        this.liquid = liquid;
    }

    public String id() {
        return id;
    }

    public UiQuality.Mode mode() {
        return mode;
    }

    public boolean liquid() {
        return liquid;
    }

    public String translationKey() {
        return "battlecraft.custom.preset." + id;
    }

    public String hintKey() {
        return "battlecraft.custom.preset." + id + ".hint";
    }

    public Map<GlassKey, Float> tuning() {
        Map<GlassKey, Float> tuned = new EnumMap<>(GlassKey.class);
        switch (this) {
            case REGLASS -> reglass(tuned);
            case CRYSTAL -> crystal(tuned);
            case LENS -> lens(tuned);
            case DEW -> dew(tuned);
            case DEW_DIM -> dewDim(tuned);
            case CLEAR -> clear(tuned);
            case CLEAR_DIM -> clearDim(tuned);
            case OUTWARD -> outward(tuned);
            case PRISM -> prism(tuned);
            case FROSTED -> frosted(tuned);
            case BLUR -> blur(tuned);
            case PLAIN -> plain(tuned);
            default -> {
            }
        }
        return tuned;
    }

    private static void reglass(Map<GlassKey, Float> tuned) {
        tuned.put(GlassKey.DENSITY, 0.0f);
        tuned.put(GlassKey.LIFT, 0.0f);
        tuned.put(GlassKey.BAND, 20.0f);
        tuned.put(GlassKey.PULL, 4.0f);
        tuned.put(GlassKey.DISPERSION, 0.35f);
        tuned.put(GlassKey.ZOOM, 0.0f);
        tuned.put(GlassKey.BLUR_LEVELS, 6.0f);
        tuned.put(GlassKey.BLUR_DOWN, 1.4f);
        tuned.put(GlassKey.BLUR_UP, 0.8f);
        tuned.put(GlassKey.FRESNEL_RANGE, 30.0f);
        tuned.put(GlassKey.FRESNEL_HARD, 0.2f);
        tuned.put(GlassKey.FRESNEL_GLOW, 0.2f);
        tuned.put(GlassKey.RADIUS_PANEL, 8.0f);
        tuned.put(GlassKey.SWEEP_LIGHT, 0.0f);
    }

    private static void crystal(Map<GlassKey, Float> tuned) {
        tuned.put(GlassKey.DENSITY, 0.34f);
        tuned.put(GlassKey.BAND, 18.0f);
        tuned.put(GlassKey.PULL, 7.4f);
        tuned.put(GlassKey.DISPERSION, 0.72f);
        tuned.put(GlassKey.ZOOM, 0.046f);
        tuned.put(GlassKey.RINGS, 14.0f);
        tuned.put(GlassKey.EDGE_RINGS, 9.0f);
        tuned.put(GlassKey.BLUR_LEVELS, 5.0f);
        tuned.put(GlassKey.BLUR_DOWN, 1.2f);
        tuned.put(GlassKey.SWEEP_LIGHT, 0.58f);
    }

    private static void lens(Map<GlassKey, Float> tuned) {
        tuned.put(GlassKey.DENSITY, 0.08f);
        tuned.put(GlassKey.LIFT, 0.3f);
        tuned.put(GlassKey.DISSOLVE, 4.0f);
        tuned.put(GlassKey.DISSOLVE_CHAOS, 0.35f);
        tuned.put(GlassKey.BAND, 24.0f);
        tuned.put(GlassKey.PULL, 8.6f);
        tuned.put(GlassKey.FALLOFF, 1.1f);
        tuned.put(GlassKey.ZOOM, 0.062f);
        tuned.put(GlassKey.RINGS, 16.0f);
        tuned.put(GlassKey.EDGE_RINGS, 11.0f);
        tuned.put(GlassKey.DISPERSION, 0.45f);
        tuned.put(GlassKey.BLUR_LEVELS, 4.0f);
        tuned.put(GlassKey.BLUR_DOWN, 1.0f);
        tuned.put(GlassKey.BLUR_UP, 0.5f);
        tuned.put(GlassKey.SWEEP_LIGHT, 0.18f);
        tuned.put(GlassKey.EDGE_PIXELS, 0.9f);
    }

    private static void dew(Map<GlassKey, Float> tuned) {
        tuned.put(GlassKey.DENSITY, 0.14f);
        tuned.put(GlassKey.LIFT, 0.5f);
        tuned.put(GlassKey.BAND, 11.0f);
        tuned.put(GlassKey.PULL, 15.0f);
        tuned.put(GlassKey.ZOOM, 0.11f);
        tuned.put(GlassKey.RINGS, 16.0f);
        tuned.put(GlassKey.EDGE_RINGS, 8.0f);
        tuned.put(GlassKey.DISPERSION, 0.22f);
        tuned.put(GlassKey.BLUR_LEVELS, 3.0f);
        tuned.put(GlassKey.BLUR_DOWN, 1.0f);
        tuned.put(GlassKey.RADIUS_PANEL, 11.0f);
        tuned.put(GlassKey.SWEEP_LIGHT, 0.26f);
    }

    private static void clear(Map<GlassKey, Float> tuned) {
        dew(tuned);
        tuned.put(GlassKey.BLUR_SHRINK, 1.0f);
        tuned.put(GlassKey.BLUR_LEVELS, 1.0f);
        tuned.put(GlassKey.BLUR_DOWN, 0.0f);
        tuned.put(GlassKey.BLUR_UP, 0.0f);
        tuned.put(GlassKey.BLUR_POLISH, 0.0f);
        tuned.put(GlassKey.DENSITY, 0.1f);
        tuned.put(GlassKey.PULL, 18.0f);
    }

    private static void outward(Map<GlassKey, Float> tuned) {
        dew(tuned);
        tuned.put(GlassKey.BEND_AIM, 1.0f);
        tuned.put(GlassKey.BAND, 14.0f);
        tuned.put(GlassKey.PULL, 9.0f);
        tuned.put(GlassKey.ZOOM, 0.0f);
    }

    private static void dewDim(Map<GlassKey, Float> tuned) {
        dew(tuned);
        tuned.put(GlassKey.BRIGHTNESS, DIM_BRIGHTNESS);
    }

    private static void clearDim(Map<GlassKey, Float> tuned) {
        clear(tuned);
        tuned.put(GlassKey.BRIGHTNESS, DIM_BRIGHTNESS);
    }

    private static void prism(Map<GlassKey, Float> tuned) {
        tuned.put(GlassKey.DENSITY, 0.22f);
        tuned.put(GlassKey.BAND, 20.0f);
        tuned.put(GlassKey.PULL, 7.8f);
        tuned.put(GlassKey.ZOOM, 0.03f);
        tuned.put(GlassKey.DISPERSION, 1.35f);
        tuned.put(GlassKey.RINGS, 15.0f);
        tuned.put(GlassKey.EDGE_RINGS, 12.0f);
        tuned.put(GlassKey.BLUR_LEVELS, 5.0f);
        tuned.put(GlassKey.BLUR_DOWN, 1.2f);
        tuned.put(GlassKey.SWEEP_LIGHT, 0.5f);
    }

    private static void frosted(Map<GlassKey, Float> tuned) {
        tuned.put(GlassKey.DENSITY, 0.78f);
        tuned.put(GlassKey.BAND, 9.0f);
        tuned.put(GlassKey.PULL, 2.6f);
        tuned.put(GlassKey.DISPERSION, 0.12f);
        tuned.put(GlassKey.BLUR_LEVELS, 6.0f);
        tuned.put(GlassKey.BLUR_DOWN, 1.5f);
        tuned.put(GlassKey.BLUR_UP, 0.9f);
        tuned.put(GlassKey.SWEEP_LIGHT, 0.24f);
    }

    private static void blur(Map<GlassKey, Float> tuned) {
        tuned.put(GlassKey.DENSITY, 0.58f);
        tuned.put(GlassKey.DISPERSION, 0.0f);
        tuned.put(GlassKey.PULL, 0.0f);
        tuned.put(GlassKey.ZOOM, 0.0f);
        tuned.put(GlassKey.BLUR_LEVELS, 5.0f);
        tuned.put(GlassKey.BLUR_DOWN, 1.3f);
    }

    private static void plain(Map<GlassKey, Float> tuned) {
        tuned.put(GlassKey.DENSITY, 1.0f);
        tuned.put(GlassKey.DISPERSION, 0.0f);
        tuned.put(GlassKey.PULL, 0.0f);
        tuned.put(GlassKey.ZOOM, 0.0f);
        tuned.put(GlassKey.SURFACE_ALPHA, 0.94f);
    }

    public static CustomPreset byId(String id) {
        if (id == null) return null;
        for (CustomPreset preset : values()) {
            if (preset.id.equalsIgnoreCase(id.trim())) return preset;
        }
        return null;
    }

}
