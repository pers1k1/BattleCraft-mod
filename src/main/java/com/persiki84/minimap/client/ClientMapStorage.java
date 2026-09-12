package com.persiki84.minimap.client;

import com.persiki84.shared.WorldFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@OnlyIn(Dist.CLIENT)
public class ClientMapStorage {
    private static final String FOLDER = "minimap_data";
    private static final String SUFFIX = ".dat";
    private static final String TEMPORARY = ".tmp";
    private static final String SINGLEPLAYER = "singleplayer";
    private static final int CHUNK_COLORS = 256;
    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    private static String currentSaveName;
    private static String currentDimension;
    private static ExecutorService writer;
    private static boolean dirty;

    public static void load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        currentSaveName = saveNameOf(mc);
        ClientMapData.chunkData.clear();
        currentDimension = dimensionOf(mc);
        dirty = false;

        Path file = fileFor(mc, currentSaveName);
        if (file != null && Files.exists(file)) read(file);
    }

    private static void read(Path file) {
        try (DataInputStream source = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int count = source.readInt();
            for (int index = 0; index < count; index++) {
                int x = source.readInt();
                int z = source.readInt();
                int[] colors = new int[CHUNK_COLORS];
                for (int pixel = 0; pixel < CHUNK_COLORS; pixel++) {
                    colors[pixel] = source.readInt();
                }
                ClientMapData.chunkData.put(new ChunkPos(x, z), colors);
            }
        } catch (IOException ignored) {
        }
    }

    public static void touch() {
        dirty = true;
    }

    public static void save() {
        if (!dirty) return;

        Path file = fileFor(Minecraft.getInstance(), currentSaveName);
        if (file == null) return;

        Map<ChunkPos, int[]> snapshot = new HashMap<>(ClientMapData.chunkData);
        dirty = false;
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "battlecraft-client-map-writer");
                thread.setDaemon(true);
                return thread;
            });
        }
        writer.execute(() -> write(file, snapshot));
    }

    public static void shutdown() {
        save();
        if (writer == null) return;

        writer.shutdown();
        try {
            writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        writer = null;
    }

    private static Path fileFor(Minecraft mc, String saveName) {
        if (saveName == null || mc.level == null) return null;

        String dimension = currentDimension == null ? dimensionOf(mc) : currentDimension;
        return mc.gameDirectory.toPath().resolve(FOLDER).resolve(saveName).resolve(dimension + SUFFIX);
    }

    private static String dimensionOf(Minecraft mc) {
        return mc.level == null ? null : mc.level.dimension().location().toString().replace(":", "_");
    }

    // WHY: одиночные миры писались в общий каталог singleplayer, поэтому карта следующего мира
    // WHY: ложилась поверх карты предыдущего; имя берём от папки мира
    private static String saveNameOf(Minecraft mc) {
        if (mc.getCurrentServer() != null) return safe(mc.getCurrentServer().ip);

        IntegratedServer local = mc.getSingleplayerServer();
        if (local == null) return SINGLEPLAYER;

        Path world = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return world == null ? SINGLEPLAYER : safe(world.toString());
    }

    private static String safe(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        return cleaned.isEmpty() ? SINGLEPLAYER : cleaned;
    }

    // WHY: чанки лежат одной кучей без измерения, а файл выбирался по текущему: уход в Нижний мир
    // WHY: записывал карту Верхнего в the_nether.dat и путал регионы на экране
    public static void followDimension() {
        Minecraft mc = Minecraft.getInstance();
        String now = dimensionOf(mc);
        if (now == null || now.equals(currentDimension)) return;

        save();
        currentDimension = now;
        ClientMapData.chunkData.clear();
        MapTextureManager.init();

        Path file = fileFor(mc, currentSaveName);
        if (file != null && Files.exists(file)) read(file);
    }

    // WHY: удаление уходит в тот же однопоточный писатель, что и запись: снимок, поставленный
    // WHY: в очередь до сброса, иначе воскресит только что удалённый файл
    public static void reset() {
        Path file = fileFor(Minecraft.getInstance(), currentSaveName);
        dirty = false;
        ClientMapData.chunkData.clear();
        MapTextureManager.init();
        if (file == null) return;

        if (writer == null) {
            drop(file);
            return;
        }
        writer.execute(() -> drop(file));
    }

    private static void drop(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static void write(Path file, Map<ChunkPos, int[]> chunks) {
        Path temporary = file.resolveSibling(file.getFileName() + TEMPORARY);
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream sink = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                sink.writeInt(chunks.size());
                for (Map.Entry<ChunkPos, int[]> chunk : chunks.entrySet()) {
                    sink.writeInt(chunk.getKey().x);
                    sink.writeInt(chunk.getKey().z);
                    for (int pixel = 0; pixel < CHUNK_COLORS; pixel++) {
                        sink.writeInt(chunk.getValue()[pixel]);
                    }
                }
            }
            WorldFiles.moveIntoPlace(temporary, file);
        } catch (IOException ignored) {
        }
    }
}
