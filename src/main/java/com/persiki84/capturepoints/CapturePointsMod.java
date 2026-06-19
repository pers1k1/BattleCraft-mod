package com.persiki84.capturepoints;

import com.mojang.logging.LogUtils;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import com.persiki84.capturepoints.client.CaptureHudOverlay;
import com.persiki84.capturepoints.client.KeyBindings;
import com.persiki84.capturepoints.commands.CapturePointCommand;
import com.persiki84.capturepoints.commands.FinalPointCommand;
import com.persiki84.capturepoints.commands.ResetCooldownCommand;
import com.persiki84.capturepoints.event.CaptureEventHandler;
import com.persiki84.capturepoints.network.CapturePointSyncPacket;
import com.persiki84.capturepoints.network.FinalPointSyncPacket;
import com.persiki84.capturepoints.network.GlobalMarkerSyncPacket;
import com.persiki84.capturepoints.network.PacketHandler;
import com.persiki84.capturepoints.network.PointSyncData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Mod(CapturePointsMod.MOD_ID)
public class CapturePointsMod {
    public static final String MOD_ID = "capturepoints";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CapturePointsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CaptureEventHandler());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
        LOGGER.info("Capture Points Mod initialized!");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Client setup complete");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            CapturePointManager.load(level);
        }

        syncAllPlayers(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        CapturePointManager.cleanupAllHolograms();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            CapturePointManager.save(level);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CapturePointManager.tick();
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlayerData(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlayerData(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlayerData(player);
        }
    }

    private void syncPlayerData(ServerPlayer player) {
        Map<String, PointSyncData> pointOwners = new HashMap<>();
        for (CapturePoint point : CapturePointManager.getAllPoints()) {
            pointOwners.put(point.getName(), new PointSyncData(point.getOwnerTeam(), point.getPosition()));
        }

        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new CapturePointSyncPacket(pointOwners)
        );

        Map<String, PointSyncData> finalPointOwners = new HashMap<>();
        for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
            finalPointOwners.put(point.getName(), new PointSyncData(point.getOwnerTeam(), point.getPosition()));
        }

        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new FinalPointSyncPacket(finalPointOwners)
        );

        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new GlobalMarkerSyncPacket(CapturePointManager.isGlobalCaptureMarkers(), CapturePointManager.isGlobalFinalMarkers())
        );

        LOGGER.info("Synced {} capture points and {} final points to player {}",
                pointOwners.size(), finalPointOwners.size(), player.getName().getString());
    }

    private void syncAllPlayers(net.minecraft.server.MinecraftServer server) {
        Map<String, PointSyncData> pointOwners = new HashMap<>();
        for (CapturePoint point : CapturePointManager.getAllPoints()) {
            pointOwners.put(point.getName(), new PointSyncData(point.getOwnerTeam(), point.getPosition()));
        }

        Map<String, PointSyncData> finalPointOwners = new HashMap<>();
        for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
            finalPointOwners.put(point.getName(), new PointSyncData(point.getOwnerTeam(), point.getPosition()));
        }

        GlobalMarkerSyncPacket markerPacket = new GlobalMarkerSyncPacket(CapturePointManager.isGlobalCaptureMarkers(), CapturePointManager.isGlobalFinalMarkers());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new CapturePointSyncPacket(pointOwners)
            );
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new FinalPointSyncPacket(finalPointOwners)
            );
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    markerPacket
            );
        }

        LOGGER.info("Synced {} capture points and {} final points to all players",
                pointOwners.size(), finalPointOwners.size());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CapturePointCommand.register(event.getDispatcher(), event.getBuildContext());
        FinalPointCommand.register(event.getDispatcher(), event.getBuildContext());
        ResetCooldownCommand.register(event.getDispatcher());
    }


    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.CAPTURE_KEY);
        }

        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("capture_hud", CaptureHudOverlay.HUD_CAPTURE);
        }
    }
}
