package com.persiki84.battlecraft.client.combat;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class TaczCombat {
    private static final String MOD_ID = "tacz";

    private static boolean probed;
    private static boolean available;

    private static Method holdsGun;
    private static Method fromLocalPlayer;
    private static Method isAim;
    private static Method aim;
    private static Method fromLivingEntity;
    private static Method getReloadState;
    private static Method getStateType;
    private static Object notReloading;
    private static Field shootKey;
    private static Field sprintLock;

    private TaczCombat() {}

    static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    static boolean aiming(LocalPlayer player) {
        if (!available() || !holdsGun(player)) return false;
        try {
            return (boolean) isAim.invoke(fromLocalPlayer.invoke(null, player));
        } catch (Throwable error) {
            available = false;
            return false;
        }
    }

    static boolean reloading(LocalPlayer player) {
        if (!available() || !holdsGun(player)) return false;
        try {
            Object state = getReloadState.invoke(fromLivingEntity.invoke(null, player));
            return state != null && getStateType.invoke(state) != notReloading;
        } catch (Throwable error) {
            available = false;
            return false;
        }
    }

    static void cancelAim(LocalPlayer player) {
        if (!available() || !holdsGun(player)) return;
        try {
            aim.invoke(fromLocalPlayer.invoke(null, player), false);
        } catch (Throwable error) {
            available = false;
        }
    }

    static boolean armed(LocalPlayer player) {
        return available() && holdsGun(player);
    }

    static void holdSprintLock() {
        if (!available() || sprintLock == null) return;
        try {
            if (!sprintLock.getBoolean(null)) sprintLock.setBoolean(null, true);
        } catch (Throwable error) {
            sprintLock = null;
        }
    }

    static boolean firing() {
        if (!available() || shootKey == null) return false;
        try {
            return KeyPressed.physically((KeyMapping) shootKey.get(null));
        } catch (Throwable error) {
            shootKey = null;
            return false;
        }
    }

    private static boolean holdsGun(LocalPlayer player) {
        try {
            return (boolean) holdsGun.invoke(null, player);
        } catch (Throwable error) {
            available = false;
            return false;
        }
    }

    private static boolean bind() {
        try {
            Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> clientOperator = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            Class<?> operator = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            Class<?> reloadState = Class.forName("com.tacz.guns.api.entity.ReloadState");
            Class<?> stateType = Class.forName("com.tacz.guns.api.entity.ReloadState$StateType");

            holdsGun = iGun.getMethod("mainHandHoldGun", LivingEntity.class);
            fromLocalPlayer = clientOperator.getMethod("fromLocalPlayer", LocalPlayer.class);
            isAim = clientOperator.getMethod("isAim");
            aim = clientOperator.getMethod("aim", boolean.class);
            fromLivingEntity = operator.getMethod("fromLivingEntity", LivingEntity.class);
            getReloadState = operator.getMethod("getSynReloadState");
            getStateType = reloadState.getMethod("getStateType");
            notReloading = stateType.getField("NOT_RELOADING").get(null);
            bindShootKey();
            return true;
        } catch (Throwable error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] tacz combat bridge is not bound: {}", error.toString());
            return false;
        }
    }

    private static void bindShootKey() {
        try {
            shootKey = Class.forName("com.tacz.guns.client.input.ShootKey").getField("SHOOT_KEY");
            sprintLock = Class.forName("com.tacz.guns.client.gameplay.LocalPlayerSprint").getField("stopSprint");
        } catch (Throwable error) {
            shootKey = null;
            BattleCraftMod.LOGGER.warn("[battlecraft] tacz shoot key is missing: {}", error.toString());
        }
    }
}
