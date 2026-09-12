package com.persiki84.battlecraft.client.goggles;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.battlecraft.compat.goggles.Goggles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class GogglesForeign {
    private static final String[] KEY_FIELDS = {"toggleGrayscaleKey", "switchModeKey", "zoomKey"};

    private static boolean probed;
    private static boolean available;

    private static final float[][] THEMES = {
            {0.05f, 1.0f, 0.05f},
            {0.8f, 0.9f, 1.0f},
            {0.0f, 0.8f, 1.0f}
    };

    private static Method colorTheme;
    private static List<?> hudModules;
    private static Object channel;
    private static Method sendToServer;
    private static java.lang.reflect.Constructor<?> togglePacket;
    private static KeyMapping[] ownKeys = new KeyMapping[0];

    private GogglesForeign() {}

    public static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(Goggles.MOD_ID) && bind();
        }
        return available;
    }

    public static void silenceOwnHud() {
        if (hudModules != null && !hudModules.isEmpty()) hudModules.clear();
    }

    public static float[] colorFilter() {
        if (!available() || colorTheme == null) return THEMES[0];
        try {
            int theme = (int) colorTheme.invoke(null);
            return theme < 0 || theme >= THEMES.length ? THEMES[0] : THEMES[theme];
        } catch (Throwable error) {
            colorTheme = null;
            return THEMES[0];
        }
    }

    public static void requestToggle(boolean switchMode) {
        if (!available()) return;
        try {
            sendToServer.invoke(channel, togglePacket.newInstance(switchMode));
        } catch (Throwable error) {
            available = false;
        }
    }

    public static void silenceOwnKeys() {
        boolean changed = false;
        for (KeyMapping mapping : ownKeys) {
            if (mapping.isUnbound()) continue;
            mapping.setKey(InputConstants.UNKNOWN);
            changed = true;
        }
        if (!changed) return;

        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    private static boolean bind() {
        try {
            Class<?> network = Class.forName("dev.itsrealperson.vision_goggles.network.NetworkManager");
            Class<?> toggle = Class.forName("dev.itsrealperson.vision_goggles.network.ToggleNVGPacket");
            Class<?> channelType = Class.forName("dev.architectury.networking.NetworkChannel");

            colorTheme = Class.forName("dev.itsrealperson.vision_goggles.util.ModConfig")
                    .getMethod("getNvgColorTheme");
            channel = network.getField("INSTANCE").get(null);
            sendToServer = channelType.getMethod("sendToServer", Object.class);
            togglePacket = toggle.getConstructor(boolean.class);
            ownKeys = readOwnKeys();
            hudModules = readHudModules();
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static List<?> readHudModules() {
        try {
            Field field = Class.forName("dev.itsrealperson.vision_goggles.client.VisionHUDOverlay")
                    .getDeclaredField("MODULES");
            field.setAccessible(true);
            return (List<?>) field.get(null);
        } catch (Throwable error) {
            return null;
        }
    }

    private static KeyMapping[] readOwnKeys() {
        try {
            Class<?> keys = Class.forName("dev.itsrealperson.vision_goggles.client.ModKeyMappings");
            KeyMapping[] found = new KeyMapping[KEY_FIELDS.length];
            for (int slot = 0; slot < KEY_FIELDS.length; slot++) {
                Field field = keys.getField(KEY_FIELDS[slot]);
                found[slot] = (KeyMapping) field.get(null);
            }
            return found;
        } catch (Throwable error) {
            return new KeyMapping[0];
        }
    }
}
