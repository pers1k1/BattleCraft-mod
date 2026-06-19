package com.persiki84.minimap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientMapStorage {
    private static String currentSaveName = null;

    public static void load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String saveName = "singleplayer";
        if (mc.getCurrentServer() != null) {
            saveName = mc.getCurrentServer().ip.replace(":", "_");
        }
        currentSaveName = saveName;
        ClientMapData.chunkData.clear();

        Path dir = mc.gameDirectory.toPath().resolve("minimap_data").resolve(saveName);
        String dim = mc.level.dimension().location().toString().replace(":", "_");
        Path file = dir.resolve(dim + ".dat");

        if (Files.exists(file)) {
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                int count = dis.readInt();
                for (int i = 0; i < count; i++) {
                    int x = dis.readInt();
                    int z = dis.readInt();
                    int[] colors = new int[256];
                    for (int j = 0; j < 256; j++) {
                        colors[j] = dis.readInt();
                    }
                    ClientMapData.chunkData.put(new ChunkPos(x, z), colors);
                }
            } catch (IOException ignored) {}
        }
    }

    public static void save() {
        if (currentSaveName == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Path dir = mc.gameDirectory.toPath().resolve("minimap_data").resolve(currentSaveName);
        try {
            Files.createDirectories(dir);
            String dim = mc.level.dimension().location().toString().replace(":", "_");
            Path file = dir.resolve(dim + ".dat");
            try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
                dos.writeInt(ClientMapData.chunkData.size());
                for (Map.Entry<ChunkPos, int[]> entry : ClientMapData.chunkData.entrySet()) {
                    dos.writeInt(entry.getKey().x);
                    dos.writeInt(entry.getKey().z);
                    for (int i = 0; i < 256; i++) {
                        dos.writeInt(entry.getValue()[i]);
                    }
                }
            }
        } catch (IOException ignored) {}
    }
}
