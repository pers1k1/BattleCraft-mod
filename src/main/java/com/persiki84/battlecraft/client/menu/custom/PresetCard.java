package com.persiki84.battlecraft.client.menu.custom;

import com.google.gson.JsonObject;
import com.persiki84.battlecraft.client.custom.ConfigDoc;
import com.persiki84.battlecraft.client.custom.ConfigSection;
import com.persiki84.battlecraft.client.custom.CustomPreset;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.battlecraft.client.custom.UserPreset;
import com.persiki84.shared.JsonRead;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

// WHY: плитка пресета знает всё, что нужно её миниатюре, заранее: тюнинг пресета и цвет акцента
// WHY: читаются один раз при сборке карточки, а не в кадре, где их сборка стоила бы аллокаций
record PresetCard(String key, Component name, Component hint, CustomPreset builtin, UserPreset mine,
                  CustomPreset base, int accent, Map<GlassKey, Float> tuning) {
    static final String NEW_KEY = "new";
    private static final String VALUES = "values";
    private static final String PRESET = "preset";
    private static final String ACCENT = "accent";

    static String keyOf(CustomPreset preset) {
        return "b:" + preset.id();
    }

    static PresetCard of(CustomPreset preset) {
        return new PresetCard(keyOf(preset), Component.translatable(preset.translationKey()),
                Component.translatable(preset.hintKey()), preset, null, preset, 0, filled(preset.tuning()));
    }

    static PresetCard of(UserPreset preset) {
        JsonObject glass = preset.doc().section(ConfigSection.GLASS);
        CustomPreset stored = CustomPreset.byId(JsonRead.text(glass, PRESET));
        CustomPreset base = stored == null ? CustomPreset.DEW : stored;
        Map<GlassKey, Float> tuning = filled(base.tuning());
        overlay(tuning, JsonRead.object(glass, VALUES));
        return new PresetCard("u:" + preset.file(), Component.literal(preset.name()), sectionsOf(preset), null,
                preset, base, accentOf(preset), tuning);
    }

    static PresetCard blank() {
        Map<GlassKey, Float> tuning = new EnumMap<>(GlassKey.class);
        for (GlassKey key : GlassKey.values()) {
            tuning.put(key, Customization.glass(key));
        }
        return new PresetCard(NEW_KEY, Component.translatable("battlecraft.custom.mine.new"),
                Component.translatable("battlecraft.custom.mine.new.hint"), null, null, Customization.preset(), 0,
                tuning);
    }

    private static Map<GlassKey, Float> filled(Map<GlassKey, Float> tuned) {
        Map<GlassKey, Float> full = new EnumMap<>(GlassKey.class);
        for (GlassKey key : GlassKey.values()) {
            Float value = tuned.get(key);
            full.put(key, value == null ? key.fallback() : value);
        }
        return full;
    }

    private static void overlay(Map<GlassKey, Float> tuning, JsonObject values) {
        if (values == null) return;
        for (String id : values.keySet()) {
            GlassKey key = GlassKey.byId(id);
            Float stored = JsonRead.number(values, id);
            if (key != null && stored != null) tuning.put(key, key.clamp(stored));
        }
    }

    private static int accentOf(UserPreset preset) {
        JsonObject values = JsonRead.object(preset.doc().section(ConfigSection.COLORS), VALUES);
        Integer argb = ConfigDoc.parseColor(JsonRead.text(values, ACCENT));
        return argb == null ? 0 : argb;
    }

    private static Component sectionsOf(UserPreset preset) {
        Component joined = Component.empty();
        boolean first = true;
        for (ConfigSection section : ConfigSection.values()) {
            if (!preset.sections().contains(section)) continue;
            if (!first) joined = joined.copy().append(", ");
            joined = joined.copy().append(Component.translatable(section.translationKey()));
            first = false;
        }
        return joined;
    }

    float value(GlassKey key) {
        Float stored = tuning.get(key);
        return stored == null ? key.fallback() : stored;
    }

    boolean fresh() {
        return NEW_KEY.equals(key);
    }

    boolean current() {
        return builtin != null && Customization.preset() == builtin;
    }
}
