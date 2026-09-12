package com.persiki84.battlecraft.client.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class SuperbCombat {
    private static final String MOD_ID = "superbwarfare";

    private static boolean probed;
    private static boolean available;

    private static Class<?> gunItem;
    private static Object companion;
    private static Method from;
    private static Field reloadField;
    private static Method reloadState;
    private static Object notReloading;
    private static Field zoomField;
    private static Field vehicleZoomField;

    private SuperbCombat() {}

    static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    static boolean aiming(LocalPlayer player) {
        if (!available()) return false;
        try {
            if (vehicleZoomField.getBoolean(null) && player.getVehicle() != null) return true;
            return zoomField.getBoolean(null) && armed(player);
        } catch (Throwable error) {
            available = false;
            return false;
        }
    }

    static boolean reloading(LocalPlayer player) {
        if (!available()) return false;
        try {
            Object data = gunData(player.getMainHandItem());
            return data != null && reloadState.invoke(reloadField.get(data)) != notReloading;
        } catch (Throwable error) {
            available = false;
            return false;
        }
    }

    static void cancelAim() {
        if (!available()) return;
        try {
            zoomField.setBoolean(null, false);
            vehicleZoomField.setBoolean(null, false);
        } catch (Throwable error) {
            available = false;
        }
    }

    static boolean armed(LocalPlayer player) {
        return available() && gunItem.isInstance(player.getMainHandItem().getItem());
    }

    private static Object gunData(ItemStack stack) throws Exception {
        if (stack.isEmpty() || !gunItem.isInstance(stack.getItem())) return null;
        return from.invoke(companion, stack);
    }

    private static boolean bind() {
        try {
            gunItem = Class.forName("com.atsuishio.superbwarfare.item.gun.GunItem");
            Class<?> gunData = Class.forName("com.atsuishio.superbwarfare.data.gun.GunData");
            Class<?> companionType = Class.forName("com.atsuishio.superbwarfare.data.gun.GunData$Companion");
            Class<?> reload = Class.forName("com.atsuishio.superbwarfare.data.gun.subdata.Reload");
            Class<?> state = Class.forName("com.atsuishio.superbwarfare.data.gun.value.ReloadState");
            Class<?> events = Class.forName("com.atsuishio.superbwarfare.event.ClientEventHandler");

            companion = gunData.getField("Companion").get(null);
            from = companionType.getMethod("from", ItemStack.class);
            reloadField = gunData.getField("reload");
            reloadState = reload.getMethod("state");
            notReloading = state.getField("NOT_RELOADING").get(null);
            zoomField = events.getField("zoom");
            vehicleZoomField = events.getField("zoomVehicle");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
