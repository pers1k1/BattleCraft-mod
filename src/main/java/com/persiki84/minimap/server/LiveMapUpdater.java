package com.persiki84.minimap.server;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveMapUpdater {
    private static final int TICK_INTERVAL = 5;
    private static final int CHUNKS_PER_PASS = 4;

    private static final Map<ResourceKey<Level>, Set<ChunkPos>> pending = new ConcurrentHashMap<>();

    private LiveMapUpdater() {}

    public static void mark(LevelAccessor accessor, BlockPos pos) {
        if (accessor instanceof ServerLevel level) {
            mark(level, new ChunkPos(pos));
        }
    }

    public static void mark(ServerLevel level, ChunkPos pos) {
        pending.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    public static void clear() {
        pending.clear();
    }

    public static void tick(MinecraftServer server) {
        if (pending.isEmpty() || server.getTickCount() % TICK_INTERVAL != 0) return;

        for (Map.Entry<ResourceKey<Level>, Set<ChunkPos>> entry : pending.entrySet()) {
            Set<ChunkPos> queue = entry.getValue();
            if (queue.isEmpty()) continue;

            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                queue.clear();
                continue;
            }

            Set<ChunkPos> batch = new HashSet<>();
            Iterator<ChunkPos> iterator = queue.iterator();
            while (iterator.hasNext() && batch.size() < CHUNKS_PER_PASS) {
                batch.add(iterator.next());
                iterator.remove();
            }

            ServerMapStorage.repaint(level, batch);
        }
    }
}
