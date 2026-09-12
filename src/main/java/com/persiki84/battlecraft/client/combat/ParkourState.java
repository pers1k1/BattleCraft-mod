package com.persiki84.battlecraft.client.combat;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

final class ParkourState {
    private static final String MOD_ID = "parcool";
    private static final String PARKOURABILITY = "com.alrex.parcool.common.capability.Parkourability";
    private static final String ACTION = "com.alrex.parcool.common.action.Action";
    private static final String ACTION_INFO = "com.alrex.parcool.common.info.ActionInfo";

    private static final Map<String, Class<?>> actionTypes = new HashMap<>();

    private static boolean probed;
    private static boolean available;
    private static Method forPlayer;
    private static Method actionOf;
    private static Method isDoing;
    private static Method actionInfoOf;
    private static Method staminaCostOf;

    private ParkourState() {}

    static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    static boolean doing(Player player, String actionClass) {
        if (!available()) return false;
        try {
            Class<?> type = actionType(actionClass);
            if (type == null) return false;

            Object parkourability = forPlayer.invoke(null, player);
            if (parkourability == null) return false;

            Object action = actionOf.invoke(parkourability, type);
            return action != null && (boolean) isDoing.invoke(action);
        } catch (Throwable error) {
            available = false;
            return false;
        }
    }

    static int staminaCost(Player player, String actionClass) {
        if (!available()) return 0;
        try {
            Class<?> type = actionType(actionClass);
            if (type == null) return 0;

            Object parkourability = forPlayer.invoke(null, player);
            if (parkourability == null) return 0;

            Object actionInfo = actionInfoOf.invoke(parkourability);
            return actionInfo == null ? 0 : (int) staminaCostOf.invoke(actionInfo, type);
        } catch (Throwable error) {
            available = false;
            return 0;
        }
    }

    static boolean doingAny(Player player, Iterable<String> actionClasses) {
        for (String actionClass : actionClasses) {
            if (doing(player, actionClass)) return true;
        }
        return false;
    }

    private static Class<?> actionType(String name) {
        if (actionTypes.containsKey(name)) return actionTypes.get(name);

        Class<?> type = null;
        try {
            type = Class.forName(name);
        } catch (Throwable ignored) {
        }
        actionTypes.put(name, type);
        return type;
    }

    private static boolean bind() {
        try {
            Class<?> parkourability = Class.forName(PARKOURABILITY);
            Class<?> action = Class.forName(ACTION);

            Class<?> actionInfo = Class.forName(ACTION_INFO);

            forPlayer = parkourability.getMethod("get", Player.class);
            actionOf = parkourability.getMethod("get", Class.class);
            isDoing = action.getMethod("isDoing");
            actionInfoOf = parkourability.getMethod("getActionInfo");
            staminaCostOf = actionInfo.getMethod("getStaminaConsumptionOf", Class.class);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
