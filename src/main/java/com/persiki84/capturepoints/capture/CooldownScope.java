package com.persiki84.capturepoints.capture;

import java.util.Locale;

public enum CooldownScope {
    POINT,
    ALL;

    public static final CooldownScope DEFAULT = POINT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "capturepoints.scope." + id();
    }

    public static CooldownScope byId(String id) {
        if (id == null || id.isEmpty()) return DEFAULT;

        for (CooldownScope scope : values()) {
            if (scope.id().equalsIgnoreCase(id)) return scope;
        }
        return DEFAULT;
    }
}
