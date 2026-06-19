package com.persiki84.shared;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LangHelper {

    private LangHelper() {}

    private static final Map<String, String> keys = new LinkedHashMap<>();

    public static void register(String key, String defaultValue) {
        keys.put(key, defaultValue);
    }

    public static Map<String, String> getKeys() {
        return Map.copyOf(keys);
    }

    public static void clear() {
        keys.clear();
    }
}
