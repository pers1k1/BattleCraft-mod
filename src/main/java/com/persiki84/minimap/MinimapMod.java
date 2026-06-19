package com.persiki84.minimap;

import com.persiki84.minimap.client.MapScreen;
import com.persiki84.minimap.client.MinimapOverlay;
import com.persiki84.minimap.network.PacketHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraft.world.level.ChunkPos;
import java.util.Set;
import java.util.HashSet;
import com.persiki84.minimap.network.MapChunkInvalidatePacket;

@Mod(MinimapMod.MOD_ID)
public class MinimapMod {
    public static final String MOD_ID = "minimap";

    public MinimapMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MapManager.tick(event.getServer());
            if (event.getServer().getTickCount() % 1200 == 0) {
                com.persiki84.minimap.server.ServerMapStorage.save();
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        com.persiki84.minimap.server.ServerMapStorage.load(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        com.persiki84.minimap.server.ServerMapStorage.save();
        MapManager.clearAll();
    }

    @SubscribeEvent
    public void onPlayerJoin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            String dim = player.level().dimension().location().toString().replace(":", "_");
            com.persiki84.minimap.server.ServerMapStorage.syncFullMap(player, dim);
            com.persiki84.minimap.MapManager.syncMarkers(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            String dim = event.getTo().location().toString().replace(":", "_");
            com.persiki84.minimap.server.ServerMapStorage.syncFullMap(player, dim);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.getLevel().isClientSide()) {
            notifyChunkChange(event.getLevel(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getLevel().isClientSide()) {
            notifyChunkChange(event.getLevel(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!event.getLevel().isClientSide()) {
            Set<ChunkPos> affectedChunks = new HashSet<>();
            for (net.minecraft.core.BlockPos pos : event.getAffectedBlocks()) {
                affectedChunks.add(new ChunkPos(pos));
            }
            for (ChunkPos cp : affectedChunks) {
                notifyChunkChange(event.getLevel(), cp);
            }
        }
    }

    private void notifyChunkChange(net.minecraft.world.level.LevelAccessor levelAccessor, net.minecraft.core.BlockPos pos) {
        if (levelAccessor instanceof net.minecraft.server.level.ServerLevel level) {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (level.hasChunk(chunkX, chunkZ)) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                PacketHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                        new MapChunkInvalidatePacket(chunkX, chunkZ)
                );
            }
        }
    }

    private void notifyChunkChange(net.minecraft.world.level.LevelAccessor levelAccessor, ChunkPos cp) {
        if (levelAccessor instanceof net.minecraft.server.level.ServerLevel level) {
            if (level.hasChunk(cp.x, cp.z)) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cp.x, cp.z);
                PacketHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                        new MapChunkInvalidatePacket(cp.x, cp.z)
                );
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        public static final KeyMapping OPEN_MAP_KEY = new KeyMapping("key.minimap.open_map", GLFW.GLFW_KEY_J, "category.minimap");

        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MAP_KEY);
        }

        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("minimap_hud", MinimapOverlay.HUD_MINIMAP);
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onChunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level && level.isClientSide()) {
                if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                    com.persiki84.minimap.client.ClientMapData.chunkData.remove(chunk.getPos());
                }
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                com.persiki84.minimap.client.MapTextureManager.update();
                if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() % 1200 == 0) {
                    com.persiki84.minimap.client.ClientMapStorage.save();
                }
            }
        }

        @SubscribeEvent
        public static void onPlayerLogin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            com.persiki84.minimap.client.MapTextureManager.clearAll();
            com.persiki84.minimap.client.ClientMapData.serverHasMod = false;
            com.persiki84.minimap.client.ClientMapStorage.load();
            com.persiki84.minimap.client.ClientMapData.getMarkers().clear();
            com.persiki84.minimap.client.ClientMapData.getPlayers().clear();
        }

        @SubscribeEvent
        public static void onPlayerLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            com.persiki84.minimap.client.ClientMapStorage.save();
            com.persiki84.minimap.client.MapTextureManager.clearAll();
            com.persiki84.minimap.client.ClientMapData.chunkData.clear();
            com.persiki84.minimap.client.ClientMapData.getMarkers().clear();
            com.persiki84.minimap.client.ClientMapData.getPlayers().clear();
            com.persiki84.minimap.client.ClientMapData.serverHasMod = false;
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && ClientModEvents.OPEN_MAP_KEY.consumeClick()) {
                if (!com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled() && com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase() == com.persiki84.battlecraft.BattleCraftManager.GamePhase.LOBBY) {
                    return;
                }
                mc.setScreen(new MapScreen());
            }
        }
    }
}
