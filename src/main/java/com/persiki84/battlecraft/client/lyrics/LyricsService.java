package com.persiki84.battlecraft.client.lyrics;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.media.MediaTrack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// WHY: на быстром перелистывании каждый трек просил бы текст у сервера. Запрос уходит только для
// WHY: трека, который продержался SETTLE_MS, по одному за раз, не чаще раза в GAP_MS и не больше
// WHY: MINUTE_LIMIT в минуту; на отказ сервера пауза растёт вдвое. Ответ кладётся под ключом своего
// WHY: трека, и остров берёт его только для того же ключа, поэтому опоздавший ответ чужой песни не
// WHY: встаёт поверх текущей
public final class LyricsService {
    private static final long DISK_SETTLE_MS = 250L;
    private static final long SETTLE_MS = 1200L;
    private static final long GAP_MS = 3000L;
    private static final int MINUTE_LIMIT = 10;
    private static final long MINUTE_MS = 60_000L;
    private static final long RETRY_BASE_MS = 15_000L;
    private static final long RETRY_MAX_MS = 120_000L;
    private static final int MEMORY_LIMIT = 64;

    private static final Object lock = new Object();
    private static final Map<String, Lyrics> memory = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Lyrics> eldest) {
            return size() > MEMORY_LIMIT;
        }
    };
    private static final ArrayDeque<Long> recent = new ArrayDeque<>();

    private static MediaTrack queried = MediaTrack.NONE;
    private static TrackQuery query;
    private static String wanted = "";
    private static long wantedAt;
    private static boolean diskChecked;
    private static boolean flying;
    private static long quietUntil;
    private static int failures;
    private static ExecutorService worker;
    private static LyricsSources sources;
    private static LyricsCache cache;

    private LyricsService() {}

    // WHY: лимиты и паузы считаются по монотонным часам: перевод системных часов назад иначе
    // WHY: запирал бы запросы до тех пор, пока время не догонит прошлую отметку
    public static Lyrics lyrics(MediaTrack track) {
        long now = System.nanoTime() / 1_000_000L;
        if (query == null || !track.sameTrack(queried) || query.stale(track.durationMs())) {
            queried = track;
            query = TrackQuery.of(track);
        }
        if (track.blind() || !query.searchable()) return Lyrics.NONE;

        synchronized (lock) {
            Lyrics known = memory.get(query.key());
            if (known != null) return known;
            schedule(query, now);
        }
        return Lyrics.NONE;
    }

    private static void schedule(TrackQuery target, long now) {
        String key = target.key();
        if (!key.equals(wanted)) {
            wanted = key;
            wantedAt = now;
            diskChecked = false;
            return;
        }
        if (flying) return;
        if (!diskChecked && now - wantedAt >= DISK_SETTLE_MS) {
            launch(() -> fromDisk(target, key));
        } else if (diskChecked && now - wantedAt >= SETTLE_MS && now >= quietUntil && allowed(now)) {
            recent.addLast(now);
            launch(() -> fromNetwork(target, key));
        }
    }

    private static boolean allowed(long now) {
        while (!recent.isEmpty() && now - recent.peekFirst() > MINUTE_MS) recent.pollFirst();
        Long last = recent.peekLast();
        return recent.size() < MINUTE_LIMIT && (last == null || now - last >= GAP_MS);
    }

    private static void launch(Runnable task) {
        flying = true;
        if (worker == null) worker = Executors.newSingleThreadExecutor(LyricsService::thread);
        worker.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException error) {
                BattleCraftMod.LOGGER.warn("lyrics lookup failed: {}", error.toString());
            } finally {
                synchronized (lock) {
                    flying = false;
                }
            }
        });
    }

    private static Thread thread(Runnable body) {
        Thread thread = new Thread(body, "battlecraft-lyrics");
        thread.setDaemon(true);
        return thread;
    }

    private static void fromDisk(TrackQuery target, String key) {
        LyricsOutcome stored = cache().read(key, target.durationMs(), System.currentTimeMillis());
        synchronized (lock) {
            if (stored != null) memory.put(key, stored.lyrics());
            if (key.equals(wanted)) diskChecked = true;
        }
    }

    private static void fromNetwork(TrackQuery target, String key) {
        LyricsOutcome outcome;
        try {
            outcome = sources().find(target);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return;
        }
        if (outcome.kind() != LyricsOutcome.Kind.FAILED) cache().write(key, outcome, System.currentTimeMillis());
        synchronized (lock) {
            settle(key, outcome, System.nanoTime() / 1_000_000L);
        }
    }

    private static void settle(String key, LyricsOutcome outcome, long now) {
        if (outcome.kind() == LyricsOutcome.Kind.FAILED) {
            failures = Math.min(failures + 1, 10);
            quietUntil = now + Math.min(RETRY_MAX_MS, RETRY_BASE_MS << (failures - 1));
            return;
        }
        failures = 0;
        memory.put(key, outcome.lyrics());
    }

    private static LyricsSources sources() {
        if (sources == null) sources = new LyricsSources(agent());
        return sources;
    }

    private static LyricsCache cache() {
        if (cache == null) cache = new LyricsCache(FMLPaths.GAMEDIR.get()
                .resolve(BattleCraftMod.MOD_ID).resolve("lyrics"));
        return cache;
    }

    private static String agent() {
        String version = ModList.get().getModContainerById(BattleCraftMod.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("dev");
        return "BattleCraft/" + version + " (https://github.com/pers1k1/BattleCraft-mod)";
    }
}
