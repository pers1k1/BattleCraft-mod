package com.persiki84.battlecraft.client.combat;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

final class ParkourStamina {
    private static final String MOD_ID = "parcool";
    private static final String STAMINA = "com.alrex.parcool.api.Stamina";

    private static boolean probed;
    private static boolean available;
    private static Method forPlayer;
    private static Method consume;
    private static Method recover;
    private static Method maximum;
    private static Method current;

    private ParkourStamina() {}

    static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    static void drain(Player player, int amount) {
        if (!available() || amount <= 0) return;
        try {
            Object stamina = forPlayer.invoke(null, player);
            if (stamina != null) consume.invoke(stamina, amount);
        } catch (Throwable error) {
            available = false;
        }
    }

    static void fill(Player player) {
        if (!available()) return;
        try {
            Object stamina = forPlayer.invoke(null, player);
            if (stamina == null) return;

            int max = (int) maximum.invoke(stamina);
            if (max > 0) recover.invoke(stamina, max);
        } catch (Throwable error) {
            available = false;
        }
    }

    static void restore(Player player, int amount) {
        if (!available() || amount <= 0) return;
        try {
            Object stamina = forPlayer.invoke(null, player);
            if (stamina != null) recover.invoke(stamina, amount);
        } catch (Throwable error) {
            available = false;
        }
    }

    static int value(Player player) {
        if (!available()) return -1;
        try {
            Object stamina = forPlayer.invoke(null, player);
            return stamina == null ? -1 : (int) current.invoke(stamina);
        } catch (Throwable error) {
            available = false;
            return -1;
        }
    }

    static int maxValue(Player player) {
        if (!available()) return 0;
        try {
            Object stamina = forPlayer.invoke(null, player);
            return stamina == null ? 0 : (int) maximum.invoke(stamina);
        } catch (Throwable error) {
            available = false;
            return 0;
        }
    }

    private static boolean bind() {
        try {
            Class<?> stamina = Class.forName(STAMINA);
            forPlayer = stamina.getMethod("get", Player.class);
            consume = stamina.getMethod("consume", int.class);
            recover = stamina.getMethod("recover", int.class);
            maximum = stamina.getMethod("getMaxValue");
            current = stamina.getMethod("getValue");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
