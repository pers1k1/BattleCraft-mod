package com.persiki84.battlecraft.mode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.persiki84.shared.JsonRead;

// WHY: один формат режима на файл режимов и на снимок в match.dat. Чтение накладывает известные
// WHY: ключи на заводские значения и зажимает их; запись кладёт известные ключи поверх исходного
// WHY: объекта, поэтому незнакомые ключи (правка руками, поле будущей версии) не теряются
final class ModeJson {
    private ModeJson() {}

    static MatchMode read(ModeId id, JsonObject source) {
        MatchMode mode = MatchMode.defaults(id);
        Boolean quarries = JsonRead.flag(source, "quarries");
        if (quarries != null) mode.quarries(quarries);
        mode.pointScore(whole(source, "pointScore", mode.pointScore()));
        mode.killScore(whole(source, "killScore", mode.killScore()));
        mode.vehicleScore(whole(source, "vehicleScore", mode.vehicleScore()));
        mode.transportScore(whole(source, "transportScore", mode.transportScore()));
        mode.scoreTarget(whole(source, "scoreTarget", mode.scoreTarget()));
        mode.capMinutes(whole(source, "capMinutes", mode.capMinutes()));
        mode.suddenMargin(whole(source, "suddenMargin", mode.suddenMargin()));
        mode.suddenMinutes(whole(source, "suddenMinutes", mode.suddenMinutes()));
        mode.startMoney(money(source, mode.startMoney()));
        mode.description(JsonRead.text(source, "description"));
        readKit(mode, source.get("kit"));
        return mode;
    }

    private static int whole(JsonObject source, String key, int fallback) {
        Float value = JsonRead.number(source, key);
        return value == null ? fallback : Math.round(value);
    }

    // WHY: JsonRead.number отдаёт float, а он точен лишь до 2^24: деньги читаются целым long
    private static long money(JsonObject source, long fallback) {
        JsonElement value = source.get("startMoney");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return value.getAsLong();
        } catch (NumberFormatException broken) {
            return fallback;
        }
    }

    private static void readKit(MatchMode mode, JsonElement listed) {
        if (listed == null || !listed.isJsonArray()) return;
        for (JsonElement row : listed.getAsJsonArray()) {
            if (!row.isJsonObject()) continue;
            JsonObject entry = row.getAsJsonObject();
            String item = JsonRead.text(entry, "item");
            if (item == null || item.length() > ModeLimits.TEXT_MAX) continue;
            String tag = JsonRead.text(entry, "tag");
            if (tag != null && tag.length() > ModeLimits.TEXT_MAX) continue;
            mode.addKit(new KitEntry(item, tag, whole(entry, "count", 1)));
        }
    }

    static JsonObject write(MatchMode mode, JsonObject original) {
        JsonObject target = original == null ? new JsonObject() : original.deepCopy();
        target.addProperty("quarries", mode.quarries());
        target.addProperty("pointScore", mode.pointScore());
        target.addProperty("killScore", mode.killScore());
        target.addProperty("vehicleScore", mode.vehicleScore());
        target.addProperty("transportScore", mode.transportScore());
        target.addProperty("scoreTarget", mode.scoreTarget());
        target.addProperty("capMinutes", mode.capMinutes());
        target.addProperty("suddenMargin", mode.suddenMargin());
        target.addProperty("suddenMinutes", mode.suddenMinutes());
        target.addProperty("startMoney", mode.startMoney());
        target.addProperty("description", mode.description());
        target.add("kit", kit(mode));
        return target;
    }

    private static JsonArray kit(MatchMode mode) {
        JsonArray rows = new JsonArray();
        for (KitEntry entry : mode.kit()) {
            JsonObject row = new JsonObject();
            row.addProperty("item", entry.itemId());
            if (!entry.tag().isEmpty()) row.addProperty("tag", entry.tag());
            row.addProperty("count", entry.count());
            rows.add(row);
        }
        return rows;
    }
}
