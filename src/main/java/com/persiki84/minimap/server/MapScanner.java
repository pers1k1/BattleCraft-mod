package com.persiki84.minimap.server;

import com.persiki84.minimap.MapPainter;
import com.persiki84.minimap.network.MapChunkSyncPacket;
import com.persiki84.minimap.network.PacketHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MapScanner {
    public static final int MIN_RADIUS = 32;
    public static final int MAX_RADIUS = 512;
    public static final int RADIUS_STEP = 32;
    public static final int DEFAULT_RADIUS = 256;

    private static final int BLOCKS_PER_CHUNK = 16;
    private static final long BUDGET_NANOS = 8_000_000L;
    private static final int BATCH = 50;
    private static final int PROGRESS_EVERY = 64;

    private static final Map<UUID, Scan> active = new ConcurrentHashMap<>();

    private MapScanner() {}

    public static void start(ServerPlayer player, int radiusBlocks) {
        int radius = Mth.clamp(radiusBlocks, MIN_RADIUS, MAX_RADIUS);
        Scan scan = new Scan(player.level().dimension(),
                ServerMapStorage.dimensionKey(player.serverLevel()),
                around(player.chunkPosition(), Mth.ceil(radius / (float) BLOCKS_PER_CHUNK)));

        Scan previous = active.put(player.getUUID(), scan);
        if (previous != null) flush(player, previous);
        player.displayClientMessage(Component.translatable("minimap.scan.started", radius, scan.total()), false);
    }

    public static boolean cancel(ServerPlayer player) {
        Scan scan = active.remove(player.getUUID());
        if (scan == null) return false;

        flush(player, scan);
        return true;
    }

    public static void clear() {
        active.clear();
    }

    public static int remaining(ServerPlayer player) {
        Scan scan = active.get(player.getUUID());
        return scan == null ? 0 : scan.remaining();
    }

    public static int total(ServerPlayer player) {
        Scan scan = active.get(player.getUUID());
        return scan == null ? 0 : scan.total();
    }

    public static void tick(MinecraftServer server) {
        if (active.isEmpty()) return;

        long deadline = System.nanoTime() + BUDGET_NANOS;
        for (Map.Entry<UUID, Scan> entry : active.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Scan scan = entry.getValue();
            if (player == null || !scan.follows(player)) {
                active.remove(entry.getKey());
                continue;
            }

            advance(player, scan, deadline);
            report(player, scan);
            if (scan.remaining() == 0) finish(player, entry.getKey(), scan);
        }
    }

    // WHY: чанк за границей прогрузки генерируется здесь же, в серверном потоке, и стоит десятки
    // WHY: миллисекунд, поэтому проход мерится временем, а не числом чанков; один берём всегда,
    // WHY: иначе на медленном тике задача не сдвинется вовсе
    private static void advance(ServerPlayer player, Scan scan, long deadline) {
        ServerLevel level = player.serverLevel();
        do {
            ChunkPos pos = scan.next();
            if (pos == null) return;

            int[] colors = MapPainter.paint(level.getChunk(pos.x, pos.z));
            if (!MapPainter.blank(colors)) {
                scan.batch.add(new MapChunkSyncPacket.ChunkData(pos.x, pos.z, colors));
            }
            if (scan.batch.size() >= BATCH) flush(player, scan);
        } while (System.nanoTime() < deadline);
    }

    private static void flush(ServerPlayer player, Scan scan) {
        if (scan.batch.isEmpty()) return;

        List<MapChunkSyncPacket.ChunkData> sending = new ArrayList<>(scan.batch);
        scan.batch.clear();

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new MapChunkSyncPacket(scan.dimensionKey, sending));
        ServerMapStorage.shareWithTeam(player, scan.dimensionKey, sending);
    }

    private static void report(ServerPlayer player, Scan scan) {
        if (scan.painted - scan.reported < PROGRESS_EVERY) return;

        scan.reported = scan.painted;
        player.displayClientMessage(
                Component.translatable("minimap.scan.progress", scan.painted, scan.total()), true);
    }

    private static void finish(ServerPlayer player, UUID id, Scan scan) {
        flush(player, scan);
        active.remove(id);
        player.displayClientMessage(Component.translatable("minimap.scan.done", scan.total()), false);
    }

    // WHY: обход идёт от центра наружу, чтобы прогруженное появлялось вокруг игрока сразу,
    // WHY: а не полосой с угла области
    private static List<ChunkPos> around(ChunkPos center, int radiusChunks) {
        List<ChunkPos> queue = new ArrayList<>();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                queue.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        queue.sort(Comparator.comparingLong(pos -> distanceSquared(center, pos)));
        return queue;
    }

    private static long distanceSquared(ChunkPos center, ChunkPos pos) {
        long dx = pos.x - center.x;
        long dz = pos.z - center.z;
        return dx * dx + dz * dz;
    }

    private static final class Scan {
        private final ResourceKey<Level> dimension;
        private final String dimensionKey;
        private final List<ChunkPos> queue;
        private final List<MapChunkSyncPacket.ChunkData> batch = new ArrayList<>();
        private int painted;
        private int reported;

        private Scan(ResourceKey<Level> dimension, String dimensionKey, List<ChunkPos> queue) {
            this.dimension = dimension;
            this.dimensionKey = dimensionKey;
            this.queue = queue;
        }

        private boolean follows(ServerPlayer player) {
            return player.level().dimension().equals(dimension);
        }

        private ChunkPos next() {
            if (painted >= queue.size()) return null;

            return queue.get(painted++);
        }

        private int total() {
            return queue.size();
        }

        private int remaining() {
            return queue.size() - painted;
        }
    }
}
