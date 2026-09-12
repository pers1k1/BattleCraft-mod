package com.persiki84.battlecraft.client.custom;

import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiScale;

import java.util.Locale;

public enum GlassKey {
    SURFACE_ALPHA(Group.MATERIAL, "surfaceAlpha", 0.15f, 1.0f, UiGlassStyle.SURFACE_ALPHA, 0.01f,
            UiGlassStyle::surfaceAlpha),
    DENSITY(Group.MATERIAL, "density", 0.0f, 1.0f, UiGlassStyle.DENSITY, 0.01f,
            UiGlassStyle::density),
    BRIGHTNESS(Group.MATERIAL, "brightness", 0.3f, 2.0f, UiGlassStyle.BRIGHTNESS, 0.02f,
            UiGlassStyle::brightness),
    LIFT(Group.MATERIAL, "lift", 0.0f, 2.5f, UiGlassStyle.LIFT_GAIN, 0.05f,
            UiGlassStyle::liftGain),
    EDGE_PIXELS(Group.MATERIAL, "edge", 0.2f, 3.0f, UiGlassStyle.EDGE_PIXELS, 0.05f,
            UiGlassStyle::edgePixels),
    BORDER(Group.MATERIAL, "border", 0.0f, 6.0f, UiGlassStyle.BORDER, 0.25f,
            UiGlassStyle::border),
    SOFTNESS(Group.MATERIAL, "softness", 0.0f, 40.0f, UiGlassStyle.SOFTNESS, 0.5f,
            UiGlassStyle::softness),
    FRESNEL_RANGE(Group.SHINE, "fresnelRange", 4.0f, 120.0f, UiGlassStyle.FRESNEL_RANGE, 1.0f,
            UiGlassStyle::fresnelRange),
    FRESNEL_HARD(Group.SHINE, "fresnelHard", 0.0f, 1.0f, UiGlassStyle.FRESNEL_HARD, 0.01f,
            UiGlassStyle::fresnelHard),
    FRESNEL_GLOW(Group.SHINE, "fresnelGlow", 0.0f, 1.0f, UiGlassStyle.FRESNEL_GLOW, 0.01f,
            UiGlassStyle::fresnelGlow),
    DISSOLVE(Group.MATERIAL, "dissolve", 0.0f, 24.0f, UiGlassStyle.DISSOLVE, 0.5f,
            UiGlassStyle::dissolve),
    DISSOLVE_CHAOS(Group.MATERIAL, "dissolveChaos", 0.0f, 1.0f, UiGlassStyle.DISSOLVE_CHAOS, 0.02f,
            UiGlassStyle::dissolveChaos),
    DISSOLVE_DRIFT(Group.MATERIAL, "dissolveDrift", 0.0f, 3.0f, UiGlassStyle.DISSOLVE_DRIFT, 0.05f,
            UiGlassStyle::dissolveDrift),
    SQUIRCLE(Group.MATERIAL, "squircle", 2.0f, 8.0f, UiGlassStyle.SQUIRCLE, 0.1f,
            UiGlassStyle::squircle),
    SWITCH_SQUIRCLE(Group.MATERIAL, "switchSquircle", 2.0f, 8.0f, UiGlassStyle.SWITCH_SQUIRCLE, 0.1f,
            UiGlassStyle::switchSquircle),
    RADIUS_PANEL(Group.MATERIAL, "radiusPanel", 0.0f, 20.0f, UiGlassStyle.RADIUS_PANEL, 0.5f,
            UiGlassStyle::radiusPanel),
    RADIUS_CELL(Group.MATERIAL, "radiusCell", 0.0f, 20.0f, UiGlassStyle.RADIUS_CELL, 0.5f,
            UiGlassStyle::radiusCell),
    HUD_SCALE(Group.MATERIAL, "hudScale", 0.5f, 2.5f, 1.0f, 0.05f,
            UiScale::setUserScale),

    BAND(Group.LENS, "band", 0.0f, 30.0f, UiGlassStyle.BAND, 0.5f,
            UiGlassStyle::band),
    PULL(Group.LENS, "pull", 0.0f, 24.0f, UiGlassStyle.PULL, 0.1f,
            UiGlassStyle::pull),
    SWITCH_LENS(Group.LENS, "switchLens", 0.0f, 4.0f, UiGlassStyle.SWITCH_LENS, 0.05f,
            UiGlassStyle::switchLens),
    FALLOFF(Group.LENS, "falloff", 0.4f, 3.0f, UiGlassStyle.FALLOFF, 0.05f,
            UiGlassStyle::falloff),
    BEND_AIM(Group.LENS, "bendAim", -1.0f, 1.0f, UiGlassStyle.BEND_AIM, 0.05f,
            UiGlassStyle::bendAim),
    BEND_REACH(Group.LENS, "bendReach", 0.5f, 10.0f, UiGlassStyle.BEND_REACH, 0.1f,
            UiGlassStyle::bendReach),
    ZOOM(Group.LENS, "zoom", 0.0f, 0.12f, UiGlassStyle.ZOOM, 0.002f,
            UiGlassStyle::zoom),
    RINGS(Group.LENS, "rings", 3.0f, 16.0f, UiGlassStyle.RINGS, 1.0f,
            value -> UiGlassStyle.rings(Math.round(value))),
    EDGE_RINGS(Group.LENS, "edgeRings", 1.0f, 12.0f, UiGlassStyle.EDGE_RINGS, 1.0f,
            value -> UiGlassStyle.edgeRings(Math.round(value))),
    DISPERSION(Group.LENS, "dispersion", 0.0f, 2.0f, UiGlassStyle.DISPERSION, 0.01f,
            UiGlassStyle::dispersion),
    SATURATION(Group.LENS, "saturation", 0.0f, 3.0f, UiGlassStyle.SATURATION, 0.02f,
            UiGlassStyle::saturation),
    BLUR_SHRINK(Group.BLUR, "blurShrink", 1.0f, 4.0f, UiGlassStyle.BLUR_SHRINK, 1.0f,
            value -> UiGlassStyle.blurShrink(Math.round(value))),
    BLUR_LEVELS(Group.BLUR, "blurLevels", 1.0f, 6.0f, UiGlassStyle.BLUR_LEVELS, 1.0f,
            value -> UiGlassStyle.blurLevels(Math.round(value))),
    BLUR_DOWN(Group.BLUR, "blurDown", 0.0f, 2.0f, UiGlassStyle.BLUR_DOWN, 0.05f,
            UiGlassStyle::blurDown),
    BLUR_UP(Group.BLUR, "blurUp", 0.0f, 1.2f, UiGlassStyle.BLUR_UP, 0.05f,
            UiGlassStyle::blurUp),
    BLUR_POLISH(Group.BLUR, "blurPolish", 0.0f, 1.5f, UiGlassStyle.BLUR_POLISH, 0.05f,
            UiGlassStyle::blurPolish),

    SWEEP_DEPTH(Group.SHINE, "sweepDepth", 0.0f, 8.0f, UiGlassStyle.SWEEP_DEPTH, 0.1f,
            UiGlassStyle::sweepDepth),
    SWEEP_LIGHT(Group.SHINE, "sweepLight", 0.0f, 1.0f, UiGlassStyle.SWEEP_LIGHT, 0.02f,
            UiGlassStyle::sweepLight),
    SWEEP_POWER(Group.SHINE, "sweepPower", 1.0f, 16.0f, UiGlassStyle.SWEEP_POWER, 0.5f,
            UiGlassStyle::sweepPower),
    SWEEP_PERIOD(Group.SHINE, "sweepPeriod", 300.0f, 5000.0f, UiGlassStyle.SWEEP_PERIOD, 50.0f,
            UiGlassStyle::sweepPeriod),

    TEXT_WEIGHT(Group.TEXT, "textWeight", -1.0f, 1.0f, UiGlassStyle.TEXT_WEIGHT, 0.05f,
            UiGlassStyle::textWeight),
    GLOW_ALPHA(Group.TEXT, "glowAlpha", 0.0f, 1.0f, UiGlassStyle.GLOW_ALPHA, 0.01f,
            UiGlassStyle::glowAlpha),
    GLOW_SPREAD(Group.TEXT, "glowSpread", 0.0f, 1.0f, UiGlassStyle.GLOW_SPREAD, 0.01f,
            UiGlassStyle::glowSpread),
    GLOW_LIFT(Group.TEXT, "glowLift", 0.0f, 1.0f, UiGlassStyle.GLOW_LIFT, 0.01f,
            UiGlassStyle::glowLift);

    public enum Group {
        MATERIAL,
        LENS,
        BLUR,
        SHINE,
        TEXT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public interface Setter {
        void set(float value);
    }

    private static final java.util.EnumSet<GlassKey> PANE_ONLY =
            java.util.EnumSet.of(FRESNEL_RANGE, FRESNEL_HARD, FRESNEL_GLOW, SATURATION, SOFTNESS, SWITCH_LENS);
    private static final java.util.EnumSet<GlassKey> RING_ONLY =
            java.util.EnumSet.of(RINGS, EDGE_RINGS);

    private final Group group;
    private final String id;
    private final float minimum;
    private final float maximum;
    private final float fallback;
    private final float step;
    private final Setter setter;

    GlassKey(Group group, String id, float minimum, float maximum, float fallback, float step,
             Setter setter) {
        this.group = group;
        this.id = id;
        this.minimum = minimum;
        this.maximum = maximum;
        this.fallback = fallback;
        this.step = step;
        this.setter = setter;
    }

    public Group group() {
        return group;
    }

    public boolean active(boolean liquid) {
        if (PANE_ONLY.contains(this)) return liquid;
        if (RING_ONLY.contains(this)) return !liquid;
        return true;
    }

    public String reasonKey(boolean liquid) {
        if (active(liquid)) return null;
        return PANE_ONLY.contains(this)
                ? "battlecraft.custom.blocked.needs_liquid"
                : "battlecraft.custom.blocked.needs_rings";
    }

    public String id() {
        return id;
    }

    public float minimum() {
        return minimum;
    }

    public float maximum() {
        return maximum;
    }

    public float fallback() {
        return fallback;
    }

    public float step() {
        return step;
    }

    public float clamp(float value) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public void apply(float value) {
        setter.set(clamp(value));
    }

    public String translationKey() {
        return "battlecraft.custom.glass." + id;
    }

    public String hintKey() {
        return translationKey() + ".hint";
    }

    public static GlassKey byId(String id) {
        for (GlassKey key : values()) {
            if (key.id.equals(id)) return key;
        }
        return null;
    }
}
