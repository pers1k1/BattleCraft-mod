package com.persiki84.minimap.server;

import com.persiki84.minimap.network.MapChunkSyncPacket;
import com.persiki84.minimap.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMapStorage {
    private static final Map<String, Map<ChunkPos, int[]>> serverChunkData = new ConcurrentHashMap<>();
    private static MinecraftServer currentServer;

    public static void load(MinecraftServer server) {
        currentServer = server;
        serverChunkData.clear();
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("minimap_data");
        if (!Files.exists(dir)) return;

        try {
            Files.list(dir).forEach(file -> {
                if (file.toString().endsWith(".dat")) {
                    String dimension = file.getFileName().toString().replace(".dat", "");
                    Map<ChunkPos, int[]> chunks = new ConcurrentHashMap<>();
                    try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                        int count = dis.readInt();
                        for (int i = 0; i < count; i++) {
                            int x = dis.readInt();
                            int z = dis.readInt();
                            int[] colors = new int[256];
                            for (int j = 0; j < 256; j++) {
                                colors[j] = dis.readInt();
                            }
                            chunks.put(new ChunkPos(x, z), colors);
                        }
                    } catch (IOException ignored) {}
                    serverChunkData.put(dimension, chunks);
                }
            });
        } catch (IOException ignored) {}
    }

    public static void save() {
        if (currentServer == null) return;
        Path dir = currentServer.getWorldPath(LevelResource.ROOT).resolve("minimap_data");
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, Map<ChunkPos, int[]>> dimEntry : serverChunkData.entrySet()) {
                Path file = dir.resolve(dimEntry.getKey() + ".dat");
                try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
                    Map<ChunkPos, int[]> chunks = dimEntry.getValue();
                    dos.writeInt(chunks.size());
                    for (Map.Entry<ChunkPos, int[]> chunkEntry : chunks.entrySet()) {
                        dos.writeInt(chunkEntry.getKey().x);
                        dos.writeInt(chunkEntry.getKey().z);
                        for (int i = 0; i < 256; i++) {
                            dos.writeInt(chunkEntry.getValue()[i]);
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    public static void receiveChunks(String dimension, List<MapChunkSyncPacket.ChunkData> chunks, ServerPlayer sender) {
        Map<ChunkPos, int[]> dimData = serverChunkData.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        List<MapChunkSyncPacket.ChunkData> updated = new ArrayList<>();
        
        for (MapChunkSyncPacket.ChunkData chunk : chunks) {
            ChunkPos cp = new ChunkPos(chunk.x, chunk.z);
            dimData.put(cp, chunk.colors);
            updated.add(chunk);
        }

        if (!updated.isEmpty()) {
            MapChunkSyncPacket packet = new MapChunkSyncPacket(dimension, updated);
            PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(() -> sender.level().dimension()), packet);
        }
    }

    public static void syncFullMap(ServerPlayer player, String dimension) {
        Map<ChunkPos, int[]> dimData = serverChunkData.get(dimension);
        if (dimData == null || dimData.isEmpty()) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MapChunkSyncPacket(dimension, new ArrayList<>()));
            return;
        }

        List<MapChunkSyncPacket.ChunkData> batch = new ArrayList<>();
        for (Map.Entry<ChunkPos, int[]> entry : dimData.entrySet()) {
            batch.add(new MapChunkSyncPacket.ChunkData(entry.getKey().x, entry.getKey().z, entry.getValue()));
            if (batch.size() >= 50) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MapChunkSyncPacket(dimension, new ArrayList<>(batch)));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MapChunkSyncPacket(dimension, batch));
        }
    }
}
