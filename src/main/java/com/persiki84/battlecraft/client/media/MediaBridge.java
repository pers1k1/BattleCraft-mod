package com.persiki84.battlecraft.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

public final class MediaBridge {
    private static final String RESOURCES = "/assets/battlecraft/bridge/";
    private static final String FOLDER = "battlecraft";
    private static final String SCRIPT = "media-watch.ps1";
    private static final String NATIVE = "media-native.cs";
    private static final String ART = "media-art.png";
    private static final int RESTART_LIMIT = 3;
    private static final long RESTART_DELAY_MS = 4000L;

    // WHY: поля пишет демон-поток моста, а читает клиентский тик: без volatile тик не видел
    // WHY: смерть процесса и мост не перезапускался до конца сеанса
    private static volatile Process process;
    private static volatile int restarts;
    private static volatile boolean unsupported;
    private static volatile boolean stopped;

    private MediaBridge() {}

    public static void start() {
        if (unsupported || process != null) return;
        if (!windows()) {
            unsupported = true;
            return;
        }

        stopped = false;
        try {
            Path folder = install();
            process = launch(folder);
            listen(process.getInputStream(), MediaBridge::accept, true);
            listen(process.getErrorStream(), MediaBridge::complain, false);
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] media bridge did not start: {}", error.toString());
            process = null;
            unsupported = ++restarts >= RESTART_LIMIT;
        }
    }

    public static void stop() {
        stopped = true;
        MediaWatch.publish(MediaTrack.NONE);
        if (process == null) return;

        process.destroy();
        process = null;
        sweep();
    }

    public static boolean running() {
        return process != null && process.isAlive();
    }

    public static boolean available() {
        return !unsupported;
    }

    public static Path artFile() {
        return folder().resolve(ART);
    }

    private static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER);
    }

    private static void sweep() {
        try {
            Files.deleteIfExists(artFile());
            Files.deleteIfExists(folder().resolve(ART + ".part"));
        } catch (IOException ignored) {
            BattleCraftMod.LOGGER.debug("[battlecraft] cover art left on disk");
        }
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Path install() throws IOException {
        Path folder = folder();
        Files.createDirectories(folder);
        unpack(SCRIPT, folder.resolve(SCRIPT));
        unpack(NATIVE, folder.resolve(NATIVE));
        return folder;
    }

    // WHY: мост пересобирает свою библиотеку, когда исходник новее dll, а перезапись тем же
    // WHY: содержимым делает его новее при каждом запуске игры: csc отнимал секунды перед первым
    // WHY: опросом плеера, и остров успевал показать аватар вместо обложки
    private static void unpack(String name, Path target) throws IOException {
        try (InputStream source = MediaBridge.class.getResourceAsStream(RESOURCES + name)) {
            if (source == null) throw new IOException("missing " + name);

            byte[] fresh = source.readAllBytes();
            if (Files.isRegularFile(target) && Arrays.equals(fresh, Files.readAllBytes(target))) return;
            Files.write(target, fresh);
        }
    }

    private static Process launch(Path folder) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-MTA",
                "-ExecutionPolicy", "Bypass", "-File", folder.resolve(SCRIPT).toAbsolutePath().toString(),
                folder.toAbsolutePath().toString(), String.valueOf(ProcessHandle.current().pid()));
        builder.redirectErrorStream(false);
        return builder.start();
    }

    private static void listen(InputStream stream, LineReader sink, boolean primary) {
        Thread worker = new Thread(() -> pump(stream, sink, primary), "battlecraft-media");
        worker.setDaemon(true);
        worker.start();
    }

    private static void pump(InputStream stream, LineReader sink, boolean primary) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                sink.accept(line);
            }
        } catch (IOException ignored) {
            if (primary) MediaWatch.publish(MediaTrack.NONE);
        }
        if (primary && !stopped) retire();
    }

    private static void retire() {
        process = null;
        MediaWatch.publish(MediaTrack.NONE);
        if (++restarts >= RESTART_LIMIT) {
            unsupported = true;
            BattleCraftMod.LOGGER.warn("[battlecraft] media bridge stopped responding, giving up");
            return;
        }
        MediaWatch.retryAfter(System.currentTimeMillis() + RESTART_DELAY_MS);
    }

    private static void complain(String line) {
        if (line.isBlank()) return;
        BattleCraftMod.LOGGER.warn("[battlecraft] media bridge: {}", line.trim());
    }

    private static void accept(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') return;

        try {
            JsonObject state = JsonParser.parseString(trimmed).getAsJsonObject();
            if (state.has("b")) {
                MediaWatch.bands(spectrum(state.getAsJsonArray("b")));
                return;
            }
            if (state.has("peak")) {
                MediaWatch.bands(spread(state.get("peak").getAsFloat()));
                return;
            }
            MediaWatch.publish(read(state));
        } catch (Exception ignored) {
            MediaWatch.publish(MediaTrack.NONE);
        }
    }

    private static float[] spectrum(com.google.gson.JsonArray sent) {
        float[] fresh = new float[MediaWatch.BANDS];
        for (int index = 0; index < fresh.length && index < sent.size(); index++) {
            fresh[index] = Math.max(0.0f, sent.get(index).getAsFloat());
        }
        return fresh;
    }

    // WHY: если захват потока не поднялся, мост шлёт один пик, и полоски идут от него все разом
    private static float[] spread(float peak) {
        float[] fresh = new float[MediaWatch.BANDS];
        java.util.Arrays.fill(fresh, Math.max(0.0f, peak));
        return fresh;
    }

    private static MediaTrack read(JsonObject state) {
        if (!bool(state, "ok")) return MediaTrack.NONE;

        String app = text(state, "app");
        String title = text(state, "title");
        boolean blind = bool(state, "blind");
        if (title.isEmpty() && !blind) return MediaTrack.NONE;

        return new MediaTrack(MediaSource.ofApp(app), MediaSource.ofSite(text(state, "site")), app, title,
                text(state, "artist"), text(state, "status"), bool(state, "playing"), blind,
                number(state, "pos"), number(state, "dur"),
                System.currentTimeMillis() - number(state, "age"), number(state, "art"));
    }

    private static String text(JsonObject state, String key) {
        return state.has(key) && !state.get(key).isJsonNull() ? state.get(key).getAsString().trim() : "";
    }

    private static boolean bool(JsonObject state, String key) {
        return state.has(key) && !state.get(key).isJsonNull() && state.get(key).getAsBoolean();
    }

    private static long number(JsonObject state, String key) {
        return state.has(key) && !state.get(key).isJsonNull() ? state.get(key).getAsLong() : 0L;
    }

    private interface LineReader {
        void accept(String line);
    }
}
