package com.persiki84.battlecraft.client.custom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class ConfigMigrations {

    private ConfigMigrations() {}

    // WHY: шаги идут цепочкой от версии файла к текущей, поэтому пропущенные версии
    // WHY: доезжают сами: файл 2026 года читается после любого числа обновлений
    static void run(ConfigDoc doc) {
        if (doc.version() >= ConfigDoc.VERSION) return;

        doc.version(ConfigDoc.VERSION);
    }

    static ConfigDoc fromLegacy(JsonObject flat) {
        ConfigDoc doc = ConfigDoc.fresh(ConfigDoc.KIND_CONFIG);
        legacyGlass(flat, doc.openSection(ConfigSection.GLASS));
        legacyColors(flat, doc.openSection(ConfigSection.COLORS));
        legacyInterface(flat, doc.openSection(ConfigSection.INTERFACE));
        move(flat, "hud", doc.root(), ConfigSection.HUD.id());
        return doc;
    }

    // WHY: до третьей версии файл не хранил снимок пресета, поэтому отличить правку игрока
    // WHY: от прежнего значения пресета нечем: тюнинг раскатывается заново один раз,
    // WHY: дальше правки бережёт секция base
    private static void legacyGlass(JsonObject flat, JsonObject body) {
        move(flat, "preset", body, "preset");
        move(flat, "mode", body, "mode");
        move(flat, "liquid", body, "liquid");
        move(flat, "glassFollowsTheme", body, "followsTheme");
    }

    private static void legacyColors(JsonObject flat, JsonObject body) {
        move(flat, "followLauncher", body, "followLauncher");
        move(flat, "colors", body, "values");
    }

    private static void legacyInterface(JsonObject flat, JsonObject body) {
        move(flat, "font", body, "font");
        move(flat, "uiSound", body, "uiSound");
        move(flat, "uiMotion", body, "uiMotion");
        move(flat, "heartbeat", body, "heartbeat");
    }

    private static void move(JsonObject from, String key, JsonObject to, String target) {
        JsonElement value = from.get(key);
        if (value != null) to.add(target, value);
    }
}
