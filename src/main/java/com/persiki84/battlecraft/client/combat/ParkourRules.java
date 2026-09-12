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
import java.util.Set;

public final class ParkourRules {
    private static final String MOD_ID = "parcool";
    private static final String START_CLASS = "com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToStartEvent";
    private static final String CONTINUE_CLASS = "com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToContinueEvent";

    static final String FAST_RUN = "com.alrex.parcool.common.action.impl.FastRun";
    private static final String SLIDE = "com.alrex.parcool.common.action.impl.Slide";
    private static final String DODGE = "com.alrex.parcool.common.action.impl.Dodge";

    private static final Set<String> LEAPS = Set.of(
            "com.alrex.parcool.common.action.impl.WallJump",
            "com.alrex.parcool.common.action.impl.CatLeap",
            "com.alrex.parcool.common.action.impl.JumpFromBar",
            "com.alrex.parcool.common.action.impl.VerticalWallRun",
            "com.alrex.parcool.common.action.impl.HorizontalWallRun"
    );

    private static final Set<String> ARMED_SPRINT_BLOCKED = Set.of(
            "com.alrex.parcool.common.action.impl.ClimbUp",
            "com.alrex.parcool.common.action.impl.Vault",
            "com.alrex.parcool.common.action.impl.ClingToCliff",
            "com.alrex.parcool.common.action.impl.HangDown"
    );

    private static Method getPlayer;
    private static Method getAction;

    private ParkourRules() {}

    @SuppressWarnings("unchecked")
    public static void listen() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        try {
            Class<?> start = Class.forName(START_CLASS);
            Class<?> proceed = Class.forName(CONTINUE_CLASS);
            getPlayer = start.getMethod("getPlayer");
            getAction = start.getMethod("getAction");

            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    (Class<Event>) start, ParkourRules::onTryToStart);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    (Class<Event>) proceed, ParkourRules::onTryToContinue);
        } catch (Throwable ignored) {
        }
    }

    private static void onTryToStart(Event event) {
        try {
            if (blocked(event)) event.setCanceled(true);
        } catch (Throwable ignored) {
        }
    }

    private static void onTryToContinue(Event event) {
        try {
            LocalPlayer self = self(event);
            if (self == null) return;

            String action = actionName(event);
            if (FAST_RUN.equals(action) && CombatRules.grounded(self)) event.setCanceled(true);
            if (DODGE.equals(action) && AimLock.dodgeDenied(self)) event.setCanceled(true);
        } catch (Throwable ignored) {
        }
    }

    private static boolean blocked(Event event) throws Exception {
        LocalPlayer self = self(event);
        if (self == null) return false;

        String action = actionName(event);
        if (action == null) return false;
        if (FAST_RUN.equals(action)) return CombatRules.grounded(self);
        if (DODGE.equals(action)) return AimLock.dodgeDenied(self);
        if (LEAPS.contains(action)) return AimLock.jumpDenied(self);
        if (SLIDE.equals(action)) return GunHold.armed(self) && ClientGameRules.allows(GameRule.NO_SLIDE_WITH_GUN);
        return ARMED_SPRINT_BLOCKED.contains(action) && self.isSprinting() && GunHold.armed(self)
                && ClientGameRules.allows(GameRule.NO_CLIMB_WITH_GUN);
    }

    private static String actionName(Event event) throws Exception {
        Object action = getAction.invoke(event);
        return action == null ? null : action.getClass().getName();
    }

    private static LocalPlayer self(Event event) throws Exception {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null || !(getPlayer.invoke(event) instanceof Player player)) return null;
        return player.getUUID().equals(self.getUUID()) ? self : null;
    }
}
