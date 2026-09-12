package com.persiki84.battlecraft.client.custom;

import com.google.gson.JsonObject;
import com.persiki84.shared.JsonRead;
import com.persiki84.shared.client.font.FontShape;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiQuality;
import com.persiki84.shared.client.ui.UiSoundScheme;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

final class ConfigCodec {
    private static final float EPSILON = 1.0E-5f;
    private static final String VALUES = "values";
    private static final String BASE = "base";
    private static final String PRESET = "preset";
    private static final String MODE = "mode";
    private static final String LIQUID = "liquid";
    private static final String FOLLOWS_THEME = "followsTheme";
    private static final String FOLLOW_LAUNCHER = "followLauncher";
    private static final String ACCENT_INK = "accentInk";
    private static final String FONT = "font";
    private static final String SOUND = "uiSound";
    private static final String MOTION = "uiMotion";
    private static final String HEARTBEAT = "heartbeat";

    private ConfigCodec() {}

    static void applyAll(ConfigDoc doc) {
        for (ConfigSection section : ConfigSection.values()) {
            applySection(section, doc.section(section));
        }
    }

    static void applyPresent(ConfigDoc doc) {
        for (ConfigSection section : doc.sections()) {
            applySection(section, doc.section(section));
        }
    }

    private static void applySection(ConfigSection section, JsonObject body) {
        switch (section) {
            case GLASS -> applyGlass(body);
            case COLORS -> applyColors(body);
            case INTERFACE -> applyInterface(body);
            case HUD -> applyHud(body);
        }
    }

    private static void applyGlass(JsonObject body) {
        CustomPreset chosen = CustomPreset.byId(JsonRead.text(body, PRESET));
        Customization.restorePreset(chosen == null ? CustomPreset.DEW : chosen);
        Customization.restoreMode(UiQuality.Mode.byId(JsonRead.text(body, MODE)));

        Boolean liquid = JsonRead.flag(body, LIQUID);
        Customization.restoreLiquid(liquid == null ? Customization.preset().liquid() : liquid);
        Boolean themed = JsonRead.flag(body, FOLLOWS_THEME);
        Customization.restoreGlassTheme(themed != null && themed);
        Customization.restoreGlassValues(tuning(body));
    }

    private static Map<GlassKey, Float> tuning(JsonObject body) {
        Map<GlassKey, Float> tuned = presetTuning();
        JsonObject values = JsonRead.object(body, VALUES);
        if (values == null) return tuned;

        JsonObject base = JsonRead.object(body, BASE);
        for (String id : values.keySet()) {
            GlassKey key = GlassKey.byId(id);
            Float stored = JsonRead.number(values, id);
            if (key == null || stored == null || fromPreset(base, id, stored)) continue;
            tuned.put(key, key.clamp(stored));
        }
        return tuned;
    }

    // WHY: значение, совпавшее со снимком пресета на момент записи, принадлежит пресету, и новый
    // WHY: тюнинг обязан его заменить; всё, что от снимка отличается, это правка игрока и она живёт
    private static boolean fromPreset(JsonObject base, String id, float stored) {
        Float origin = JsonRead.number(base, id);
        return origin != null && Math.abs(origin - stored) < EPSILON;
    }

    private static Map<GlassKey, Float> presetTuning() {
        Map<GlassKey, Float> tuned = new EnumMap<>(GlassKey.class);
        for (Map.Entry<GlassKey, Float> entry : Customization.preset().tuning().entrySet()) {
            tuned.put(entry.getKey(), entry.getKey().clamp(entry.getValue()));
        }
        return tuned;
    }

    private static void applyColors(JsonObject body) {
        Boolean follow = JsonRead.flag(body, FOLLOW_LAUNCHER);
        Customization.restoreFollowLauncher(follow == null || follow);
        Boolean inked = JsonRead.flag(body, ACCENT_INK);
        Customization.restoreAccentInk(inked != null && inked);

        Map<PaletteKey, Integer> picked = new EnumMap<>(PaletteKey.class);
        JsonObject values = JsonRead.object(body, VALUES);
        if (values != null) {
            for (String id : values.keySet()) {
                PaletteKey key = PaletteKey.byId(id);
                Integer argb = ConfigDoc.parseColor(JsonRead.text(values, id));
                if (key != null && argb != null) picked.put(key, argb);
            }
        }
        Customization.restoreColorValues(picked);
    }

    private static void applyInterface(JsonObject body) {
        FontShape font = FontShape.byId(JsonRead.text(body, FONT));
        Customization.font(font == null ? FontShape.FALLBACK : font);
        UiSoundScheme sound = UiSoundScheme.byId(JsonRead.text(body, SOUND));
        Customization.sound(sound == null ? UiSoundScheme.FALLBACK : sound);
        UiMotionSet motion = UiMotionSet.byId(JsonRead.text(body, MOTION));
        Customization.motion(motion == null ? UiMotionSet.IGNITE : motion);
        Boolean beating = JsonRead.flag(body, HEARTBEAT);
        Customization.restoreHeartbeat(beating != null && beating);
        applyKnobs(JsonRead.object(body, VALUES));
    }

    // WHY: живой конфиг ручки toml не пишет, поэтому их отсутствие это «не трогать файл Forge»,
    // WHY: а не «поставить дефолты»; пресет их всегда перечисляет, и там пропуск это дефолт
    private static void applyKnobs(JsonObject values) {
        if (values == null) return;

        for (InterfaceFlag flag : InterfaceFlag.values()) {
            Boolean stored = JsonRead.flag(values, flag.id());
            flag.set(stored == null ? flag.fallback() : stored);
        }
        for (InterfaceDial dial : InterfaceDial.values()) {
            Float stored = JsonRead.number(values, dial.id());
            dial.set(stored == null ? dial.fallback() : stored);
        }
    }

    private static void applyHud(JsonObject body) {
        HudLayout.resetAll();
        HudStore.read(body);
    }

    static void captureAll(ConfigDoc doc) {
        captureGlass(doc.openSection(ConfigSection.GLASS));
        captureColors(doc.openSection(ConfigSection.COLORS));
        captureInterface(doc.openSection(ConfigSection.INTERFACE));
        HudStore.write(doc.openSection(ConfigSection.HUD));
    }

    static ConfigDoc snapshot(String name, Set<ConfigSection> wanted) {
        ConfigDoc doc = ConfigDoc.fresh(ConfigDoc.KIND_PRESET);
        doc.name(name);
        doc.created(LocalDate.now().toString());

        if (wanted.contains(ConfigSection.GLASS)) captureGlass(doc.openSection(ConfigSection.GLASS));
        if (wanted.contains(ConfigSection.COLORS)) captureColors(doc.openSection(ConfigSection.COLORS));
        if (wanted.contains(ConfigSection.INTERFACE)) {
            JsonObject body = doc.openSection(ConfigSection.INTERFACE);
            captureInterface(body);
            captureKnobs(body);
        }
        if (wanted.contains(ConfigSection.HUD)) HudStore.write(doc.openSection(ConfigSection.HUD));
        return doc;
    }

    private static void captureGlass(JsonObject body) {
        body.addProperty(PRESET, Customization.preset().id());
        body.addProperty(MODE, Customization.mode().id());
        body.addProperty(LIQUID, Customization.liquid());
        body.addProperty(FOLLOWS_THEME, Customization.glassFollowsTheme());
        writeGlass(JsonRead.open(body, VALUES), Customization.glassValues());
        writeGlass(JsonRead.open(body, BASE), presetTuning());
    }

    // WHY: обходятся знакомые ключи, а не keySet цели: ручки чужой версии мода лежат там же
    // WHY: и обязаны пережить запись из этой
    private static void writeGlass(JsonObject target, Map<GlassKey, Float> values) {
        for (GlassKey key : GlassKey.values()) {
            Float value = values.get(key);
            if (value == null) {
                target.remove(key.id());
            } else {
                target.addProperty(key.id(), value);
            }
        }
    }

    private static void captureColors(JsonObject body) {
        body.addProperty(FOLLOW_LAUNCHER, Customization.followLauncher());
        body.addProperty(ACCENT_INK, Customization.accentInk());

        JsonObject target = JsonRead.open(body, VALUES);
        Map<PaletteKey, Integer> values = Customization.colorValues();
        for (PaletteKey key : PaletteKey.values()) {
            Integer argb = values.get(key);
            if (argb == null) {
                target.remove(key.id());
            } else {
                target.addProperty(key.id(), ConfigDoc.formatColor(argb));
            }
        }
    }

    private static void captureInterface(JsonObject body) {
        body.addProperty(FONT, Customization.font().id());
        body.addProperty(SOUND, Customization.sound().id());
        body.addProperty(MOTION, Customization.motion().id());
        body.addProperty(HEARTBEAT, Customization.heartbeat());
    }

    private static void captureKnobs(JsonObject body) {
        JsonObject target = JsonRead.open(body, VALUES);
        for (InterfaceFlag flag : InterfaceFlag.values()) {
            target.addProperty(flag.id(), flag.get());
        }
        for (InterfaceDial dial : InterfaceDial.values()) {
            target.addProperty(dial.id(), dial.get());
        }
    }
}
