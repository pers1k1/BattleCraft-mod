package com.persiki84.airdrop;

import com.persiki84.airdrop.cache.CacheGuard;
import com.persiki84.airdrop.cache.CacheTicker;
import com.persiki84.airdrop.cache.LootCaches;
import com.persiki84.airdrop.command.AirDropCommands;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.entity.AirDropEntity;
import com.persiki84.airdrop.entity.DropTickets;
import com.persiki84.airdrop.entity.ModEntities;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.airdrop.network.CacheBroadcast;
import com.persiki84.airdrop.network.PacketHandler;
import com.persiki84.airdrop.scheduler.AirDropScheduler;
import com.persiki84.battlecraft.MatchEvent;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(AirDropMod.MOD_ID)
public class AirDropMod {
    public static final String MOD_ID = "airdrop";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public AirDropMod() {
        AirDropConfig.register();

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(modBus);
        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new AirDropScheduler());
        MinecraftForge.EVENT_BUS.register(new CacheGuard());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) ->
                AirDropCommands.register(e.getDispatcher(), e.getBuildContext()));
    }

    public static boolean enabled() {
        return AirDropConfig.active() && ModuleSwitches.allows(ModuleId.AIRDROP);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            PacketHandler.register();
            DropTickets.register();
            LootTables.reload(FMLPaths.CONFIGDIR.get());
        });
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LootCaches.load(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LootCaches.save();
        LootCaches.clear();
        CacheBroadcast.clear();
        DropTickets.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        DropTickets.tick(event.getServer());
        if (!enabled()) return;

        CacheTicker.tick(event.getServer());
        CacheBroadcast.tick(event.getServer());
    }

    @SubscribeEvent
    public void onMatchStarted(MatchEvent.Started event) {
        LootCaches.nextMatch();
        if (enabled()) CacheTicker.settleLoaded(event.server());
        LootCaches.save();
    }

    @SubscribeEvent
    public void onMatchEnded(MatchEvent.Ended event) {
        AirDropEntity.mapCleared();
        if (!enabled()) return;

        CacheTicker.settleLoaded(event.server());
        LootCaches.save();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CacheBroadcast.forget(event.getEntity().getUUID());
    }
}
