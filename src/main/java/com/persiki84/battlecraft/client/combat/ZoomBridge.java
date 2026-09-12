package com.persiki84.battlecraft.client.combat;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;

final class ZoomBridge {
    private static final String MOD_ID = "justzoom";

    private static boolean probed;
    private static KeyMapping zoomKey;

    private ZoomBridge() {}

    static void suppress() {
        KeyMapping key = key();
        if (key == null || !key.isDown()) return;
        key.setDown(false);
    }

    private static KeyMapping key() {
        if (probed) return zoomKey;

        probed = true;
        if (!ModList.get().isLoaded(MOD_ID)) return null;
        try {
            Field field = Class.forName("de.keksuccino.justzoom.KeyMappings").getField("KEY_TOGGLE_ZOOM");
            zoomKey = (KeyMapping) field.get(null);
        } catch (Throwable error) {
            zoomKey = null;
        }
        return zoomKey;
    }
}
