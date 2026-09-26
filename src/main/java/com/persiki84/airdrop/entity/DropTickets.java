package com.persiki84.airdrop.entity;

import com.mojang.datafixers.util.Pair;
import com.persiki84.airdrop.AirDropMod;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// WHY: тикеты ящиков переживают перезапуск: снятые на старте, они оставляли ящик вдали от игроков
// WHY: висеть в воздухе до первого прохожего. Живость ящика при загрузке тикетов не проверить -
// WHY: сущности ещё не прочитаны, поэтому ничейные тикеты снимаются после паузы на пробуждение
public final class DropTickets {
    private static final int GRACE_TICKS = 600;

    private static final Map<ResourceKey<Level>, Map<UUID, Pair<LongSet, LongSet>>> unclaimed = new HashMap<>();

    private DropTickets() {}

    public static void register() {
        ForgeChunkManager.setForcedChunkLoadingCallback(AirDropMod.MOD_ID, DropTickets::remember);
    }

    private static void remember(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        if (helper.getEntityTickets().isEmpty()) return;

        unclaimed.put(level.dimension(), new HashMap<>(helper.getEntityTickets()));
    }

    public static void claim(ServerLevel level, UUID owner, long chunk) {
        if (unclaimed.isEmpty()) return;

        Map<UUID, Pair<LongSet, LongSet>> owners = unclaimed.get(level.dimension());
        Pair<LongSet, LongSet> held = owners == null ? null : owners.get(owner);
        if (held == null) return;

        held.getFirst().remove(chunk);
        held.getSecond().remove(chunk);
    }

    public static void tick(MinecraftServer server) {
        if (unclaimed.isEmpty() || server.getTickCount() < GRACE_TICKS) return;

        unclaimed.forEach((dimension, owners) -> release(server.getLevel(dimension), owners));
        unclaimed.clear();
    }

    public static void clear() {
        unclaimed.clear();
    }

    private static void release(ServerLevel level, Map<UUID, Pair<LongSet, LongSet>> owners) {
        if (level == null) return;

        owners.forEach((owner, held) -> {
            for (long chunk : held.getFirst()) unforce(level, owner, chunk, false);
            for (long chunk : held.getSecond()) unforce(level, owner, chunk, true);
        });
    }

    private static void unforce(ServerLevel level, UUID owner, long chunk, boolean ticking) {
        ForgeChunkManager.forceChunk(level, AirDropMod.MOD_ID, owner, ChunkPos.getX(chunk), ChunkPos.getZ(chunk),
                false, ticking);
    }
}
