package com.persiki84.battlecraft.client.wallpaper;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class WallpaperEncoders implements AutoCloseable {
    private static final int MAX_THREADS = 3;
    private static final long SETTLE_SECONDS = 5L;

    private final Path cache;
    private final int rotation;
    private final int count;
    private final WallpaperImport progress;
    private final BlockingQueue<Bench> free;
    private final Bench[] benches;
    private final ExecutorService pool;
    private final AtomicInteger stored = new AtomicInteger();
    private volatile WallpaperFailure failure;
    private volatile int fittedWidth = 1;
    private volatile int fittedHeight = 1;

    // WHY: JPEG 4K жмётся дольше, чем мост декодирует кадр, и в один поток импорт ролика шёл
    // WHY: минуты. Кадры жмутся параллельно, каждый поток со своим растром и своим писателем
    WallpaperEncoders(int width, int height, int rotation, int count, Path cache, WallpaperImport progress)
            throws WallpaperFailure {
        this.cache = cache;
        this.rotation = rotation;
        this.count = count;
        this.progress = progress;
        int threads = Math.max(1, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors() - 1));
        free = new ArrayBlockingQueue<>(threads);
        benches = new Bench[threads];
        for (int index = 0; index < threads; index++) {
            benches[index] = new Bench(width, height);
            free.add(benches[index]);
        }
        pool = Executors.newFixedThreadPool(threads, WallpaperEncoders::encoderThread);
    }

    private static Thread encoderThread(Runnable task) {
        Thread thread = new Thread(task, "battlecraft-wallpaper-encode");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    }

    void encode(IntBuffer raw, int index) throws WallpaperFailure {
        Bench bench = take();
        raw.get(bench.pixels);
        pool.execute(() -> run(bench, index));
    }

    private void run(Bench bench, int index) {
        try {
            BufferedImage turned = rotation == 0 ? bench.canvas : WallpaperScale.rotated(bench.canvas, rotation);
            BufferedImage fitted = WallpaperScale.fit(turned);
            fittedWidth = fitted.getWidth();
            fittedHeight = fitted.getHeight();
            bench.jpeg.write(fitted, WallpaperCache.frame(cache, index));
            progress.advance((float) stored.incrementAndGet() / Math.max(1, count));
        } catch (WallpaperFailure | RuntimeException broken) {
            failure = broken instanceof WallpaperFailure known ? known
                    : new WallpaperFailure(WallpaperFailure.WRITE, broken.toString());
        } finally {
            free.add(bench);
        }
    }

    private Bench take() throws WallpaperFailure {
        try {
            Bench bench = free.take();
            if (failure != null) {
                free.add(bench);
                throw failure;
            }
            return bench;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new WallpaperFailure(WallpaperFailure.VIDEO, "interrupted");
        }
    }

    void finish() throws WallpaperFailure {
        Bench[] returned = new Bench[benches.length];
        for (int index = 0; index < returned.length; index++) returned[index] = take();
        for (Bench bench : returned) free.add(bench);
    }

    int fittedWidth() {
        return fittedWidth;
    }

    int fittedHeight() {
        return fittedHeight;
    }

    @Override
    public void close() {
        pool.shutdownNow();
        try {
            pool.awaitTermination(SETTLE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        for (Bench bench : benches) bench.jpeg.close();
    }

    private static final class Bench {
        final BufferedImage canvas;
        final int[] pixels;
        final WallpaperJpeg jpeg;

        Bench(int width, int height) throws WallpaperFailure {
            canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            pixels = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
            jpeg = new WallpaperJpeg();
        }
    }
}
