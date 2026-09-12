package com.persiki84.battlecraft.client.custom;

import com.google.gson.JsonObject;
import com.persiki84.shared.JsonRead;

import java.util.EnumSet;
import java.util.Set;

public final class ConfigDoc {
    public static final String FORMAT = "battlecraft-config";
    public static final int VERSION = 3;
    public static final String KIND_CONFIG = "config";
    public static final String KIND_PRESET = "preset";

    private static final String FORMAT_KEY = "format";
    private static final String VERSION_KEY = "version";
    private static final String KIND_KEY = "kind";
    private static final String NAME_KEY = "name";
    private static final String CREATED_KEY = "created";

    private final JsonObject root;

    private ConfigDoc(JsonObject root) {
        this.root = root;
    }

    public static ConfigDoc fresh(String kind) {
        JsonObject made = new JsonObject();
        made.addProperty(FORMAT_KEY, FORMAT);
        made.addProperty(VERSION_KEY, VERSION);
        made.addProperty(KIND_KEY, kind);
        return new ConfigDoc(made);
    }

    public static ConfigDoc wrap(JsonObject parsed) {
        return new ConfigDoc(parsed);
    }

    public JsonObject root() {
        return root;
    }

    public String format() {
        return JsonRead.text(root, FORMAT_KEY);
    }

    public boolean ours() {
        return FORMAT.equals(format());
    }

    public int version() {
        Float stored = JsonRead.number(root, VERSION_KEY);
        return stored == null ? 0 : Math.round(stored);
    }

    public void version(int value) {
        root.addProperty(VERSION_KEY, value);
    }
    public String name() {
        return JsonRead.text(root, NAME_KEY);
    }

    public void name(String value) {
        root.addProperty(NAME_KEY, value);
    }

    public String created() {
        return JsonRead.text(root, CREATED_KEY);
    }

    public void created(String value) {
        root.addProperty(CREATED_KEY, value);
    }

    public JsonObject section(ConfigSection section) {
        return JsonRead.object(root, section.id());
    }

    // WHY: секция открывается на месте, а не пересоздаётся: незнакомые ключи внутри неё
    // WHY: принадлежат другой версии мода и обязаны пережить запись из этой
    public JsonObject openSection(ConfigSection section) {
        JsonObject body = section(section);
        if (body != null) return body;

        JsonObject made = new JsonObject();
        root.add(section.id(), made);
        return made;
    }

    public boolean has(ConfigSection section) {
        return section(section) != null;
    }

    public Set<ConfigSection> sections() {
        Set<ConfigSection> found = EnumSet.noneOf(ConfigSection.class);
        for (ConfigSection section : ConfigSection.values()) {
            if (has(section)) found.add(section);
        }
        return found;
    }

    public static String formatColor(int argb) {
        return String.format("#%08X", argb);
    }

    public static Integer parseColor(String hex) {
        if (hex == null) return null;

        String digits = hex.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() == 6) digits = "FF" + digits;
        if (digits.length() != 8) return null;

        try {
            return (int) Long.parseLong(digits, 16);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
