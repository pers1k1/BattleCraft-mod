package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.Locale;
import java.util.Map;

public final class KeyLabel {
    private static final String KEYBOARD_PREFIX = "key.keyboard.";
    private static final String MOUSE_PREFIX = "key.mouse.";
    private static final String UNBOUND = "---";

    private static final Map<String, String> MOUSE_NAMES = Map.of(
            "left", "LMB",
            "right", "RMB",
            "middle", "MMB"
    );

    private KeyLabel() {}

    public static String of(KeyMapping mapping) {
        return mapping == null ? UNBOUND : of(mapping.getKey());
    }

    public static String of(InputConstants.Key key) {
        if (key == null || key.getValue() == InputConstants.UNKNOWN.getValue()) return UNBOUND;

        String name = key.getName();
        if (name.startsWith(MOUSE_PREFIX)) return mouseName(name.substring(MOUSE_PREFIX.length()));
        if (name.startsWith(KEYBOARD_PREFIX)) return spaced(name.substring(KEYBOARD_PREFIX.length()));
        return spaced(name);
    }

    private static String mouseName(String button) {
        String known = MOUSE_NAMES.get(button);
        return known != null ? known : "MOUSE " + spaced(button);
    }

    private static String spaced(String raw) {
        return raw.replace('.', ' ').toUpperCase(Locale.ROOT);
    }
}
