package com.persiki84.battlecraft.client.wallpaper;

import com.persiki84.battlecraft.BattleCraftMod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class WallpaperVideo {
    private static final String RESOURCES = "/assets/battlecraft/bridge/";
    private static final String SCRIPT = "wallpaper-import.ps1";
    private static final String NATIVE = "wallpaper-native.cs";
    private static final long SILENCE_LIMIT_MS = 120_000L;
    private static final long WATCH_STEP_MS = 1000L;
    private static final long SETTLE_SECONDS = 3L;

    private WallpaperVideo() {}

    static WallpaperClip convert(Path source, Path cache, Path bridgeFolder, WallpaperImport progress)
            throws WallpaperFailure {
        if (!windows()) throw new WallpaperFailure(WallpaperFailure.PLATFORM, "video needs Windows");

        Path raw = cache.resolve(WallpaperCache.RAW);
        Process process = launch(bridgeFolder, source, raw);
        try {
            drain(process.getErrorStream());
            WallpaperVideoFeed feed = new WallpaperVideoFeed(raw, cache, progress);
            watch(process, feed);
            return feed.consume(process.getInputStream());
        } finally {
            settle(process);
            sweep(raw);
        }
    }

    private static void settle(Process process) {
        process.destroyForcibly();
        try {
            process.waitFor(SETTLE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Process launch(Path bridgeFolder, Path source, Path raw) throws WallpaperFailure {
        try {
            Files.createDirectories(bridgeFolder);
            unpack(SCRIPT, bridgeFolder.resolve(SCRIPT));
            unpack(NATIVE, bridgeFolder.resolve(NATIVE));
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-MTA",
                    "-ExecutionPolicy", "Bypass", "-File", bridgeFolder.resolve(SCRIPT).toAbsolutePath().toString(),
                    bridgeFolder.toAbsolutePath().toString(), String.valueOf(ProcessHandle.current().pid()),
                    source.toAbsolutePath().toString(), raw.toAbsolutePath().toString(),
                    String.valueOf(WallpaperClip.MAX_FPS), String.valueOf(WallpaperClip.MAX_SECONDS));
            return builder.start();
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.BRIDGE, error.toString());
        }
    }

    // WHY: скрипт пересобирает библиотеку, когда исходник новее dll, поэтому исходник с тем же
    // WHY: содержимым не перезаписывается: иначе csc отрабатывал бы на каждом импорте
    private static void unpack(String name, Path target) throws IOException {
        try (InputStream source = WallpaperVideo.class.getResourceAsStream(RESOURCES + name)) {
            if (source == null) throw new IOException("missing " + name);

            byte[] fresh = source.readAllBytes();
            if (Files.isRegularFile(target) && Arrays.equals(fresh, Files.readAllBytes(target))) return;
            Files.write(target, fresh);
        }
    }

    private static void drain(InputStream stream) {
        Thread worker = new Thread(() -> complain(stream), "battlecraft-wallpaper-bridge-errors");
        worker.setDaemon(true);
        worker.start();
    }

    private static void complain(InputStream stream) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (!line.isBlank()) BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper bridge: {}", line.trim());
            }
        } catch (IOException ignored) {
            BattleCraftMod.LOGGER.debug("[battlecraft] wallpaper bridge error stream closed");
        }
    }

    // WHY: мост сам снимается, когда декодер повис, но если повис сам PowerShell или csc, строк
    // WHY: не будет вовсе, и импорт висел бы в RUNNING до конца сеанса
    private static void watch(Process process, WallpaperVideoFeed feed) {
        Thread watchdog = new Thread(() -> {
            while (process.isAlive()) {
                if (System.currentTimeMillis() - feed.lastHeardMs() > SILENCE_LIMIT_MS) process.destroyForcibly();
                sleep();
            }
        }, "battlecraft-wallpaper-bridge-watch");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void sleep() {
        try {
            Thread.sleep(WATCH_STEP_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sweep(Path raw) {
        try {
            WallpaperCache.clear(raw);
        } catch (IOException ignored) {
            BattleCraftMod.LOGGER.debug("[battlecraft] wallpaper raw frames left on disk");
        }
    }
}
