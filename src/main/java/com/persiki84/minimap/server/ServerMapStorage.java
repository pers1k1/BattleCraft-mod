package com.persiki84.minimap.server;

import com.persiki84.minimap.MapPainter;
import com.persiki84.minimap.network.MapChunkSyncPacket;
import com.persiki84.minimap.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.PacketDistributor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ServerMapStorage {
    private static final String FOLDER = "minimap_data";
    private static final String TEAMS = "teams";
    private static final String SHARED = "shared";
    private static final String SUFFIX = ".dat";
    private static final int CHUNK_COLORS = 256;
    private static final int BATCH_SIZE = 50;
    private static final int TRACK_INTERVAL = 20;
    private static final int SHUTDOWN_WAIT_SECONDS = 10;
    private static final int CHUNK_REACH = 32;

    private static final Map<String, Map<String, Map<ChunkPos, int[]>>> teamChunkData = new ConcurrentHashMap<>();
    private static final Map<String, Map<ChunkPos, int[]>> sharedChunkData = new ConcurrentHashMap<>();
    private static final Map<String, Map<ChunkPos, int[]>> uploadBuffer = new ConcurrentHashMap<>();
    private static final Set<String> sharedUnsaved = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> lastTeam = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> unsaved = new ConcurrentHashMap<>();
    private static ExecutorService writer;
    private static MinecraftServer currentServer;

    public static void load(MinecraftServer server) {
        currentServer = server;
        teamChunkData.clear();
        sharedChunkData.clear();
        sharedUnsaved.clear();
        lastTeam.clear();
        unsaved.clear();
        uploadBuffer.clear();
        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "battlecraft-map-writer");
            thread.setDaemon(true);
            return thread;
        });

        loadShared(folder(SHARED));

        Path root = folder(TEAMS);
        if (!Files.exists(root)) return;

        try (Stream<Path> teams = Files.list(root)) {
            teams.filter(Files::isDirectory).forEach(ServerMapStorage::loadTeam);
        } catch (IOException ignored) {
        }
    }

    private static void loadShared(Path folder) {
        if (!Files.exists(folder)) return;

        try (Stream<Path> files = Files.list(folder)) {
            files.filter(file -> file.toString().endsWith(SUFFIX)).forEach(file -> {
                Map<ChunkPos, int[]> chunks = readChunks(file);
                if (chunks == null) return;

                sharedChunkData.put(file.getFileName().toString().replace(SUFFIX, ""), chunks);
            });
        } catch (IOException ignored) {
        }
    }

    private static void loadTeam(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(file -> file.toString().endsWith(SUFFIX)).forEach(ServerMapStorage::readDimension);
        } catch (IOException ignored) {
        }
    }

    private static void readDimension(Path file) {
        String dimension = file.getFileName().toString().replace(SUFFIX, "");
        String team = ownerOf(file);
        Map<ChunkPos, int[]> chunks = readChunks(file);
        if (team == null || chunks == null) return;

        teamChunkData.computeIfAbsent(team, key -> new ConcurrentHashMap<>()).put(dimension, chunks);
    }

    private static String ownerOf(Path file) {
        try (DataInputStream source = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            return source.readUTF();
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static Map<ChunkPos, int[]> readChunks(Path file) {
        Map<ChunkPos, int[]> chunks = new ConcurrentHashMap<>();
        try (DataInputStream source = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            source.readUTF();
            int count = source.readInt();
            for (int index = 0; index < count; index++) {
                int x = source.readInt();
                int z = source.readInt();
                int[] colors = new int[CHUNK_COLORS];
                for (int pixel = 0; pixel < CHUNK_COLORS; pixel++) {
                    colors[pixel] = source.readInt();
                }
                chunks.put(new ChunkPos(x, z), colors);
            }
        } catch (IOException unreadable) {
            return null;
        }
        return chunks;
    }

    public static void save() {
        if (currentServer == null) return;

        saveShared();
        if (unsaved.isEmpty()) return;

        Path root = folder(TEAMS);
        Map<String, Set<String>> pending = new java.util.HashMap<>();
        for (Map.Entry<String, Set<String>> entry : unsaved.entrySet()) {
            pending.put(entry.getKey(), new java.util.HashSet<>(entry.getValue()));
        }
        unsaved.clear();

        if (writer == null) {
            flush(root, pending);
            return;
        }
        writer.execute(() -> flush(root, pending));
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

    private static void flush(Path root, Map<String, Set<String>> pending) {
        for (Map.Entry<String, Set<String>> team : pending.entrySet()) {
            Map<String, Map<ChunkPos, int[]>> dimensions = teamChunkData.get(team.getKey());
            if (dimensions == null) continue;

            Path folder = root.resolve(fileName(team.getKey()));
            for (String dimension : team.getValue()) {
                Map<ChunkPos, int[]> chunks = dimensions.get(dimension);
                if (chunks == null) continue;

                try {
                    Files.createDirectories(folder);
                    writeChunks(folder.resolve(fileName(dimension) + SUFFIX), team.getKey(), chunks);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void markUnsaved(String team, String dimension) {
        unsaved.computeIfAbsent(team, key -> ConcurrentHashMap.newKeySet()).add(dimension);
    }

    private static void saveShared() {
        if (sharedUnsaved.isEmpty()) return;

        Path target = folder(SHARED);
        List<String> pending = new ArrayList<>(sharedUnsaved);
        sharedUnsaved.clear();

        Runnable job = () -> {
            for (String dimension : pending) {
                Map<ChunkPos, int[]> chunks = sharedChunkData.get(dimension);
                if (chunks == null) continue;

                try {
                    Files.createDirectories(target);
                    writeChunks(target.resolve(fileName(dimension) + SUFFIX), SHARED, chunks);
                } catch (IOException ignored) {
                }
            }
        };
        if (writer == null) {
            job.run();
            return;
        }
        writer.execute(job);
    }

    private static int[] shared(String dimension, ChunkPos pos, int[] colors) {
        for (Map<String, Map<ChunkPos, int[]>> dimensions : teamChunkData.values()) {
            Map<ChunkPos, int[]> known = dimensions.get(dimension);
            int[] existing = known == null ? null : known.get(pos);
            if (existing != null && Arrays.equals(existing, colors)) return existing;
        }
        return colors;
    }

    // WHY: писатель идёт по живой карте, в которую тик кладёт новые чанки: счётчик читался до
    // WHY: обхода и расходился с телом, а лишние записи после него читатель молча терял
    private static void writeChunks(Path file, String team, Map<ChunkPos, int[]> live) throws IOException {
        List<Map.Entry<ChunkPos, int[]>> chunks = new ArrayList<>(live.entrySet());

        try (DataOutputStream sink = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            sink.writeUTF(team);
            sink.writeInt(chunks.size());
            for (Map.Entry<ChunkPos, int[]> chunk : chunks) {
                sink.writeInt(chunk.getKey().x);
                sink.writeInt(chunk.getKey().z);
                for (int pixel = 0; pixel < CHUNK_COLORS; pixel++) {
                    sink.writeInt(chunk.getValue()[pixel]);
                }
            }
        }
    }

    public static void repaint(ServerLevel level, Collection<ChunkPos> positions) {
        if (teamChunkData.isEmpty()) return;

        String dimension = dimensionKey(level);
        Map<ChunkPos, int[]> painted = new ConcurrentHashMap<>();
        for (ChunkPos pos : positions) {
            if (!level.hasChunk(pos.x, pos.z)) continue;
            painted.put(pos, MapPainter.paint(level.getChunk(pos.x, pos.z)));
        }
        if (painted.isEmpty()) return;

        for (Map.Entry<String, Map<String, Map<ChunkPos, int[]>>> team : teamChunkData.entrySet()) {
            Map<ChunkPos, int[]> known = team.getValue().get(dimension);
            if (known == null || known.isEmpty()) continue;

            List<MapChunkSyncPacket.ChunkData> updated = new ArrayList<>();
            for (Map.Entry<ChunkPos, int[]> entry : painted.entrySet()) {
                int[] previous = known.get(entry.getKey());
                if (previous == null || Arrays.equals(previous, entry.getValue())) continue;

                known.put(entry.getKey(), shared(dimension, entry.getKey(), entry.getValue()));
                updated.add(new MapChunkSyncPacket.ChunkData(entry.getKey().x, entry.getKey().z, entry.getValue()));
            }
            if (updated.isEmpty()) continue;

            markUnsaved(team.getKey(), dimension);
            sendToTeam(team.getKey(), level.getServer(), null, new MapChunkSyncPacket(dimension, updated));
        }
    }

    public static String dimensionKey(ServerLevel level) {
        return level.dimension().location().toString().replace(":", "_");
    }

    // WHY: измерение и координаты приходят от клиента: чужое имя завело бы свой файл и свою
    // WHY: кучу в памяти, а координата с другого конца мира копилась бы вечно
    public static void receiveChunks(String dimension, List<MapChunkSyncPacket.ChunkData> chunks, ServerPlayer sender) {
        if (chunks.isEmpty()) return;
        if (!dimensionKey(sender.serverLevel()).equals(dimension)) return;

        ChunkPos origin = sender.chunkPosition();
        List<MapChunkSyncPacket.ChunkData> near = new ArrayList<>();
        for (MapChunkSyncPacket.ChunkData chunk : chunks) {
            if (Math.abs(chunk.x - origin.x) > CHUNK_REACH || Math.abs(chunk.z - origin.z) > CHUNK_REACH) continue;

            near.add(chunk);
        }
        shareWithTeam(sender, dimension, near);
    }

    public static void shareWithTeam(ServerPlayer sender, String dimension,
                                     List<MapChunkSyncPacket.ChunkData> chunks) {
        String team = teamKey(sender);
        if (team == null || chunks.isEmpty()) return;

        List<MapChunkSyncPacket.ChunkData> updated = absorb(team, dimension, chunks);
        if (updated.isEmpty()) return;

        markUnsaved(team, dimension);
        sendToTeam(team, sender.server, sender, new MapChunkSyncPacket(dimension, updated));
    }

    private static List<MapChunkSyncPacket.ChunkData> absorb(String team, String dimension,
                                                             List<MapChunkSyncPacket.ChunkData> chunks) {
        Map<ChunkPos, int[]> known = teamChunkData
                .computeIfAbsent(team, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(dimension, key -> new ConcurrentHashMap<>());

        List<MapChunkSyncPacket.ChunkData> updated = new ArrayList<>();
        for (MapChunkSyncPacket.ChunkData chunk : chunks) {
            ChunkPos pos = new ChunkPos(chunk.x, chunk.z);
            int[] previous = known.get(pos);
            if (previous != null && Arrays.equals(previous, chunk.colors)) continue;

            known.put(pos, shared(dimension, pos, chunk.colors));
            updated.add(chunk);
        }
        return updated;
    }

    // WHY: общая карта, залитая админом, достаётся каждому входящему независимо от команды,
    // WHY: поэтому идёт первой, а поверх неё ложится то, что команда прогрузила сама
    public static void syncFullMap(ServerPlayer player, String dimension) {
        sendChunks(player, dimension, sharedChunkData.get(dimension));

        String team = teamKey(player);
        if (team == null) return;

        Map<String, Map<ChunkPos, int[]>> dimensions = teamChunkData.get(team);
        sendChunks(player, dimension, dimensions == null ? null : dimensions.get(dimension));
    }

    private static void sendChunks(ServerPlayer player, String dimension, Map<ChunkPos, int[]> known) {
        if (known == null || known.isEmpty()) return;

        List<MapChunkSyncPacket.ChunkData> batch = new ArrayList<>();
        for (Map.Entry<ChunkPos, int[]> entry : known.entrySet()) {
            batch.add(new MapChunkSyncPacket.ChunkData(entry.getKey().x, entry.getKey().z, entry.getValue()));
            if (batch.size() < BATCH_SIZE) continue;

            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new MapChunkSyncPacket(dimension, new ArrayList<>(batch)));
            batch.clear();
        }
        if (!batch.isEmpty()) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new MapChunkSyncPacket(dimension, batch));
        }
    }

    public static void syncFullMap(ServerPlayer player) {
        syncFullMap(player, dimensionKey(player.serverLevel()));
    }

    public static void trackTeams(MinecraftServer server) {
        if (server.getTickCount() % TRACK_INTERVAL != 0) return;

        lastTeam.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String team = teamKey(player);
            if (Objects.equals(team, lastTeam.get(player.getUUID()))) continue;

            if (team == null) {
                lastTeam.remove(player.getUUID());
                continue;
            }
            lastTeam.put(player.getUUID(), team);
            syncFullMap(player);
        }
    }

    // WHY: карта грузится пачками, поэтому раздача и запись идут по флагу последней пачки, а не
    // WHY: на каждый пакет: иначе полная карта рассылалась бы игрокам тысячи раз подряд
    public static void receiveUpload(ServerPlayer sender, MapShareScope scope, String target,
                                     String dimension, List<MapChunkSyncPacket.ChunkData> chunks,
                                     boolean last) {
        Map<ChunkPos, int[]> known = uploadTarget(scope, target, dimension);
        if (known == null) return;

        for (MapChunkSyncPacket.ChunkData chunk : chunks) {
            known.put(new ChunkPos(chunk.x, chunk.z), chunk.colors);
        }
        if (!last) return;

        if (scope == MapShareScope.EVERYONE) sharedUnsaved.add(dimension);
        if (scope == MapShareScope.TEAM) markUnsaved(target, dimension);
        deliverUpload(sender, scope, target, dimension, known);
    }

    private static Map<ChunkPos, int[]> uploadTarget(MapShareScope scope, String target, String dimension) {
        if (scope == MapShareScope.EVERYONE) {
            return sharedChunkData.computeIfAbsent(dimension, key -> new ConcurrentHashMap<>());
        }
        if (scope == MapShareScope.TEAM) {
            if (target.isEmpty()) return null;

            return teamChunkData.computeIfAbsent(target, key -> new ConcurrentHashMap<>())
                    .computeIfAbsent(dimension, key -> new ConcurrentHashMap<>());
        }
        return uploadBuffer.computeIfAbsent(target, key -> new ConcurrentHashMap<>());
    }

    private static void deliverUpload(ServerPlayer sender, MapShareScope scope, String target,
                                      String dimension, Map<ChunkPos, int[]> known) {
        if (scope == MapShareScope.PLAYER) {
            ServerPlayer receiver = sender.server.getPlayerList().getPlayerByName(target);
            uploadBuffer.remove(target);
            if (receiver != null) sendChunks(receiver, dimension, known);
            return;
        }

        for (ServerPlayer member : sender.server.getPlayerList().getPlayers()) {
            if (scope == MapShareScope.TEAM && !target.equals(teamKey(member))) continue;

            sendChunks(member, dimension, known);
        }
    }

    public static int resetShared(String dimension) {
        Map<ChunkPos, int[]> known = sharedChunkData.remove(dimension);
        sharedUnsaved.remove(dimension);
        dropFile(folder(SHARED).resolve(fileName(dimension) + SUFFIX));
        return known == null ? 0 : known.size();
    }

    public static int resetTeams(String dimension) {
        int cleared = 0;
        for (Map.Entry<String, Map<String, Map<ChunkPos, int[]>>> team : teamChunkData.entrySet()) {
            if (team.getValue().remove(dimension) == null) continue;

            cleared++;
            Set<String> pending = unsaved.get(team.getKey());
            if (pending != null) pending.remove(dimension);
            dropFile(folder(TEAMS).resolve(fileName(team.getKey())).resolve(fileName(dimension) + SUFFIX));
        }
        return cleared;
    }

    private static Path folder(String branch) {
        return currentServer.getWorldPath(LevelResource.ROOT).resolve(FOLDER).resolve(branch);
    }

    // WHY: удаление ставится в ту же однопоточную очередь, что и запись: снимок, поставленный
    // WHY: в неё до сброса, иначе перепишет файл сразу после удаления
    private static void dropFile(Path file) {
        Runnable job = () -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
        };
        if (writer == null) {
            job.run();
            return;
        }
        writer.execute(job);
    }

    public static int sharedSize(String dimension) {
        Map<ChunkPos, int[]> known = sharedChunkData.get(dimension);
        return known == null ? 0 : known.size();
    }

    private static void sendToTeam(String team, MinecraftServer server, ServerPlayer skip,
                                   MapChunkSyncPacket packet) {
        for (ServerPlayer member : server.getPlayerList().getPlayers()) {
            if (member == skip || !team.equals(teamKey(member))) continue;
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> member), packet);
        }
    }

    private static String teamKey(ServerPlayer player) {
        Team team = player.getTeam();
        return team == null ? null : team.getName();
    }

    private static String fileName(String team) {
        return team.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
