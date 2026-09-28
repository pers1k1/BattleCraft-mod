package com.persiki84.battlecraft.client.wallpaper;

import com.persiki84.battlecraft.BattleCraftMod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class WallpaperStore {
    static final String WALLPAPERS = "wallpapers";
    static final String CACHE = ".cache";

    private static final Pattern UNSAFE = Pattern.compile("[^a-z0-9_-]+");
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private static final int STEM_LIMIT = 32;
    private static final String INCOMING = ".incoming";
    private static final String NEXT = ".next";
    private static final int CLEAR_ATTEMPTS = 4;
    private static final long CLEAR_PAUSE_NANOS = 60_000_000L;
    // WHY: в обои идут только первые секунды ролика, а копия исходника живёт в папке обоев
    // WHY: целиком: фильм на десятки гигабайт занимал бы диск ради 20 секунд
    private static final long SOURCE_LIMIT_BYTES = 2L << 30;
    // WHY: ролик 4K это копия исходника и до 600 кадров JPEG, около гигабайта на обои. Предел
    // WHY: проверяется перед импортом, поэтому одни обои могут его перешагнуть, но не больше
    private static final long LIBRARY_LIMIT_BYTES = 8L << 30;

    private final Path bridge;
    private final Path folder;
    private final Map<String, WallpaperImport> running = new ConcurrentHashMap<>();
    private final Set<String> refused = ConcurrentHashMap.newKeySet();
    private final Set<String> rebuilt = ConcurrentHashMap.newKeySet();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(WallpaperStore::importThread);

    WallpaperStore(Path bridge) {
        this.bridge = bridge;
        this.folder = bridge.resolve(WALLPAPERS);
        worker.execute(this::sweepLeftovers);
    }

    // WHY: сборка, оборванная закрытием игры, остаётся копией исходника в .incoming и кешем .next:
    // WHY: окно обоев их не видит и удалить из игры не даёт, а это может быть гигабайтный ролик.
    // WHY: Уборка стоит первой задачей в потоке импорта: живой сборки до неё быть не может, а
    // WHY: сотни кадров не удаляются в рендер-потоке, где хранилище создаётся с первым кадром стола
    private void sweepLeftovers() {
        forget(folder.resolve(INCOMING));
        Path caches = folder.resolve(CACHE);
        if (!Files.isDirectory(caches)) return;

        try (DirectoryStream<Path> staged = Files.newDirectoryStream(caches, "*" + NEXT)) {
            for (Path next : staged) forget(next);
        } catch (IOException error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper leftovers not swept: {}", error.toString());
        }
    }

    private static Thread importThread(Runnable task) {
        Thread thread = new Thread(task, "battlecraft-wallpaper-import");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    }

    Path folder() {
        return folder;
    }

    Path cache(String id) {
        return folder.resolve(CACHE).resolve(id);
    }

    private Path staging(String id) {
        return folder.resolve(CACHE).resolve(id + NEXT);
    }

    static String idOf(String fileName) {
        String stem = UNSAFE.matcher(WallpaperFormat.stem(fileName).toLowerCase(Locale.ROOT)).replaceAll("_");
        if (stem.length() > STEM_LIMIT) stem = stem.substring(0, STEM_LIMIT);
        return stem + "-" + String.format(Locale.ROOT, "%08x", fileName.hashCode());
    }

    List<WallpaperEntry> entries() {
        List<WallpaperEntry> found = new ArrayList<>();
        Path root = folder.resolve(CACHE);
        if (!Files.isDirectory(root)) return found;

        try (DirectoryStream<Path> caches = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path cache : caches) {
                WallpaperMeta meta = WallpaperMeta.read(cache);
                if (meta != null && meta.id().equals(cache.getFileName().toString())) found.add(meta.entry());
            }
        } catch (IOException error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper cache unreadable: {}", error.toString());
        }
        found.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        return found;
    }

    WallpaperMeta meta(String id) {
        return VALID_ID.matcher(id).matches() ? WallpaperMeta.read(cache(id)) : null;
    }

    boolean rebuilt(String id) {
        return rebuilt.contains(id);
    }

    void opened(String id) {
        rebuilt.remove(id);
    }

    // WHY: у корня диска нет имени файла: перетащенный на окно диск C: иначе обрывал исключением
    // WHY: весь бросок, и окно обоев не открывалось даже ради остальных файлов
    WallpaperImport importFile(Path source) {
        Path file = source.getFileName();
        if (file == null) return WallpaperImport.failed("", WallpaperFailure.FORMAT);

        String name = file.toString();
        String id = idOf(name);
        WallpaperFormat format = WallpaperFormat.of(name);
        if (format == null) return WallpaperImport.failed(id, WallpaperFailure.FORMAT);

        WallpaperImport fresh = new WallpaperImport(id);
        WallpaperImport already = running.putIfAbsent(id, fresh);
        if (already != null) return already;

        worker.execute(() -> run(source, format, fresh));
        return fresh;
    }

    // WHY: неудачный импорт убирает за собой недописанную сборку и кеш без meta: без meta обоев нет
    // WHY: в списке и удалить их из игры нельзя. Файл, который игрок сам положил в папку, остаётся,
    // WHY: но запоминается до конца сеанса, чтобы окно не гоняло его в импорт при каждом открытии
    private void run(Path source, WallpaperFormat format, WallpaperImport attempt) {
        Path placed = folder.resolve(source.getFileName().toString());
        boolean resident = false;
        String failure = null;
        try {
            resident = placedAlready(source, placed);
            admit(resident ? null : source, attempt.id());
            install(resident ? null : source, placed, format, attempt);
        } catch (WallpaperFailure rejected) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper {} rejected: {}", source.getFileName(), rejected.getMessage());
            failure = rejected.key();
        } catch (RuntimeException | Error broken) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper {} failed: {}", source.getFileName(), broken.toString());
            failure = WallpaperFailure.READ;
        }
        if (failure != null && resident && !WallpaperFailure.FULL.equals(failure)) refuse(placed);
        if (failure != null) discard(attempt.id());
        running.remove(attempt.id(), attempt);
        if (failure == null) attempt.finish();
        else attempt.fail(failure);
    }

    private boolean placedAlready(Path source, Path target) throws WallpaperFailure {
        if (!Files.isRegularFile(source)) throw new WallpaperFailure(WallpaperFailure.READ, "no such file");

        try {
            Files.createDirectories(folder);
            return Files.exists(target) && Files.isSameFile(source, target);
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.COPY, error.toString());
        }
    }

    private void admit(Path outside, String id) throws WallpaperFailure {
        if (outside != null && sizeOf(outside) > SOURCE_LIMIT_BYTES) {
            throw new WallpaperFailure(WallpaperFailure.LARGE, "source over " + SOURCE_LIMIT_BYTES + " bytes");
        }
        if (libraryBytes(id) > LIBRARY_LIMIT_BYTES) {
            throw new WallpaperFailure(WallpaperFailure.FULL, "library over " + LIBRARY_LIMIT_BYTES + " bytes");
        }
    }

    private static long sizeOf(Path file) throws WallpaperFailure {
        try {
            return Files.size(file);
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.READ, error.toString());
        }
    }

    // WHY: кеш заменяемых обоев не считается: новая сборка встанет на его место
    private long libraryBytes(String replaced) {
        Path own = cache(replaced);
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(file -> !file.startsWith(own)).filter(Files::isRegularFile)
                    .mapToLong(WallpaperStore::bytesOf).sum();
        } catch (IOException | UncheckedIOException error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper library not measured: {}", error.toString());
            return 0L;
        }
    }

    private static long bytesOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException error) {
            return 0L;
        }
    }

    // WHY: файл собирается в стороне и заменяет исходник и кеш только целиком и после успеха: битый
    // WHY: ролик с именем рабочих обоев иначе уничтожал их, а игра, закрытая посреди копирования,
    // WHY: оставляла под именем обоев обрезанный файл. Копия лежит в .incoming под своим именем:
    // WHY: декодер видео выбирает разборщик по расширению, а сканер папки подпапки не читает
    private void install(Path outside, Path placed, WallpaperFormat format, WallpaperImport attempt)
            throws WallpaperFailure {
        String id = attempt.id();
        Path next = staging(id);
        try {
            WallpaperCache.clear(next);
            Files.createDirectories(WallpaperCache.frames(next));
            Path file = outside == null ? placed : copy(outside, placed.getFileName());
            boolean built = build(file, placed.getFileName().toString(), next, format, attempt);
            if (outside != null) settle(file, placed);
            if (built) adopt(next, id);
            else WallpaperCache.clear(next);
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.WRITE, error.toString());
        }
    }

    private Path copy(Path source, Path name) throws WallpaperFailure {
        Path incoming = folder.resolve(INCOMING).resolve(name);
        try {
            Files.createDirectories(incoming.getParent());
            Files.copy(source, incoming, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return incoming;
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.COPY, error.toString());
        }
    }

    private static void settle(Path incoming, Path placed) throws WallpaperFailure {
        try {
            Files.move(incoming, placed, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.COPY, error.toString());
        }
    }

    private boolean build(Path file, String name, Path next, WallpaperFormat format, WallpaperImport attempt)
            throws IOException, WallpaperFailure {
        String id = attempt.id();
        long size = Files.size(file);
        long modified = Files.getLastModifiedTime(file).toMillis();
        WallpaperMeta existing = WallpaperMeta.read(cache(id));
        if (existing != null && existing.describes(name, size, modified)) return false;

        WallpaperClip clip = convert(format, file, next, attempt);
        new WallpaperMeta(WallpaperMeta.VERSION, id, name, size, modified, clip.kind(), clip.frames(),
                clip.fps(), clip.width(), clip.height()).write(next);
        return true;
    }

    private void adopt(Path next, String id) throws IOException {
        clearShown(cache(id));
        Files.move(next, cache(id), StandardCopyOption.ATOMIC_MOVE);
        rebuilt.add(id);
    }

    // WHY: декодер показанных обоев читает их кадры прямо сейчас, и Windows не удаляет папку, пока
    // WHY: в ней открыт хоть один файл. Чтение кадра длится миллисекунды, поэтому очистка
    // WHY: повторяется: иначе свежая сборка выбрасывалась, а старый кеш уже без meta пропадал
    private static void clearShown(Path cache) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                WallpaperCache.clear(cache);
                return;
            } catch (IOException busy) {
                if (attempt >= CLEAR_ATTEMPTS) throw busy;
                LockSupport.parkNanos(CLEAR_PAUSE_NANOS);
            }
        }
    }

    private static void forget(Path leftover) {
        try {
            WallpaperCache.clear(leftover);
        } catch (IOException error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper leftover {} kept: {}", leftover.getFileName(), error.toString());
        }
    }

    private void refuse(Path placed) {
        String stamp = stamp(placed);
        if (!stamp.isEmpty()) refused.add(stamp);
    }

    boolean refused(Path file) {
        String stamp = stamp(file);
        return !stamp.isEmpty() && refused.contains(stamp);
    }

    private static String stamp(Path file) {
        try {
            return file.getFileName() + "|" + Files.size(file) + "|" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException error) {
            return "";
        }
    }

    private void discard(String id) {
        forget(staging(id));
        forget(folder.resolve(INCOMING));
        if (WallpaperMeta.read(cache(id)) == null) forget(cache(id));
    }

    private WallpaperClip convert(WallpaperFormat format, Path placed, Path cache, WallpaperImport attempt)
            throws WallpaperFailure {
        return switch (format) {
            case STILL -> WallpaperStill.convert(placed, cache);
            case GIF -> WallpaperGif.convert(placed, cache, attempt);
            case VIDEO -> WallpaperVideo.convert(placed, cache, bridge, attempt);
        };
    }

    boolean remove(String id) {
        if (!VALID_ID.matcher(id).matches() || running.containsKey(id)) return false;

        boolean removed = false;
        try {
            removed = deleteSources(id);
            if (Files.exists(cache(id))) {
                WallpaperCache.clear(cache(id));
                removed = true;
            }
        } catch (IOException error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper {} not removed: {}", id, error.toString());
        }
        return removed;
    }

    private boolean deleteSources(String id) throws IOException {
        if (!Files.isDirectory(folder)) return false;

        boolean removed = false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, Files::isRegularFile)) {
            for (Path file : files) {
                if (idOf(file.getFileName().toString()).equals(id)) removed |= Files.deleteIfExists(file);
            }
        }
        return removed;
    }
}
