package com.persiki84.airdrop.network;

import com.persiki84.airdrop.cache.CacheTicker;
import com.persiki84.airdrop.cache.LootCache;
import com.persiki84.airdrop.cache.LootCaches;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// WHY: игроку уходят только тайники в 48 блоках и только видимые: полный список это карта всего
// WHY: лута сборки, то есть рентген, выданный модом добровольно
public final class CacheBroadcast {
    public static final int FIELD_LIMIT = 64;

    private static final double FIELD_RADIUS = 48.0;
    private static final int PERIOD_TICKS = 20;
    private static final long FORCE_MILLIS = 15000L;
    private static final int TICKS_PER_SECOND = 20;

    private static final Map<UUID, Long> sentSignature = new HashMap<>();
    private static final Map<UUID, Long> sentAt = new HashMap<>();

    private CacheBroadcast() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD_TICKS != 0) return;

        long now = CacheTicker.clock(server);
        boolean running = CacheTicker.running();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player, now, running);
        }
    }

    public static void forget(UUID player) {
        sentSignature.remove(player);
        sentAt.remove(player);
    }

    public static void clear() {
        sentSignature.clear();
        sentAt.clear();
    }

    private static void sync(ServerPlayer player, long now, boolean running) {
        Field field = collect(player, now, running);
        UUID id = player.getUUID();
        long millis = System.currentTimeMillis();
        Long previous = sentSignature.get(id);
        Long stamp = sentAt.get(id);
        boolean stale = stamp == null || millis - stamp > FORCE_MILLIS;
        if (!stale && previous != null && previous == field.signature) return;

        sentSignature.put(id, field.signature);
        sentAt.put(id, millis);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), field.packet(running));
    }

    private static Field collect(ServerPlayer player, long now, boolean running) {
        Field field = new Field(running);
        String dimension = LootCaches.dimensionOf(player.level());
        double limit = FIELD_RADIUS * FIELD_RADIUS;
        for (LootCache cache : LootCaches.view()) {
            if (field.count >= FIELD_LIMIT) break;
            if (!cache.tier().shown() || !cache.dimension().equals(dimension)) continue;
            if (cache.pos().distToCenterSqr(player.position()) > limit) continue;

            field.add(cache, now);
        }
        return field;
    }

    private static final class Field {
        private final long[] positions = new long[FIELD_LIMIT];
        private final byte[] tiers = new byte[FIELD_LIMIT];
        private final byte[] flags = new byte[FIELD_LIMIT];
        private final int[] left = new int[FIELD_LIMIT];
        private final int[] total = new int[FIELD_LIMIT];
        private final int[] fills = new int[FIELD_LIMIT];
        private int count;
        private long signature;

        private Field(boolean running) {
            signature = running ? 7L : 3L;
        }

        private void add(LootCache cache, long now) {
            long due = cache.refillAt();
            positions[count] = cache.pos().asLong();
            tiers[count] = (byte) cache.tier().ordinal();
            flags[count] = (byte) ((cache.empty() ? CacheFieldPacket.EMPTY : 0)
                    | (cache.missing() ? CacheFieldPacket.MISSING : 0));
            left[count] = due == LootCache.NEVER ? -1 : (int) Math.max(0L, (due - now) / TICKS_PER_SECOND);
            total[count] = cache.refillSeconds();
            fills[count] = Long.hashCode(cache.filledAt());
            signature = signature * 31L + positions[count];
            signature = signature * 31L + tiers[count] * 7L + flags[count];
            signature = signature * 31L + fills[count] + due;
            count++;
        }

        private CacheFieldPacket packet(boolean running) {
            return new CacheFieldPacket(running, Arrays.copyOf(positions, count),
                    Arrays.copyOf(tiers, count), Arrays.copyOf(flags, count),
                    Arrays.copyOf(left, count), Arrays.copyOf(total, count),
                    Arrays.copyOf(fills, count));
        }
    }
}
