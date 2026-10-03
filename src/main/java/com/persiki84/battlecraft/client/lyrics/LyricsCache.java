package com.persiki84.battlecraft.client.lyrics;

import com.persiki84.battlecraft.BattleCraftMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

// WHY: имя файла это хэш ключа, а не название трека: название приходит со страницы, и из него
// WHY: собирался бы путь с «..» и разделителями. Ключ лежит первой строкой и сверяется при чтении
final class LyricsCache {
    private static final String SUFFIX = ".lrc";
    private static final String FOUND = "found";
    private static final String MISSING = "missing";
    private static final int FILE_LIMIT = 300;
    private static final int READ_LIMIT = 600 * 1024;
    private static final long MISSING_TTL_MS = 24L * 60L * 60L * 1000L;

    private final Path folder;

    LyricsCache(Path folder) {
        this.folder = folder;
    }

    LrclibClient.Outcome read(String key, long durationMs, long now) {
        Path file = fileOf(key);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > READ_LIMIT) return null;
            String[] parts = Files.readString(file, StandardCharsets.UTF_8).split("\n", 3);
            if (parts.length < 2 || !parts[0].equals(key)) return null;
            String[] status = parts[1].split(" ", 2);
            if (status[0].equals(MISSING)) {
                return now - Long.parseLong(status[1].trim()) < MISSING_TTL_MS ? LrclibClient.Outcome.MISSING : null;
            }
            Lyrics lyrics = parts.length == 3 ? LrcParser.parse(parts[2], durationMs) : Lyrics.NONE;
            if (status[0].equals(FOUND) && lyrics.present()) return new LrclibClient.Outcome(LrclibClient.Kind.FOUND, lyrics, parts[2]);
        } catch (IOException | RuntimeException error) {
            BattleCraftMod.LOGGER.debug("lyrics cache entry dropped: {}", error.toString());
        }
        forget(file);
        return null;
    }

    void write(String key, LrclibClient.Outcome outcome, long now) {
        if (outcome.kind() == LrclibClient.Kind.FAILED) return;

        String status = outcome.kind() == LrclibClient.Kind.FOUND ? FOUND : MISSING;
        String body = key + "\n" + status + " " + now + "\n" + outcome.source();
        try {
            Files.createDirectories(folder);
            Path file = fileOf(key);
            Path staging = file.resolveSibling(file.getFileName() + ".part");
            Files.writeString(staging, body, StandardCharsets.UTF_8);
            move(staging, file);
            prune();
        } catch (IOException error) {
            BattleCraftMod.LOGGER.debug("lyrics cache not written: {}", error.toString());
        }
    }

    private static void move(Path staging, Path file) throws IOException {
        try {
            Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void prune() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> listing = Files.list(folder)) {
            listing.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).forEach(files::add);
        }
        if (files.size() <= FILE_LIMIT) return;

        files.sort(Comparator.comparingLong(LyricsCache::modified));
        for (int index = 0; index < files.size() - FILE_LIMIT; index++) forget(files.get(index));
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException error) {
            return 0L;
        }
    }

    private static void forget(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException error) {
            BattleCraftMod.LOGGER.debug("lyrics cache entry kept: {}", error.toString());
        }
    }

    private Path fileOf(String key) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : key.getBytes(StandardCharsets.UTF_8)) {
            hash ^= value & 0xff;
            hash *= 0x100000001b3L;
        }
        return folder.resolve(Long.toHexString(hash) + SUFFIX);
    }
}
