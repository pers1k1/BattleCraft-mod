package com.persiki84.shared;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

// WHY: gson бросает на значении не того типа, а разбор файла настроек обязан терять только
// WHY: испорченное поле: одно битое число не имеет права отменить весь остальной файл
public final class JsonRead {

    private JsonRead() {}

    public static JsonObject object(JsonObject holder, String key) {
        JsonElement element = holder == null ? null : holder.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static JsonObject open(JsonObject holder, String key) {
        JsonObject body = object(holder, key);
        if (body != null) return body;

        JsonObject made = new JsonObject();
        holder.add(key, made);
        return made;
    }

    public static String text(JsonObject holder, String key) {
        JsonPrimitive primitive = primitive(holder, key);
        return primitive != null && primitive.isString() ? primitive.getAsString() : null;
    }

    public static Float number(JsonObject holder, String key) {
        JsonPrimitive primitive = primitive(holder, key);
        if (primitive == null || (!primitive.isNumber() && !primitive.isString())) return null;

        try {
            return primitive.getAsFloat();
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    public static Boolean flag(JsonObject holder, String key) {
        JsonPrimitive primitive = primitive(holder, key);
        if (primitive == null) return null;
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (!primitive.isString()) return null;

        String written = primitive.getAsString().trim();
        if (written.equalsIgnoreCase("true")) return Boolean.TRUE;
        return written.equalsIgnoreCase("false") ? Boolean.FALSE : null;
    }

    private static JsonPrimitive primitive(JsonObject holder, String key) {
        JsonElement element = holder == null ? null : holder.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
    }
}
