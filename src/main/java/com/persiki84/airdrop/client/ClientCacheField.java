package com.persiki84.airdrop.client;

import com.persiki84.airdrop.cache.CacheTier;
import com.persiki84.airdrop.network.CacheFieldPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientCacheField {
    private static final long UNKNOWN = Long.MIN_VALUE;

    public static final class Cell {
        private final BlockPos pos;
        private final AABB bounds;
        private final float drift;
        private CacheTier tier = CacheTier.COMMON;
        private boolean empty;
        private boolean missing;
        private long refillAt = UNKNOWN;
        private int refillTotal;
        private int fills;
        private long filledAt = UNKNOWN;
        private long emptiedAt = UNKNOWN;
        private BlockState fittedState;
        private AABB frame;

        private Cell(BlockPos pos) {
            this.pos = pos;
            this.bounds = new AABB(pos).inflate(1.5);
            this.drift = (Mth.getSeed(pos) & 0xFFFFL) / 65536.0f;
        }

        public BlockPos pos() { return pos; }
        public AABB bounds() { return bounds; }
        public float drift() { return drift; }
        public CacheTier tier() { return tier; }
        public boolean empty() { return empty || missing; }
        public boolean missing() { return missing; }

        public boolean waiting() {
            return refillAt != UNKNOWN && empty();
        }

        public int secondsLeft() {
            if (refillAt == UNKNOWN) return -1;
            return (int) Math.max(0L, (refillAt - System.currentTimeMillis() + 999L) / 1000L);
        }

        // WHY: кольцо пополнения показывает долю пройденного срока, а не остаток: так оно растёт к
        // WHY: моменту пополнения и замыкается ровно тогда, когда тайник наполнится
        public float refillProgress() {
            if (refillAt == UNKNOWN || refillTotal <= 0) return 0.0f;
            float left = (refillAt - System.currentTimeMillis()) / 1000.0f;
            return Math.max(0.0f, Math.min(1.0f, 1.0f - left / refillTotal));
        }

        public boolean fittedTo(BlockState state) {
            return frame != null && fittedState == state;
        }

        public void fit(BlockState state, AABB box) {
            fittedState = state;
            frame = box;
        }

        public AABB frame() {
            return frame;
        }

        public float sinceFilled() {
            return since(filledAt);
        }

        public float sinceEmptied() {
            return since(emptiedAt);
        }

        private static float since(long stamp) {
            return stamp == UNKNOWN ? Float.MAX_VALUE : (System.currentTimeMillis() - stamp) / 1000.0f;
        }
    }

    private static final Map<Long, Cell> cells = new HashMap<>();
    private static final List<Cell> listed = new ArrayList<>();
    private static boolean running = true;

    private ClientCacheField() {}

    public static List<Cell> cells() {
        return listed;
    }

    public static boolean running() {
        return running;
    }

    public static void forget() {
        cells.clear();
        listed.clear();
    }

    public static void accept(CacheFieldPacket packet) {
        Map<Long, Cell> kept = new HashMap<>();
        long now = System.currentTimeMillis();
        running = packet.running;
        for (int index = 0; index < packet.size(); index++) {
            long key = packet.positions[index];
            Cell cell = cells.get(key);
            boolean fresh = cell == null;
            if (fresh) cell = new Cell(BlockPos.of(key));
            adopt(cell, packet, index, now, fresh);
            kept.put(key, cell);
        }
        cells.clear();
        cells.putAll(kept);
        listed.clear();
        listed.addAll(cells.values());
    }

    // WHY: волна наполнения играет только на смене отметки заполнения у известного тайника: новый
    // WHY: в поле тайник уже полон, и вспышка при каждом подходе на 48 блоков читалась бы как событие
    private static void adopt(Cell cell, CacheFieldPacket packet, int index, long now, boolean fresh) {
        int flags = packet.flags[index];
        boolean nowEmpty = (flags & CacheFieldPacket.EMPTY) != 0;
        if (!fresh && packet.fills[index] != cell.fills && !nowEmpty) cell.filledAt = now;
        if (!fresh && nowEmpty && !cell.empty) cell.emptiedAt = now;

        cell.fills = packet.fills[index];
        cell.empty = nowEmpty;
        cell.missing = (flags & CacheFieldPacket.MISSING) != 0;
        cell.tier = CacheTier.byIndex(packet.tiers[index]);
        cell.refillTotal = Math.max(1, packet.refillTotal[index]);
        retime(cell, packet.refillLeft[index], now);
    }

    private static void retime(Cell cell, int left, long now) {
        if (left < 0) {
            cell.refillAt = UNKNOWN;
            return;
        }
        long wanted = now + left * 1000L;
        if (cell.refillAt == UNKNOWN || Math.abs(wanted - cell.refillAt) > 1500L) cell.refillAt = wanted;
    }
}
