package com.persiki84.battlecraft.mode;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.economy.WalletAdmin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class ModeEvents {
    private static final int CLOCK_TICKS = 20;
    private static int clock;

    private ModeEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ModeBook.open(event.getServer());
        WalletAdmin.matchStartMoney(MatchModes::startMoney);
        clock = 0;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ModeBook.close();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock < CLOCK_TICKS) return;
        clock = 0;
        MatchClock.tick(event.getServer());
        ModeSync.scoreIfChanged();
    }

    // WHY: нокдаун отменяет смерть раньше всех, а добивание идёт настоящей смертью с прежним
    // WHY: убийцей; на самом позднем приоритете каждое убийство засчитывается ровно один раз
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        MatchKills.playerKilled(victim, event.getSource().getEntity());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ModeSync.lobbyTo(player);
        ModeSync.scoreTo(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ModeVotes.forget(player.getUUID());
        ModeSync.lobbyToAll(player.getServer(), player.getUUID());
    }
}
