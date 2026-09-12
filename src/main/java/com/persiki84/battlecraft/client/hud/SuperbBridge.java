package com.persiki84.battlecraft.client.hud;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class SuperbBridge {
    private static final String MOD_ID = "superbwarfare";

    private static boolean probed;
    private static boolean available;
    private static boolean vehicleProbed;
    private static Class<?> vehicleType;

    private static Class<?> gunItem;
    private static Object companion;
    private static Method from;
    private static Method countBackupAmmo;
    private static Method infiniteBackupAmmo;
    private static Method compute;
    private static Method magazine;
    private static Field ammoField;
    private static Field fireModeField;
    private static Method intGet;
    private static Method enumGet;

    private SuperbBridge() {}

    static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    static boolean riding(Player player) {
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        if (vehicleType == null) {
            if (vehicleProbed) return false;
            vehicleProbed = true;
            vehicleType = findVehicleType();
            if (vehicleType == null) return false;
        }
        return player.getVehicle() != null && vehicleType.isInstance(player.getVehicle());
    }

    private static Class<?> findVehicleType() {
        try {
            return Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
        } catch (Throwable error) {
            return null;
        }
    }

    static GunBridge.Gun read(ItemStack stack, Inventory inventory) {
        if (!available() || stack.isEmpty() || !gunItem.isInstance(stack.getItem())) return null;
        try {
            Object data = from.invoke(companion, stack);
            if (data == null) return null;

            int loaded = (int) intGet.invoke(ammoField.get(data));
            int size = (int) magazine.invoke(compute.invoke(data));
            int spare = infiniteSpare(data, inventory)
                    ? GunBridge.SPARE_INFINITE
                    : (int) countBackupAmmo.invoke(data, inventory.player);

            Object mode = enumGet.invoke(fireModeField.get(data));
            return new GunBridge.Gun(loaded, size, spare, -1, mode == null ? "" : mode.toString());
        } catch (Throwable error) {
            available = false;
            return null;
        }
    }

    private static boolean infiniteSpare(Object data, Inventory inventory) throws Exception {
        return inventory.player.isCreative()
                || (boolean) infiniteBackupAmmo.invoke(data, inventory.player);
    }

    private static boolean bind() {
        try {
            gunItem = Class.forName("com.atsuishio.superbwarfare.item.gun.GunItem");
            Class<?> gunData = Class.forName("com.atsuishio.superbwarfare.data.gun.GunData");
            Class<?> defaults = Class.forName("com.atsuishio.superbwarfare.data.gun.DefaultGunData");
            Class<?> intValue = Class.forName("com.atsuishio.superbwarfare.data.gun.value.IntValue");
            Class<?> enumValue = Class.forName("com.atsuishio.superbwarfare.data.gun.value.StringEnumValue");
            Class<?> companionType = Class.forName("com.atsuishio.superbwarfare.data.gun.GunData$Companion");

            companion = gunData.getField("Companion").get(null);
            from = companionType.getMethod("from", ItemStack.class);
            countBackupAmmo = gunData.getMethod("countBackupAmmo", net.minecraft.world.entity.Entity.class);
            infiniteBackupAmmo = gunData.getMethod("hasInfiniteBackupAmmo", net.minecraft.world.entity.Entity.class);
            compute = gunData.getMethod("compute");
            magazine = defaults.getMethod("getMagazine");
            ammoField = gunData.getField("ammo");
            fireModeField = gunData.getField("fireMode");
            intGet = intValue.getMethod("get");
            enumGet = enumValue.getMethod("get");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
