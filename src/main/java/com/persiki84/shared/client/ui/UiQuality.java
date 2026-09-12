package com.persiki84.shared.client.ui;

import java.util.Locale;

public final class UiQuality {
    public enum Mode {
        LIQUID,
        BLUR,
        PLAIN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Mode byId(String id) {
            if (id == null) return null;
            for (Mode mode : values()) {
                if (mode.id().equalsIgnoreCase(id.trim())) return mode;
            }
            return null;
        }
    }

    private static Mode mode = Mode.LIQUID;

    private UiQuality() {}

    public static void use(Mode value) {
        mode = value == null ? Mode.LIQUID : value;
    }

    public static boolean refracting() {
        return mode == Mode.LIQUID;
    }

    public static boolean blurring() {
        return mode != Mode.PLAIN;
    }
}
