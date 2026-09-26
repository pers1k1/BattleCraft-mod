package com.persiki84.airdrop.cache;

import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.loot.LootRoller;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

// WHY: тайник заполняется лениво - когда его чанк прогружен, а не всем списком на старте матча:
// WHY: прогрузка чанков сотни тайников разом в одном тике стоила бы секунды главного потока,
// WHY: а игрок всё равно не дойдёт до тайника раньше, чем его чанк прогрузится
public final class CacheTicker {
    public static final int MANUAL = -1;
    public static final int QUEUED = -2;

    private static final int PERIOD_TICKS = 20;
    private static final int SAVE_TICKS = 200;
    private static final double LOOTED_SHARE = 0.25;

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
            settleOutsideMatch(level, cache, container, now);
            return;
        }
        if (cache.filledMatch() != LootCaches.serial()) {
            fill(level, cache, container, now, LootCaches.serial());
            return;
        }
        if (cache.observe(looted(cache, container), now)) LootCaches.markDirty();

        long due = cache.refillAt();
        if (due != LootCache.NEVER && now >= due) fill(level, cache, container, now, LootCaches.serial());
    }

    private static void settleOutsideMatch(ServerLevel level, LootCache cache, Container container, long now) {
        if (cache.filledMatch() == QUEUED) {
            fill(level, cache, container, now, MANUAL);
            return;
        }
        clearAfterMatch(cache, container);
    }

    // WHY: пополнение «после разграбления» ждало полностью пустого контейнера, и один оставленный
    // WHY: предмет выключал его до конца матча: разграбленным считается тайник, где осталась
    // WHY: четверть выложенных стопок или меньше. Без записи о выложенном - прежнее правило
    private static boolean looted(LootCache cache, Container container) {
        if (cache.refill() != RefillMode.LOOTED || cache.stocked() <= 0) return container.isEmpty();
        return occupiedSlots(container) <= cache.stocked() * LOOTED_SHARE;
    }

    private static int occupiedSlots(Container container) {
        int occupied = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) occupied++;
        }
        return occupied;
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

    // WHY: заполнение всего списка разом грузило бы чанк каждого далёкого тайника синхронно:
    // WHY: невыгруженный тайник получает отметку и заполняется тикером, когда его чанк прогрузится
    public static boolean queueIfUnloaded(MinecraftServer server, LootCache cache) {
        ServerLevel level = levelOf(server, cache);
        if (level == null || level.isLoaded(cache.pos())) return false;

        cache.queueFill(QUEUED);
        LootCaches.markDirty();
        return true;
    }

    public static boolean emptyNow(MinecraftServer server, LootCache cache) {
        ServerLevel level = levelOf(server, cache);
        Container container = level == null ? null : CacheContainers.resolve(level, cache.pos());
        if (container == null) return false;

        empty(cache, container);
        return true;
    }

    // WHY: снятый с учёта тайник остаётся обычным сундуком без защиты, и лут в нём доставался бы
    // WHY: первому прохожему: при снятии он вычищается, если чанк прогружен и грузить его не нужно
    public static void emptyIfLoaded(MinecraftServer server, LootCache cache) {
        ServerLevel level = levelOf(server, cache);
        if (level == null || !level.isLoaded(cache.pos())) return;

        Container container = CacheContainers.resolve(level, cache.pos());
        if (container == null) return;

        container.clearContent();
        container.setChanged();
    }

    private static void fill(ServerLevel level, LootCache cache, Container container, long now, int match) {
        int placed = LootRoller.fill(container, LootTables.getOrEmpty(cache.table()), level.random);
        cache.filled(match, now, placed);
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
        ResourceKey<Level> dimension = LootCaches.dimensionKey(cache.dimension());
        return dimension == null ? null : server.getLevel(dimension);
    }
}
