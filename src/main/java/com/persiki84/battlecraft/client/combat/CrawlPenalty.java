package com.persiki84.battlecraft.client.combat;

import com.persiki84.battlecraft.client.ClientGameRules;
import com.persiki84.battlecraft.rules.GameRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

public final class CrawlPenalty {
    private static final String MOD_ID = "parcool";
    private static final String EVENT_CLASS = "com.alrex.parcool.api.unstable.action.ParCoolActionEvent$StartEvent";
    private static final String CRAWL = "com.alrex.parcool.common.action.impl.Crawl";

    private static final long WINDOW_MS = 3000L;
    private static final int FREE_STARTS = 3;
    private static final float STEP_COST = 0.07f;
    private static final float MAX_COST = 0.4f;

    private static final Deque<Long> starts = new ArrayDeque<>();

    private static Method getPlayer;
    private static Method getAction;

    private CrawlPenalty() {}

    @SuppressWarnings("unchecked")
    public static void listen() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        try {
            Class<?> event = Class.forName(EVENT_CLASS);
            getPlayer = event.getMethod("getPlayer");
            getAction = event.getMethod("getAction");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    (Class<Event>) event, CrawlPenalty::onStart);
        } catch (Throwable ignored) {
        }
    }

    private static void onStart(Event event) {
        try {
            if (!ClientGameRules.allows(GameRule.CRAWL_SPAM_PENALTY)) return;

            Player player = self(event);
            if (player == null) return;

            int over = countStart() - FREE_STARTS;
            if (over <= 0) return;

            ParkourStamina.drain(player, cost(player, over));
        } catch (Throwable ignored) {
        }
    }

    private static int cost(Player player, int over) {
        int maximum = ParkourStamina.maxValue(player);
        if (maximum <= 0) return 0;

        float share = Math.min(MAX_COST, STEP_COST * over);
        return Math.max(1, Math.round(maximum * share));
    }

    private static int countStart() {
        long now = System.currentTimeMillis();
        while (!starts.isEmpty() && now - starts.peekFirst() > WINDOW_MS) {
            starts.pollFirst();
        }

        starts.addLast(now);
        return starts.size();
    }

    private static Player self(Event event) throws Exception {
        Object action = getAction.invoke(event);
        if (action == null || !CRAWL.equals(action.getClass().getName())) return null;

        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null || !(getPlayer.invoke(event) instanceof Player player)) return null;
        return player.getUUID().equals(self.getUUID()) ? self : null;
    }
}
