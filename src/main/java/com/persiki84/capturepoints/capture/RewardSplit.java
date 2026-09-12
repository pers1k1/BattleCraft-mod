package com.persiki84.capturepoints.capture;

import java.util.Locale;

public enum RewardSplit {
    EACH,
    SHARE,
    MERIT;

    public static final RewardSplit DEFAULT = EACH;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "capturepoints.split." + id();
    }

    public static RewardSplit byId(String id) {
        if (id == null || id.isEmpty()) return DEFAULT;

        for (RewardSplit split : values()) {
            if (split.id().equalsIgnoreCase(id)) return split;
        }
        return DEFAULT;
    }
}
