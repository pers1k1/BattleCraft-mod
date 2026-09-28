package com.persiki84.battlecraft.client.wallpaper;

final class WallpaperFailure extends Exception {
    static final String FORMAT = "battlecraft.wallpaper.error.format";
    static final String COPY = "battlecraft.wallpaper.error.copy";
    static final String READ = "battlecraft.wallpaper.error.read";
    static final String SIZE = "battlecraft.wallpaper.error.size";
    static final String WRITE = "battlecraft.wallpaper.error.write";
    static final String PLATFORM = "battlecraft.wallpaper.error.platform";
    static final String BRIDGE = "battlecraft.wallpaper.error.bridge";
    static final String CODEC = "battlecraft.wallpaper.error.codec";
    static final String VIDEO = "battlecraft.wallpaper.error.video";
    static final String LARGE = "battlecraft.wallpaper.error.large";
    static final String FULL = "battlecraft.wallpaper.error.full";

    private static final long serialVersionUID = 1L;

    private final String key;

    WallpaperFailure(String key, String detail) {
        super(detail);
        this.key = key;
    }

    String key() {
        return key;
    }
}
