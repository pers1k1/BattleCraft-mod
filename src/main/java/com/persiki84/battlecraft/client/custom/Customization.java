package com.persiki84.battlecraft.client.custom;

import com.persiki84.battlecraft.client.LauncherTheme;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.font.FontShape;
import com.persiki84.shared.client.font.MsdfFontSets;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiMotion;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiQuality;
import com.persiki84.shared.client.ui.UiSoundScheme;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import com.persiki84.shared.client.ui.UiWorldPalette;

import java.util.EnumMap;
import java.util.Map;

public final class Customization {
    private static final Map<GlassKey, Float> glass = new EnumMap<>(GlassKey.class);
    private static final Map<PaletteKey, Integer> colors = new EnumMap<>(PaletteKey.class);

    private static final float GLASS_TINT = 0.45f;
    private static final float GLASS_FALL = 0.30f;

    private static CustomPreset preset = CustomPreset.DEW;
    private static FontShape font = FontShape.FALLBACK;
    private static UiSoundScheme sound = UiSoundScheme.FALLBACK;
    private static UiMotionSet motion = UiMotionSet.IGNITE;
    private static UiQuality.Mode mode = UiQuality.Mode.LIQUID;
    private static boolean followLauncher = true;
    private static boolean glassFollowsTheme;
    private static boolean accentInk;
    private static boolean liquid = true;
    private static boolean heartbeat;
    private static boolean loaded;
    private static boolean dirty;

    private Customization() {}

    public static void load() {
        if (loaded) return;

        loaded = true;
        ConfigFile.read();
        apply();
    }

    public static void apply() {
        UiQuality.use(mode);
        UiMotion.set(motion);
        UiGlassStyle.reset();
        UiGlassStyle.liquid(liquid);
        UiVital.throbbing(heartbeat);
        UiPalette.reset();
        UiWorldPalette.reset();
        UiAccent.clearOverrides();
        UiAccent.inkFollowsAccent(accentInk);
        dropHudAccent();

        applyAccent();
        applyGlassTheme();
        for (Map.Entry<PaletteKey, Integer> entry : colors.entrySet()) {
            entry.getKey().apply(entry.getValue());
        }
        for (Map.Entry<GlassKey, Float> entry : glass.entrySet()) {
            entry.getKey().apply(entry.getValue());
        }
        if (!glass.containsKey(GlassKey.HUD_SCALE)) {
            GlassKey.HUD_SCALE.apply(GlassKey.HUD_SCALE.fallback());
        }
    }

    public static boolean liquid() {
        return liquid;
    }

    public static boolean heartbeat() {
        return heartbeat;
    }

    public static void heartbeat(boolean value) {
        heartbeat = value;
        apply();
    }

    public static void liquid(boolean value) {
        liquid = value;
        if (liquid) mode = UiQuality.Mode.LIQUID;
        apply();
    }

    private static void applyAccent() {
        int launcher = followLauncher ? LauncherTheme.accent() : 0;
        if (launcher != 0) {
            UiAccent.set(launcher);
            return;
        }
        UiAccent.reset();
    }

    // WHY: заводское стекло серое, поэтому тема подмешивается в него долей, а не заменяет цвет:
    // WHY: полная замена съедает перепад плотности между верхом и низом и панель перестаёт читаться
    private static void applyGlassTheme() {
        if (!glassFollowsTheme || colors.containsKey(PaletteKey.GLASS)) return;

        int themed = LauncherTheme.accent();
        if (themed == 0) return;

        int top = UiTheme.mix(UiTheme.GLASS_TOP, UiTheme.withAlpha(themed, 1.0f), GLASS_TINT);
        UiPalette.glass(UiTheme.withAlpha(top, opacity(UiTheme.GLASS_TOP)),
                UiTheme.mix(top, UiTheme.BLACK, GLASS_FALL));
    }

    private static float opacity(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0f;
    }

    public static int themedGlass() {
        int themed = LauncherTheme.accent();
        if (themed == 0) return UiTheme.withAlpha(UiTheme.GLASS_TOP, 1.0f);

        return UiTheme.mix(UiTheme.withAlpha(UiTheme.GLASS_TOP, 1.0f),
                UiTheme.withAlpha(themed, 1.0f), GLASS_TINT);
    }

    public static int plainGlass() {
        return UiTheme.withAlpha(UiTheme.GLASS_TOP, 1.0f);
    }

    public static boolean glassFollowsTheme() {
        return glassFollowsTheme;
    }

    public static void glassFollowsTheme(boolean value) {
        glassFollowsTheme = value;
        if (value) colors.remove(PaletteKey.GLASS);
        apply();
    }

    public static UiMotionSet motion() {
        return motion;
    }

    public static void motion(UiMotionSet value) {
        motion = value == null ? UiMotionSet.IGNITE : value;
        UiMotion.set(motion);
    }

    public static UiSoundScheme sound() {
        return sound;
    }

    public static void sound(UiSoundScheme value) {
        sound = value == null ? UiSoundScheme.FALLBACK : value;
    }

    public static void save() {
        dirty = true;
    }

    public static void flush() {
        if (!dirty) return;

        dirty = false;
        ConfigFile.write();
    }

    public static FontShape font() {
        return font;
    }

    public static void font(FontShape value) {
        FontShape previous = font;
        font = value == null ? FontShape.FALLBACK : value;
        if (previous != font) MsdfFontSets.drop(previous);
    }

    public static UiQuality.Mode mode() {
        return mode;
    }

    public static void mode(UiQuality.Mode value) {
        mode = value == null ? UiQuality.Mode.LIQUID : value;
        apply();
    }

    public static boolean followLauncher() {
        return followLauncher;
    }

    public static void followLauncher(boolean value) {
        followLauncher = value;
        if (value) colors.remove(PaletteKey.ACCENT);
        apply();
    }

    // WHY: под акцентом уже весь текст, и худ в том числе, поэтому отдельная ручка худа обязана
    // WHY: сняться сама: иначе игрок держит включённым выключатель, который ничего не решает
    private static void dropHudAccent() {
        if (accentInk && HudConfig.hudAccentText()) HudConfig.hudAccentText(false);
    }

    public static boolean accentInk() {
        return accentInk;
    }

    public static void accentInk(boolean value) {
        accentInk = value;
        apply();
    }

    public static float glass(GlassKey key) {
        Float stored = glass.get(key);
        return stored == null ? key.fallback() : stored;
    }

    // WHY: значение пишется даже когда совпало с заводским: снимок пресета сравнивает
    // WHY: ручку с базой, и «игрок выставил дефолт» обязано отличаться от «игрок не трогал»
    public static void glass(GlassKey key, float value) {
        glass.put(key, key.clamp(value));
        apply();
    }

    // WHY: строка палитры показывает то, что нарисовано, а не заводскую константу: иначе текст,
    // WHY: перекрашенный акцентом или темой лаунчера, стоит в списке белым и сбивает с толку
    public static int color(PaletteKey key) {
        Integer stored = colors.get(key);
        return stored == null ? key.live() : stored;
    }

    public static boolean colorTouched(PaletteKey key) {
        return colors.containsKey(key);
    }

    public static void color(PaletteKey key, int argb) {
        colors.put(key, argb);
        if (key == PaletteKey.ACCENT) followLauncher = false;
        if (key == PaletteKey.GLASS) glassFollowsTheme = false;
        apply();
    }

    public static void clearColor(PaletteKey key) {
        colors.remove(key);
        apply();
    }

    public static void preset(CustomPreset chosen) {
        preset = chosen;
        retunePreset();
        apply();
    }

    static void retunePreset() {
        glass.clear();
        mode = preset.mode();
        liquid = preset.liquid();
        for (Map.Entry<GlassKey, Float> entry : preset.tuning().entrySet()) {
            glass.put(entry.getKey(), entry.getKey().clamp(entry.getValue()));
        }
    }

    public static CustomPreset preset() {
        return preset;
    }

    public static void followTheme() {
        colors.clear();
        followLauncher = true;
        apply();
    }

    public static void resetColors() {
        colors.clear();
        accentInk = false;
        apply();
    }

    public static void resetGlass() {
        preset(preset);
    }

    public static void resetHud() {
        HudLayout.resetAll();
    }

    public static void resetInterface() {
        font(FontShape.FALLBACK);
        sound(UiSoundScheme.FALLBACK);
        motion(UiMotionSet.IGNITE);
        heartbeat = false;
        for (InterfaceFlag flag : InterfaceFlag.values()) {
            flag.reset();
        }
        for (InterfaceDial dial : InterfaceDial.values()) {
            dial.reset();
        }
        apply();
    }

    public static void resetAll() {
        colors.clear();
        followLauncher = true;
        glassFollowsTheme = false;
        accentInk = false;
        HudLayout.resetAll();
        resetInterface();
        preset(CustomPreset.DEW);
    }

    static void restorePreset(CustomPreset saved) {
        preset = saved == null ? CustomPreset.DEW : saved;
    }

    static Map<GlassKey, Float> glassValues() {
        return glass;
    }

    static Map<PaletteKey, Integer> colorValues() {
        return colors;
    }

    // WHY: пресет к этому мигу уже восстановлен, поэтому файл без режима берёт режим пресета,
    // WHY: а не жидкое стекло вслепую
    static void restoreMode(UiQuality.Mode saved) {
        mode = saved == null ? preset.mode() : saved;
    }

    static void restoreLiquid(boolean value) {
        liquid = value;
    }

    static void restoreGlassValues(Map<GlassKey, Float> values) {
        glass.clear();
        glass.putAll(values);
    }

    // WHY: ручной цвет перекрывает тему, поэтому файл, где слежение включено, а цвет прописан,
    // WHY: сделал бы переключатель «под цвет темы» бездейственным: слежение сильнее записи
    static void restoreColorValues(Map<PaletteKey, Integer> values) {
        colors.clear();
        colors.putAll(values);
        if (followLauncher) colors.remove(PaletteKey.ACCENT);
        if (glassFollowsTheme) colors.remove(PaletteKey.GLASS);
    }

    static void restoreFollowLauncher(boolean value) {
        followLauncher = value;
    }

    static void restoreAccentInk(boolean value) {
        accentInk = value;
    }

    static void restoreHeartbeat(boolean value) {
        heartbeat = value;
    }

    static void restoreGlassTheme(boolean value) {
        glassFollowsTheme = value;
    }
}
