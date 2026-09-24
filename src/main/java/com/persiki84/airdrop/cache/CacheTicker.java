package com.persiki84.airdrop.cache;

import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.loot.LootRoller;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

// WHY: тайник заполняется лениво - когда его чанк прогружен, а не всем списком на старте матча:
// WHY: прогрузка чанков сотни тайников разом в одном тике стоила бы секунды главного потока,
// WHY: а игрок всё равно не дойдёт до тайника раньше, чем его чанк прогрузится
public final class CacheTicker {
    public static final int MANUAL = -1;

    private static final int PERIOD_TICKS = 20;
    private static final int SAVE_TICKS = 200;

    private CacheTicker() {}

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % PERIOD_TICKS == 0) settleLoaded(server);
        if (tick % SAVE_TICKS == 0) LootCaches.saveIfDirty();
    }

    public static void settleLoaded(MinecraftServer server) {
        long now = clock(server);
        boolean running = running();
        for (LootCache cache : LootCaches.view()) {
            ServerLevel level = levelOf(server, cache);
            if (level == null || !level.isLoaded(cache.pos())) continue;

            settle(level, cache, now, running);
        }
    }

    public static void settle(ServerLevel level, LootCache cache, long now, boolean running) {
        Container container = CacheContainers.resolve(level, cache.pos());
        cache.missing(container == null);
        if (container == null) return;

        if (!running) {
            clearAfterMatch(cache, container);
            return;
        }
        if (cache.filledMatch() != LootCaches.serial()) {
            fill(level, cache, container, now, LootCaches.serial());
            return;
        }
        if (cache.observe(container.isEmpty(), now)) LootCaches.markDirty();

        long due = cache.refillAt();
        if (due != LootCache.NEVER && now >= due) fill(level, cache, container, now, LootCaches.serial());
    }

    // WHY: лут прошлого матча, оставшийся в лобби, это бесплатная добыча до старта следующего:
    // WHY: ручное заполнение оператором вне матча помечено отдельно и под эту чистку не попадает
    private static void clearAfterMatch(LootCache cache, Container container) {
        if (cache.cleared() || cache.filledMatch() <= 0) return;
        if (!AirDropConfig.SERVER.cacheClearOnMatchEnd.get()) return;

        empty(cache, container);
    }

    public static boolean fillNow(MinecraftServer server, LootCache cache) {
        ServerLevel level = levelOf(server, cache);
        Container container = level == null ? null : CacheContainers.resolve(level, cache.pos());
        if (container == null) return false;

        fill(level, cache, container, clock(server), running() ? LootCaches.serial() : MANUAL);
        return true;
    }

    public static boolean emptyNow(MinecraftServer server, LootCache cache) {
        ServerLevel level = levelOf(server, cache);
        Container container = level == null ? null : CacheContainers.resolve(level, cache.pos());
        if (container == null) return false;

        empty(cache, container);
        return true;
    }

    private static void fill(ServerLevel level, LootCache cache, Container container, long now, int match) {
        int placed = LootRoller.fill(container, LootTables.getOrEmpty(cache.table()), level.random);
        cache.filled(match, now, placed == 0);
        LootCaches.markDirty();
    }

    private static void empty(LootCache cache, Container container) {
        container.clearContent();
        container.setChanged();
        cache.clearedOut();
        LootCaches.markDirty();
    }

    public static boolean running() {
        return BattleCraftManager.getInstance().matchRunning();
    }

    public static long clock(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static ServerLevel levelOf(MinecraftServer server, LootCache cache) {
        ResourceLocation id = ResourceLocation.tryParse(cache.dimension());
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }
}
