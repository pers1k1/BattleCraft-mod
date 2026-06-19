package com.persiki84.minimap.client;

import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.minimap.network.PlayerPositionSyncPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@OnlyIn(Dist.CLIENT)
public class ClientMapData {
    private static List<MapMarkerSyncPacket.MarkerData> markers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static List<com.persiki84.minimap.network.MapWorldMarkerSyncPacket.WorldMarker> worldMarkers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static List<com.persiki84.minimap.network.PlayerPositionSyncPacket.PlayerPos> players = new java.util.concurrent.CopyOnWriteArrayList<>();
    
    public static boolean enableMinimap = true;
    public static boolean showOtherMarkers = true;
    public static boolean serverHasMod = false;

    public static final java.util.Map<net.minecraft.world.level.ChunkPos, int[]> chunkData = new java.util.concurrent.ConcurrentHashMap<>();

    public static void receiveMapChunks(String dimension, java.util.List<com.persiki84.minimap.network.MapChunkSyncPacket.ChunkData> chunks) {
        serverHasMod = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String currentDim = mc.level.dimension().location().toString().replace(":", "_");
        if (!currentDim.equals(dimension)) return;

        for (com.persiki84.minimap.network.MapChunkSyncPacket.ChunkData chunk : chunks) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(chunk.x, chunk.z);
            chunkData.put(cp, chunk.colors);
            MapTextureManager.markChunkUpdated(cp);
        }
    }

    public static float minimapScale = 1.0f;
    public static float minimapZoom = 1.0f;
    public static int minimapSize = 100;

    public static void syncMarkers(List<MapMarkerSyncPacket.MarkerData> newMarkers) {
        markers.clear();
        markers.addAll(newMarkers);
    }

    public static void syncPlayers(List<PlayerPositionSyncPacket.PlayerPos> newPlayers) {
        players.clear();
        players.addAll(newPlayers);
    }

    public static List<MapMarkerSyncPacket.MarkerData> getMarkers() {
        return new ArrayList<>(markers);
    }

    public static void syncWorldMarkers(List<com.persiki84.minimap.network.MapWorldMarkerSyncPacket.WorldMarker> newMarkers) {
        worldMarkers.clear();
        worldMarkers.addAll(newMarkers);
    }

    public static List<com.persiki84.minimap.network.MapWorldMarkerSyncPacket.WorldMarker> getWorldMarkers() {
        return new ArrayList<>(worldMarkers);
    }

    public static List<PlayerPositionSyncPacket.PlayerPos> getPlayers() {
        return new ArrayList<>(players);
    }

    public static boolean hasMarker(UUID playerId, boolean isTeam) {
        for (MapMarkerSyncPacket.MarkerData marker : markers) {
            if (marker.playerId.equals(playerId) && marker.isTeam == isTeam) {
                return true;
            }
        }
        return false;
    }
}
