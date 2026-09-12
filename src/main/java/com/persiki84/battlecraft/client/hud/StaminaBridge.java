package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.UiAnim;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class StaminaBridge {
    private static final float SUPERB_MAX = 100.0f;

    private static Reading current;

    private static boolean parcoolProbed;
    private static boolean parcoolReady;
    private static Method parcoolGet;
    private static Method parcoolValue;
    private static Method parcoolMax;
    private static Method parcoolExhausted;

    private static boolean superbProbed;
    private static boolean superbReady;
    private static Field superbValue;
    private static Field superbExhausted;

    private StaminaBridge() {}

    public static Reading read(Player player) {
        Reading reading = parcool(player);
        return reading != null ? reading : superb();
    }

    public static void poll(Player player) {
        current = read(player);
    }

    public static void clear() {
        current = null;
    }

    public static Reading current() {
        return current;
    }

    public static float exertion() {
        Reading reading = current;
        if (reading == null) return 0.0f;
        if (reading.exhausted()) return 1.0f;
        return UiAnim.clamp01(1.0f - reading.ratio());
    }

    private static Reading parcool(Player player) {
        if (!parcoolProbed) {
            parcoolProbed = true;
            parcoolReady = ModList.get().isLoaded("parcool") && bindParcool();
        }
        if (!parcoolReady) return null;
        try {
            Object stamina = parcoolGet.invoke(null, player);
            if (stamina == null) return null;
            int max = (int) parcoolMax.invoke(stamina);
            if (max <= 0) return null;
            int value = (int) parcoolValue.invoke(stamina);
            return new Reading(value / (float) max, value, (boolean) parcoolExhausted.invoke(stamina));
        } catch (Throwable error) {
            parcoolReady = false;
            return null;
        }
    }

    private static Reading superb() {
        if (!superbProbed) {
            superbProbed = true;
            superbReady = ModList.get().isLoaded("superbwarfare") && bindSuperb();
        }
        if (!superbReady) return null;
        try {
            float value = superbValue.getFloat(null);
            return new Reading(value / SUPERB_MAX, Math.round(value), superbExhausted.getBoolean(null));
        } catch (Throwable error) {
            superbReady = false;
            return null;
        }
    }

    private static boolean bindParcool() {
        try {
            Class<?> api = Class.forName("com.alrex.parcool.api.Stamina");
            parcoolGet = api.getMethod("get", Player.class);
            parcoolValue = api.getMethod("getValue");
            parcoolMax = api.getMethod("getMaxValue");
            parcoolExhausted = api.getMethod("isExhausted");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static boolean bindSuperb() {
        try {
            Class<?> handler = Class.forName("com.atsuishio.superbwarfare.event.ClientEventHandler");
            superbValue = handler.getField("stamina");
            superbExhausted = handler.getField("exhaustion");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    public record Reading(float ratio, int value, boolean exhausted) {}
}
