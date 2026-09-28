package com.persiki84.battlecraft.client.wallpaper;

import java.util.Locale;
import java.util.Set;

enum WallpaperFormat {
    STILL(Set.of("png", "jpg", "jpeg", "bmp")),
    GIF(Set.of("gif")),
    VIDEO(Set.of("mp4", "mov", "m4v", "wmv", "avi", "mkv", "webm"));

    private final Set<String> extensions;

    WallpaperFormat(Set<String> extensions) {
        this.extensions = extensions;
    }

    static WallpaperFormat of(String fileName) {
        String extension = extension(fileName);
        for (WallpaperFormat format : values()) {
            if (format.extensions.contains(extension)) return format;
        }
        return null;
    }

    static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
