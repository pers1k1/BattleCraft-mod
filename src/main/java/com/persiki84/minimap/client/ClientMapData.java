package com.persiki84.minimap.client;

import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.minimap.network.PlayerPositionSyncPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@OnlyIn(Dist.CLIENT)
public class ClientMapData {
    private static final List<MapMarkerSyncPacket.MarkerData> markers = new CopyOnWriteArrayList<>();
    private static final List<com.persiki84.minimap.network.MapWorldMarkerSyncPacket.WorldMarker> worldMarkers = new CopyOnWriteArrayList<>();
    private static final List<PlayerPositionSyncPacket.PlayerPos> players = new CopyOnWriteArrayList<>();

    private static final List<MapMarkerSyncPacket.MarkerData> markersView = java.util.Collections.unmodifiableList(markers);
    private static final List<com.persiki84.minimap.network.MapWorldMarkerSyncPacket.WorldMarker> worldMarkersView = java.util.Collections.unmodifiableList(worldMarkers);
    private static final List<PlayerPositionSyncPacket.PlayerPos> playersView = java.util.Collections.unmodifiableList(players);


    public static boolean enableMinimap = true;
    public static boolean showOtherMarkers = true;
    public static boolean serverHasMod = false;

    // WHY: раньше признак ставился только приходом чанков, а отправка его требовала: на чистом
    // WHY: сервере первым не слал никто и командная карта не заводилась вовсе. Спрашиваем канал.
    public static boolean serverTakesChunks() {
        if (serverHasMod) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;

        return com.persiki84.minimap.network.PacketHandler.INSTANCE
                .isRemotePresent(mc.getConnection().getConnection());
    }

    public static final java.util.Map<net.minecraft.world.level.ChunkPos, int[]> chunkData = new java.util.concurrent.ConcurrentHashMap<>();

    public static void receiveMapChunks(String dimension, java.util.List<com.persiki84.minimap.network.MapChunkSyncPacket.ChunkData> chunks) {
        serverHasMod = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String currentDim = mc.level.dimension().location().toString().replace(":", "_");
        if (!currentDim.equals(dimension)) return;

        java.util.List<net.minecraft.world.level.ChunkPos> received = new java.util.ArrayList<>(chunks.size());
        for (com.persiki84.minimap.network.MapChunkSyncPacket.ChunkData chunk : chunks) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(chunk.x, chunk.z);
            chunkData.put(cp, chunk.colors);
            received.add(cp);
        }
        if (received.isEmpty()) return;

        ClientMapStorage.touch();
        MapTextureManager.markChunksUpdated(received);
    }

    public static float minimapZoom = 1.0f;
    public static final int MIN_SIZE = 50;
    public static final int MAX_SIZE = 300;

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
        return markersView;
    }

    public static void clearMarkers() {
        markers.clear();
        worldMarkers.clear();
        MarkerPings.reset();
    }

    public static void clearPlayers() {
        players.clear();
    }

    public static void syncWorldMarkers(List<com.persiki84.minimap.network.MapWorldMarkerSyncPacket.WorldMarker> newMarkers) {
        worldMarkers.clear();
        worldMarkers.addAll(newMarkers);
    }

    public static List<com.persiki84.minimap.network.MapWorldMarkerSyncPacket.WorldMarker> getWorldMarkers() {
        return worldMarkersView;
    }

    public static List<PlayerPositionSyncPacket.PlayerPos> getPlayers() {
        return playersView;
    }

}
