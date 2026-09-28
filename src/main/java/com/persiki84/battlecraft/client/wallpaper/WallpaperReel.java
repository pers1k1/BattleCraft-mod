package com.persiki84.battlecraft.client.wallpaper;

import com.persiki84.battlecraft.BattleCraftMod;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;

final class WallpaperReel {
    static final int RING = 3;

    private static final int LARGE_PIXELS = 1920 * 1080;
    private static final int MAX_WORKERS = 3;
    private static final long IDLE_NANOS = 2_000_000_000L;
    private static final long NAP_NANOS = 1_000_000L;
    private static final long SLEEP_NANOS = 50_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NOTHING = -1L;

    private final Path[] frames;
    private final int width;
    private final int height;
    private final long bytes;
    private final long frameNanos;
    private final AtomicLongArray targets;
    private final AtomicLongArray filled;
    private final AtomicLong claimed = new AtomicLong();
    private final AtomicInteger copying = new AtomicInteger();
    private final Thread[] workers;
    private volatile long consumed;
    private volatile long touchedAt = System.nanoTime();
    private volatile boolean stopped;
    private volatile boolean complained;

    private WallpaperReel(Path cache, WallpaperMeta meta, AtomicLongArray targets) {
        frames = WallpaperCache.framePaths(cache, meta.frames());
        width = meta.width();
        height = meta.height();
        bytes = (long) width * height * 4L;
        frameNanos = meta.fps() > 0.0f ? Math.round(NANOS_PER_SECOND / (double) meta.fps()) : Long.MAX_VALUE;
        this.targets = targets;
        filled = new AtomicLongArray(targets.length());
        for (int index = 0; index < targets.length(); index++) filled.set(index, NOTHING);
        workers = new Thread[workerCount()];
    }

    static int slotsFor(WallpaperMeta meta) {
        return meta.frames() == 1 ? 1 : RING;
    }

    static WallpaperReel start(Path cache, WallpaperMeta meta, AtomicLongArray targets) {
        WallpaperReel reel = new WallpaperReel(cache, meta, targets);
        for (int index = 0; index < reel.workers.length; index++) {
            Thread worker = new Thread(reel::work, "battlecraft-wallpaper-decode-" + index);
            worker.setDaemon(true);
            reel.workers[index] = worker;
            worker.start();
        }
        return reel;
    }

    // WHY: кадр 4K декодируется дольше 33 мс, поэтому крупные ролики держат 30 к/с пулом:
    // WHY: соседние кадры декодируются параллельно в свои ячейки кольца, а отдаются строго по порядку
    private int workerCount() {
        if (still() || width * height <= LARGE_PIXELS) return 1;
        return Math.max(1, Math.min(MAX_WORKERS, Runtime.getRuntime().availableProcessors() - 1));
    }

    boolean still() {
        return frames.length == 1;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    long frameNanos() {
        return frameNanos;
    }

    void touch(long now) {
        touchedAt = now;
    }

    boolean hasFrame() {
        long next = consumed;
        return filled.get(slot(next)) == next;
    }

    int currentSlot() {
        return slot(consumed);
    }

    void advance() {
        consumed = consumed + 1L;
    }

    // WHY: ячейки это отображённые буферы OpenGL, и после stop рендер-поток их удаляет. Поток,
    // WHY: который уже пишет в ячейку, дописывает её до конца, иначе запись уйдёт в снятую память.
    // WHY: Порядок счётчика и флага зеркальный с copyUnlessStopped, поэтому гонки нет
    void stop() {
        stopped = true;
        for (Thread worker : workers) LockSupport.unpark(worker);
        while (copying.get() > 0) Thread.onSpinWait();
    }

    private int slot(long sequence) {
        return (int) (sequence % targets.length());
    }

    private void work() {
        try (WallpaperFrameDecoder decoder = new WallpaperFrameDecoder(width, height)) {
            while (!stopped) {
                long sequence = claimed.getAndIncrement();
                if (still() && sequence > 0L || !awaitRoom(sequence)) break;
                decode(decoder, sequence);
            }
        } catch (RuntimeException | OutOfMemoryError broken) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper decoder stopped: {}", broken.toString());
        }
    }

    private boolean awaitRoom(long sequence) {
        while (!stopped) {
            boolean idle = System.nanoTime() - touchedAt > IDLE_NANOS;
            if (!idle && sequence - consumed < targets.length()) return true;
            LockSupport.parkNanos(idle ? SLEEP_NANOS : NAP_NANOS);
        }
        return false;
    }

    // WHY: битый или пропавший кадр (обои пересобираются под показом) не должен останавливать
    // WHY: кольцо: ячейка всё равно публикуется со старым содержимым, и ролик идёт дальше
    private void decode(WallpaperFrameDecoder decoder, long sequence) {
        int slot = slot(sequence);
        try {
            ByteBuffer pixels = decoder.decode(frames[(int) (sequence % frames.length)]);
            try {
                awaitMapped(slot);
                copyUnlessStopped(pixels, slot);
            } finally {
                decoder.release(pixels);
            }
        } catch (IOException | RuntimeException error) {
            if (!complained) BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper frame unreadable: {}", error.toString());
            complained = true;
        }
        filled.set(slot, sequence);
    }

    private void awaitMapped(int slot) {
        while (!stopped && targets.get(slot) == 0L) LockSupport.parkNanos(NAP_NANOS);
    }

    private void copyUnlessStopped(ByteBuffer pixels, int slot) {
        copying.incrementAndGet();
        try {
            long target = targets.get(slot);
            if (!stopped && target != 0L) MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), target, bytes);
        } finally {
            copying.decrementAndGet();
        }
    }
}
