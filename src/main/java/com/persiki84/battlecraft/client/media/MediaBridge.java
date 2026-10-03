package com.persiki84.battlecraft.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class MediaBridge {
    private static final String RESOURCES = "/assets/battlecraft/bridge/";
    private static final String FOLDER = "battlecraft";
    private static final String SCRIPT = "media-watch.ps1";
    private static final String NATIVE = "media-native.cs";
    private static final String ART = "media-art.png";
    private static final int RESTART_LIMIT = 3;
    private static final long RESTART_DELAY_MS = 4000L;
    private static final int OUTBOX_LIMIT = 8;
    private static final long OUTBOX_WAIT_MS = 1000L;

    // WHY: поля пишет демон-поток моста, а читает клиентский тик: без volatile тик не видел
    // WHY: смерть процесса и мост не перезапускался до конца сеанса
    private static volatile Process process;
    private static volatile int restarts;
    private static volatile boolean unsupported;
    private static volatile String announced = "";
    private static volatile BlockingQueue<String> outbox;

    private MediaBridge() {}

    // WHY: без паузы после неудачного запуска тик пробовал снова каждые 50 мс, и короткая блокировка
    // WHY: файлов моста антивирусом за три тика выключала музыку острова до конца сеанса
    public static synchronized void start() {
        if (unsupported || process != null) return;
        if (!windows()) {
            unsupported = true;
            return;
        }

        try {
            Path folder = install();
            Process launched = launch(folder);
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(OUTBOX_LIMIT);
            process = launched;
            outbox = queue;
            listen(launched, launched.getInputStream(), MediaBridge::accept, true);
            listen(launched, launched.getErrorStream(), MediaBridge::complain, false);
            speak(launched, queue);
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] media bridge did not start: {}", error.toString());
            process = null;
            outbox = null;
            unsupported = ++restarts >= RESTART_LIMIT;
            MediaWatch.retryAfter(System.currentTimeMillis() + RESTART_DELAY_MS);
        }
    }

    public static void stop() {
        Process current = process;
        if (current != null) disown(current);
        MediaControl.forget();
        MediaWatch.publish(MediaTrack.NONE);
        if (current == null) return;

        current.destroy();
        sweep();
    }

    // WHY: мост снимают с учёта и игровой поток (stop), и поток его трубы (смерть процесса): проверка
    // WHY: владельца и обнуление одним шагом не дают опоздавшему потоку старого моста снять новый
    private static synchronized boolean disown(Process owner) {
        if (process != owner) return false;

        process = null;
        outbox = null;
        return true;
    }

    // WHY: поле обнуляет поток моста, а спрашивают его кадр за кадром кнопки плеера: второе чтение
    // WHY: поля между проверкой и вызовом ловило null и роняло клиент
    public static boolean running() {
        Process current = process;
        return current != null && current.isAlive();
    }

    public static boolean available() {
        return !unsupported;
    }

    // WHY: команда несёт приложение и название трека, над которыми её нажали: мост исполняет её, только
    // WHY: если публикует острову ту же сессию с тем же треком. Одно приложение держит по сессии на
    // WHY: вкладку, и пауза, нажатая над одной вкладкой, иначе досталась бы соседней
    static boolean command(String verb, long positionMs, MediaTrack track) {
        BlockingQueue<String> queue = outbox;
        if (queue == null || !running() || !deliverable(track.app()) || !deliverable(track.title())) return false;

        return queue.offer(verb + '\t' + positionMs + '\t' + track.app() + '\t' + track.title() + '\n');
    }

    private static boolean deliverable(String field) {
        return !field.isEmpty() && field.indexOf('\t') < 0 && field.indexOf('\n') < 0 && field.indexOf('\r') < 0;
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

    private static void listen(Process owner, InputStream stream, LineReader sink, boolean primary) {
        Thread worker = new Thread(() -> pump(owner, stream, sink, primary), "battlecraft-media");
        worker.setDaemon(true);
        worker.start();
    }

    // WHY: поток остановленного моста дочитывает трубу уже после stop и после запуска нового моста:
    // WHY: без сверки с владельцем он публиковал старый трек поверх выключенного острова, а своим
    // WHY: концом снимал с учёта новый мост, и тот оставался сиротой рядом с запущенным следом
    private static void pump(Process owner, InputStream stream, LineReader sink, boolean primary) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (!primary) sink.accept(line);
                else deliver(owner, sink, line);
            }
        } catch (IOException ignored) {
            BattleCraftMod.LOGGER.debug("[battlecraft] media bridge pipe broke");
        }
        if (primary && disown(owner)) retire(owner);
    }

    // WHY: проверка владельца и публикация идут под тем же монитором, что и снятие моста с учёта:
    // WHY: строка, прочитанная в момент stop, иначе публиковала старый трек уже после пустого.
    // WHY: Первая разобранная строка сбрасывает счётчик падений: три сбоя за часы работы не должны
    // WHY: выключать музыку до перезапуска игры
    private static synchronized void deliver(Process owner, LineReader sink, String line) {
        if (process != owner) return;

        sink.accept(line);
        restarts = 0;
    }

    // WHY: запись в трубу блокирует, пока мост её не вычитал, а команды жмут на игровом потоке: при
    // WHY: зависшем мосте кнопка плеера и выход из игры вставали бы на записи. Строки пишет свой поток
    private static void speak(Process owner, BlockingQueue<String> queue) {
        Thread worker = new Thread(() -> relay(owner, queue), "battlecraft-media-remote");
        worker.setDaemon(true);
        worker.start();
    }

    private static void relay(Process owner, BlockingQueue<String> queue) {
        try (Writer sink = new OutputStreamWriter(owner.getOutputStream(), StandardCharsets.UTF_8)) {
            while (owner.isAlive()) {
                String line = queue.poll(OUTBOX_WAIT_MS, TimeUnit.MILLISECONDS);
                if (line == null) continue;

                sink.write(line);
                sink.flush();
            }
        } catch (IOException | InterruptedException error) {
            BattleCraftMod.LOGGER.debug("[battlecraft] media command pipe closed: {}", error.toString());
        }
    }

    private static void retire(Process owner) {
        owner.destroy();
        MediaControl.forget();
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
            publish(read(state));
        } catch (Exception ignored) {
            publish(MediaTrack.NONE);
        }
    }

    private static void publish(MediaTrack track) {
        announce(track);
        MediaControl.deliver(track);
    }

    // WHY: без строки в логе «плеер не подхватывается» не отличить от моста, который его не
    // WHY: услышал, от острова, который его не показал. Пишется только смена источника
    private static void announce(MediaTrack track) {
        String source = track.present() ? track.app() + (track.blind() ? " by sound" : "") : "none";
        if (source.equals(announced)) return;

        announced = source;
        BattleCraftMod.LOGGER.info("[battlecraft] media source: {}", source);
    }

    private static float[] spectrum(com.google.gson.JsonArray sent) {
        float[] fresh = new float[MediaWatch.BANDS];
        for (int index = 0; index < fresh.length && index < sent.size(); index++) {
            float value = sent.get(index).getAsFloat();
            fresh[index] = Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
        }
        return fresh;
    }

    // WHY: если захват потока не поднялся, мост шлёт один пик, и полоски идут от него все разом
    private static float[] spread(float peak) {
        float[] fresh = new float[MediaWatch.BANDS];
        java.util.Arrays.fill(fresh, Float.isFinite(peak) ? Math.max(0.0f, peak) : 0.0f);
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
                number(state, "pos"), number(state, "dur"), System.currentTimeMillis() - number(state, "age"),
                number(state, "art"), (int) number(state, "ctl"));
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
