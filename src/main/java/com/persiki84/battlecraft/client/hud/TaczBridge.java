package com.persiki84.battlecraft.client.hud;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

final class TaczBridge {
    private static final String MOD_ID = "tacz";
    private static final String UNKNOWN_FIRE_MODE = "UNKNOWN";

    private static boolean probed;
    private static boolean available;

    private static Class<?> ammoItem;
    private static Class<?> ammoBoxItem;
    private static Object openBolt;

    private static Method getIGunOrNull;
    private static Method getGunId;
    private static Method getCurrentAmmoCount;
    private static Method hasBulletInBarrel;
    private static Method getFireMode;
    private static Method useDummyAmmo;
    private static Method useInventoryAmmo;
    private static Method getDummyAmmoAmount;
    private static Method getMaxDummyAmmoAmount;
    private static Method getCommonGunIndex;
    private static Method getGunData;
    private static Method getBolt;
    private static Method getReloadData;
    private static Method isInfiniteReload;
    private static Method getAmmoCountWithAttachment;
    private static Method isAmmoOfGun;
    private static Method isAmmoBoxOfGun;
    private static Method isAmmoBoxCreative;
    private static Method isAmmoBoxAllTypeCreative;
    private static Method getAmmoBoxCount;

    private TaczBridge() {}

    static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    static GunBridge.Gun read(ItemStack stack, Inventory inventory) {
        if (!available() || stack.isEmpty()) return null;
        try {
            Object gun = getIGunOrNull.invoke(null, stack);
            if (gun == null) return null;

            Object data = gunData(gun, stack);
            int magazine = data == null ? 0 : (int) getAmmoCountWithAttachment.invoke(null, stack, data);
            String fireMode = fireMode(gun, stack);

            if ((boolean) useDummyAmmo.invoke(gun, stack)) {
                int spare = (int) getDummyAmmoAmount.invoke(gun, stack);
                int max = (int) getMaxDummyAmmoAmount.invoke(gun, stack);
                return new GunBridge.Gun(loaded(gun, stack, data), magazine, spare, max, fireMode);
            }

            int spare = spareAmmo(stack, data, inventory);
            if ((boolean) useInventoryAmmo.invoke(gun, stack)) {
                int inBarrel = barrelBullet(gun, stack, data);
                return new GunBridge.Gun(Math.max(0, spare) + inBarrel, magazine,
                        GunBridge.SPARE_HIDDEN, -1, fireMode);
            }
            return new GunBridge.Gun(loaded(gun, stack, data), magazine, spare, -1, fireMode);
        } catch (Throwable error) {
            available = false;
            return null;
        }
    }

    private static String fireMode(Object gun, ItemStack stack) throws Exception {
        Object mode = getFireMode.invoke(gun, stack);
        if (mode == null) return "";

        String name = mode.toString();
        return UNKNOWN_FIRE_MODE.equalsIgnoreCase(name) ? "" : name;
    }

    private static int loaded(Object gun, ItemStack stack, Object data) throws Exception {
        return (int) getCurrentAmmoCount.invoke(gun, stack) + barrelBullet(gun, stack, data);
    }

    private static int barrelBullet(Object gun, ItemStack stack, Object data) throws Exception {
        if (data == null || !(boolean) hasBulletInBarrel.invoke(gun, stack)) return 0;
        return getBolt.invoke(data) == openBolt ? 0 : 1;
    }

    private static int spareAmmo(ItemStack stack, Object data, Inventory inventory) throws Exception {
        if (inventory.player.isCreative() || infiniteReload(data)) return GunBridge.SPARE_INFINITE;
        return countInventoryAmmo(stack, inventory);
    }

    private static boolean infiniteReload(Object data) throws Exception {
        return data != null && (boolean) isInfiniteReload.invoke(getReloadData.invoke(data));
    }

    private static int countInventoryAmmo(ItemStack gunStack, Inventory inventory) throws Exception {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;

            if (ammoItem.isInstance(stack.getItem())
                    && (boolean) isAmmoOfGun.invoke(stack.getItem(), gunStack, stack)) {
                total += stack.getCount();
            }
            if (!ammoBoxItem.isInstance(stack.getItem())
                    || !(boolean) isAmmoBoxOfGun.invoke(stack.getItem(), gunStack, stack)) {
                continue;
            }
            if ((boolean) isAmmoBoxAllTypeCreative.invoke(stack.getItem(), stack)
                    || (boolean) isAmmoBoxCreative.invoke(stack.getItem(), stack)) {
                return GunBridge.SPARE_INFINITE;
            }
            total += (int) getAmmoBoxCount.invoke(stack.getItem(), stack);
        }
        return total;
    }

    private static Object gunData(Object gun, ItemStack stack) throws Exception {
        ResourceLocation id = (ResourceLocation) getGunId.invoke(gun, stack);
        if (id == null) return null;
        Object index = getCommonGunIndex.invoke(null, id);
        if (!(index instanceof Optional<?> optional) || optional.isEmpty()) return null;
        return getGunData.invoke(optional.get());
    }

    private static boolean bind() {
        try {
            bindGun();
            bindAmmo();
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void bindGun() throws Exception {
        Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
        Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
        Class<?> commonIndex = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
        Class<?> gunData = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
        Class<?> reloadData = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunReloadData");
        Class<?> attachments = Class.forName("com.tacz.guns.util.AttachmentDataUtils");
        Class<?> bolt = Class.forName("com.tacz.guns.resource.pojo.data.gun.Bolt");

        getIGunOrNull = iGun.getMethod("getIGunOrNull", ItemStack.class);
        getGunId = iGun.getMethod("getGunId", ItemStack.class);
        getCurrentAmmoCount = iGun.getMethod("getCurrentAmmoCount", ItemStack.class);
        hasBulletInBarrel = iGun.getMethod("hasBulletInBarrel", ItemStack.class);
        getFireMode = iGun.getMethod("getFireMode", ItemStack.class);
        useDummyAmmo = iGun.getMethod("useDummyAmmo", ItemStack.class);
        useInventoryAmmo = iGun.getMethod("useInventoryAmmo", ItemStack.class);
        getDummyAmmoAmount = iGun.getMethod("getDummyAmmoAmount", ItemStack.class);
        getMaxDummyAmmoAmount = iGun.getMethod("getMaxDummyAmmoAmount", ItemStack.class);
        getCommonGunIndex = api.getMethod("getCommonGunIndex", ResourceLocation.class);
        getGunData = commonIndex.getMethod("getGunData");
        getBolt = gunData.getMethod("getBolt");
        getReloadData = gunData.getMethod("getReloadData");
        isInfiniteReload = reloadData.getMethod("isInfinite");
        getAmmoCountWithAttachment = attachments.getMethod("getAmmoCountWithAttachment", ItemStack.class, gunData);
        openBolt = bolt.getField("OPEN_BOLT").get(null);
    }

    private static void bindAmmo() throws Exception {
        ammoItem = Class.forName("com.tacz.guns.api.item.IAmmo");
        ammoBoxItem = Class.forName("com.tacz.guns.api.item.IAmmoBox");

        isAmmoOfGun = ammoItem.getMethod("isAmmoOfGun", ItemStack.class, ItemStack.class);
        isAmmoBoxOfGun = ammoBoxItem.getMethod("isAmmoBoxOfGun", ItemStack.class, ItemStack.class);
        isAmmoBoxCreative = ammoBoxItem.getMethod("isCreative", ItemStack.class);
        isAmmoBoxAllTypeCreative = ammoBoxItem.getMethod("isAllTypeCreative", ItemStack.class);
        getAmmoBoxCount = ammoBoxItem.getMethod("getAmmoCount", ItemStack.class);
    }
}
