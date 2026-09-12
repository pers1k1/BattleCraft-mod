package com.persiki84.battlecraft.parcool;

import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.GameRules;
import com.persiki84.shared.gunsmith.GunSmith;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "battlecraft")
public final class ParCoolLimits {
    public static final float EXHAUSTED_RATIO = 0.15f;

    private static final int CHECK_INTERVAL = 2;
    private static final int RUN_ALLOWED = 1;
    private static final int AGILE_ALLOWED = 2;
    private static final int UNKNOWN = -1;

    private static final Map<UUID, Integer> applied = new ConcurrentHashMap<>();

    private ParCoolLimits() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL != 0 || !ParCoolBridge.available()) return;

        int wanted = wantedState(player);
        if (applied.getOrDefault(player.getUUID(), UNKNOWN) == wanted) return;

        boolean running = (wanted & RUN_ALLOWED) != 0;
        boolean agile = (wanted & AGILE_ALLOWED) != 0;
        if (ParCoolBridge.allow(player, running, agile)) applied.put(player.getUUID(), wanted);
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        applied.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        applied.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        applied.remove(event.getEntity().getUUID());
    }

    private static int wantedState(ServerPlayer player) {
        int state = 0;
        if (!GameRules.allows(GameRule.NO_SPRINT_BOOST_WITH_GUN) || !armed(player)) state |= RUN_ALLOWED;
        if (!GameRules.allows(GameRule.STAMINA_LIMITS_MOVES) || !winded(player)) state |= AGILE_ALLOWED;
        return state;
    }

    private static boolean armed(ServerPlayer player) {
        return GunSmith.isGun(player.getItemInHand(InteractionHand.MAIN_HAND))
                || GunSmith.isGun(player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private static boolean winded(ServerPlayer player) {
        return ParCoolBridge.exhausted(player) || ParCoolBridge.ratio(player) < EXHAUSTED_RATIO;
    }
}
