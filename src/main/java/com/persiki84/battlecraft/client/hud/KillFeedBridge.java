package com.persiki84.battlecraft.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class KillFeedBridge {
    private static final String SUPERB_HANDLER = "com.atsuishio.superbwarfare.event.KillMessageHandler";
    private static final String TACZ_OVERLAY = "com.tacz.guns.client.gui.overlay.KillAmountOverlay";
    private static final long ENTRY_MS = 2600L;
    private static final long STREAK_MS = 15000L;
    private static final String STREAK_KEY = "battlecraft.killfeed.tacz";

    private static final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    private static boolean probed;
    private static Object superbHandler;
    private static Method superbQueue;
    private static Method recordAttacker;
    private static Method recordTarget;
    private static Method recordHeadshot;
    private static Field taczStamp;
    private static long lastStamp;
    private static int streak;
    private static long streakUntil;

    private KillFeedBridge() {}

    public static void tick() {
        if (!probed) {
            probed = true;
            bindSuperb();
            bindTacz();
        }
        pollSuperb();
        pollTacz();
    }

    public static void forget() {
        seen.clear();
        lastStamp = 0L;
        streak = 0;
        streakUntil = 0L;
    }

    private static void pollSuperb() {
        if (superbQueue == null) return;

        try {
            Collection<?> queue = (Collection<?>) superbQueue.invoke(superbHandler);
            seen.retainAll(queue);
            for (Object record : queue) {
                if (seen.add(record)) announce(record);
            }
        } catch (Throwable error) {
            superbQueue = null;
        }
    }

    private static void announce(Object record) throws ReflectiveOperationException {
        Entity attacker = (Entity) recordAttacker.invoke(record);
        Entity target = (Entity) recordTarget.invoke(record);
        if (attacker == null || target == null) return;

        boolean headshot = (boolean) recordHeadshot.invoke(record);
        ToastHud.push(Component.translatable(headshot
                        ? "battlecraft.killfeed.headshot"
                        : "battlecraft.killfeed.kill",
                attacker.getDisplayName(), target.getDisplayName()), -ENTRY_MS);
    }

    private static void pollTacz() {
        if (taczStamp == null) return;

        try {
            long stamp = taczStamp.getLong(null);
            if (stamp == lastStamp) return;

            lastStamp = stamp;
            if (stamp == 0L) return;
            count();
        } catch (Throwable error) {
            taczStamp = null;
        }
    }

    // WHY: у TACZ своё окно серии из его конфига, поэтому счёт ведётся здесь: пятнадцать секунд
    // WHY: от последнего убийства, столько же держится и само уведомление
    private static void count() {
        long now = System.currentTimeMillis();
        streak = now < streakUntil ? streak + 1 : 1;
        streakUntil = now + STREAK_MS;
        ToastHud.pushRenewing(STREAK_KEY, KillFeedBridge::streakText, STREAK_MS);
    }

    private static Component streakText() {
        return Component.translatable(STREAK_KEY, streak);
    }

    private static void bindSuperb() {
        if (!ModList.get().isLoaded("superbwarfare")) return;

        try {
            Class<?> handler = Class.forName(SUPERB_HANDLER);
            superbHandler = handler.getField("INSTANCE").get(null);
            superbQueue = handler.getMethod("getQUEUE");

            Class<?> record = Class.forName("com.atsuishio.superbwarfare.tools.LivingKillRecord");
            recordAttacker = record.getMethod("getAttacker");
            recordTarget = record.getMethod("getTarget");
            recordHeadshot = record.getMethod("getHeadshot");
        } catch (Throwable error) {
            superbQueue = null;
        }
    }

    private static void bindTacz() {
        if (!ModList.get().isLoaded("tacz")) return;

        try {
            Class<?> overlay = Class.forName(TACZ_OVERLAY);
            taczStamp = overlay.getDeclaredField("killTimestamp");
            taczStamp.setAccessible(true);
        } catch (Throwable error) {
            taczStamp = null;
        }
    }
}
