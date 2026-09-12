package com.persiki84.battlecraft.client.custom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.persiki84.shared.JsonRead;

final class HudStore {

    private HudStore() {}

    static void read(JsonObject section) {
        if (section == null) return;

        for (HudSlot slot : HudSlot.values()) {
            JsonObject stored = JsonRead.object(section, slot.id());
            if (stored != null) readSlot(HudLayout.of(slot), stored);
        }
    }

    private static void readSlot(HudPlacement placement, JsonObject stored) {
        HudAnchor anchor = HudAnchor.byId(JsonRead.text(stored, "anchor"));
        if (anchor != null) placement.anchor(anchor);

        placement.offset(number(stored, "x", placement.offsetX()), number(stored, "y", placement.offsetY()));
        placement.scale(number(stored, "scale", placement.scale()));
        placement.alpha(number(stored, "alpha", placement.alpha()));
        placement.dim(number(stored, "dim", placement.dim()));
        placement.corner(number(stored, "corner", placement.corner()));

        Boolean visible = JsonRead.flag(stored, "visible");
        if (visible != null) placement.visible(visible);

        placement.tint(ConfigDoc.parseColor(JsonRead.text(stored, "tint")));
        if (stored.has("dock")) {
            placement.dock(readDock(stored.get("dock")));
            return;
        }
        if (moved(placement)) placement.dock(null);
    }

    // WHY: файл до появления стыковки поля dock не знал, и сдвинутый игроком элемент
    // WHY: обязан потерять наклон к соседу, иначе его утащит обратно к нему
    private static boolean moved(HudPlacement placement) {
        return placement.offsetX() != 0.0f || placement.offsetY() != 0.0f;
    }

    private static HudDock readDock(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;

        JsonObject stored = element.getAsJsonObject();
        HudSlot target = HudSlot.byId(JsonRead.text(stored, "target"));
        HudSide side = HudSide.byId(JsonRead.text(stored, "side"));
        if (target == null || side == null) return null;

        return new HudDock(target, side, HudAlign.byId(JsonRead.text(stored, "align")));
    }

    private static float number(JsonObject stored, String key, float fallback) {
        Float value = JsonRead.number(stored, key);
        return value == null ? fallback : value;
    }

    // WHY: слоты чужой версии мода лежат в этой же секции и остаются нетронутыми,
    // WHY: поэтому обходятся только знакомые, а не keySet целиком
    static void write(JsonObject section) {
        for (HudSlot slot : HudSlot.values()) {
            HudPlacement placement = HudLayout.of(slot);
            if (placement.plain()) {
                section.remove(slot.id());
            } else {
                section.add(slot.id(), writeSlot(placement));
            }
        }
    }

    private static JsonObject writeSlot(HudPlacement placement) {
        JsonObject stored = new JsonObject();
        stored.addProperty("anchor", placement.anchor().id());
        stored.addProperty("x", placement.offsetX());
        stored.addProperty("y", placement.offsetY());
        stored.addProperty("scale", placement.scale());
        stored.addProperty("alpha", placement.alpha());
        stored.addProperty("visible", placement.visible());
        stored.addProperty("dim", placement.dim());
        stored.addProperty("corner", placement.corner());
        if (placement.tint() != null) stored.addProperty("tint", ConfigDoc.formatColor(placement.tint()));
        stored.add("dock", placement.dock() == null
                ? new JsonPrimitive("none") : writeDock(placement.dock()));
        return stored;
    }

    private static JsonObject writeDock(HudDock dock) {
        JsonObject stored = new JsonObject();
        stored.addProperty("target", dock.target().id());
        stored.addProperty("side", dock.side().id());
        stored.addProperty("align", dock.align().id());
        return stored;
    }
}
