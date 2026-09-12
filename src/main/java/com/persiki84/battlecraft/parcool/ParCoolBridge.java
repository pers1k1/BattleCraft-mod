package com.persiki84.battlecraft.parcool;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public final class ParCoolBridge {
    private static final String LIMITATION = "com.alrex.parcool.api.unstable.Limitation";
    private static final String STAMINA = "com.alrex.parcool.api.Stamina";
    private static final String ACTIONS = "com.alrex.parcool.common.action.impl.";

    private static final String[] AGILE_ACTIONS = {
            "Crawl", "Roll", "Dodge", "Slide", "ClimbUp", "WallJump",
            "VerticalWallRun", "HorizontalWallRun", "ClingToCliff", "ClimbPoles", "CatLeap"
    };

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static boolean probed;
    private static boolean available;
    private static boolean announced;
    private static Method individual;
    private static Method permit;
    private static Method enable;
    private static Method apply;
    private static Method stamina;
    private static Method staminaValue;
    private static Method staminaMax;
    private static Method staminaExhausted;
    private static Class<?> fastRun;
    private static Class<?>[] agile;

    private ParCoolBridge() {}

    public static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded("parcool") && bind();
            if (!available) LOGGER.warn("[battlecraft] parcool bridge not bound");
        }
        return available;
    }

    public static float ratio(Player player) {
        try {
            Object handle = stamina.invoke(null, player);
            if (handle == null) return 1.0f;

            int max = (int) staminaMax.invoke(handle);
            if (max <= 0) return 1.0f;
            return (int) staminaValue.invoke(handle) / (float) max;
        } catch (Throwable error) {
            available = false;
            return 1.0f;
        }
    }

    public static boolean exhausted(Player player) {
        try {
            Object handle = stamina.invoke(null, player);
            return handle != null && (boolean) staminaExhausted.invoke(handle);
        } catch (Throwable error) {
            available = false;
            return false;
        }
    }

    public static boolean allow(ServerPlayer player, boolean running, boolean agileActions) {
        try {
            Object limits = individual.invoke(null, player);
            if (limits == null) return false;

            enable.invoke(limits);
            permit.invoke(limits, fastRun, running);
            for (Class<?> action : agile) {
                permit.invoke(limits, action, agileActions);
            }
            apply.invoke(limits);
            announce("limits applied: run=" + running + " agile=" + agileActions);
            return true;
        } catch (Throwable error) {
            available = false;
            LOGGER.warn("[battlecraft] parcool limits failed: {}", String.valueOf(error));
            return false;
        }
    }

    private static void announce(String message) {
        if (announced) return;
        announced = true;
        LOGGER.info("[battlecraft] parcool {}", message);
    }

    private static boolean bind() {
        try {
            bindLimitation();
            bindStamina();
            bindActions();
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void bindLimitation() throws ReflectiveOperationException {
        Class<?> type = Class.forName(LIMITATION);
        individual = type.getMethod("getIndividual", ServerPlayer.class);
        permit = type.getMethod("permit", Class.class, boolean.class);
        enable = type.getMethod("enable");
        apply = type.getMethod("apply");
    }

    private static void bindStamina() throws ReflectiveOperationException {
        Class<?> type = Class.forName(STAMINA);
        stamina = type.getMethod("get", Player.class);
        staminaValue = type.getMethod("getValue");
        staminaMax = type.getMethod("getMaxValue");
        staminaExhausted = type.getMethod("isExhausted");
    }

    private static void bindActions() throws ReflectiveOperationException {
        fastRun = Class.forName(ACTIONS + "FastRun");
        agile = new Class<?>[AGILE_ACTIONS.length];
        for (int index = 0; index < AGILE_ACTIONS.length; index++) {
            agile[index] = Class.forName(ACTIONS + AGILE_ACTIONS[index]);
        }
    }
}
