package com.persiki84.battlecraft.client.wallpaper;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

final class WallpaperCache {
    static final String FRAMES = "frames";
    static final String RAW = "raw";

    private WallpaperCache() {}

    static Path frames(Path cache) {
        return cache.resolve(FRAMES);
    }

    static Path frame(Path cache, int index) {
        return frames(cache).resolve(String.format(Locale.ROOT, "%06d.jpg", index));
    }

    static Path[] framePaths(Path cache, int count) {
        Path[] paths = new Path[count];
        for (int index = 0; index < count; index++) paths[index] = frame(cache, index);
        return paths;
    }

    static void clear(Path cache) throws IOException {
        if (!Files.exists(cache, LinkOption.NOFOLLOW_LINKS)) return;

        Files.walkFileTree(cache, new Eraser());
    }

    // WHY: Files.walk считает junction Windows обычной папкой и заходит в неё, то есть стёр бы то,
    // WHY: на что она указывает, за пределами кеша. Ссылка удаляется сама, без захода внутрь; только
    // WHY: настоящая непустая папка с точкой повторной обработки (облачная) отвечает «не пуста» и
    // WHY: чистится обычно, а любой другой отказ обрывает очистку, а не ведёт внутрь.
    // WHY: Ошибки обхода приходят как IOException, а не UncheckedIOException, которую удаление обоев
    // WHY: в рендер-потоке и учёт неудачного импорта не ловили
    private static final class Eraser extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
            if (!attributes.isOther()) return unsealed(directory);
            try {
                Files.delete(directory);
                return FileVisitResult.SKIP_SUBTREE;
            } catch (DirectoryNotEmptyException filled) {
                return unsealed(directory);
            }
        }

        // WHY: meta уходит первой: очистка, оборванная на середине, не оставляет meta над
        // WHY: половиной кадров, и такой кеш не считается готовыми обоями
        private static FileVisitResult unsealed(Path directory) throws IOException {
            Files.deleteIfExists(directory.resolve(WallpaperMeta.FILE));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
            if (failure != null) throw failure;
            Files.deleteIfExists(directory);
            return FileVisitResult.CONTINUE;
        }
    }
}
