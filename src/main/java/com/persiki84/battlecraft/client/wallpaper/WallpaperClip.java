package com.persiki84.battlecraft.client.wallpaper;

record WallpaperClip(WallpaperEntry.Kind kind, int frames, float fps, int width, int height) {
    static final int MAX_FPS = 30;
    static final int MAX_SECONDS = 20;

    static WallpaperClip still(int width, int height) {
        return new WallpaperClip(WallpaperEntry.Kind.IMAGE, 1, 0.0f, width, height);
    }

    static int frameCount(double seconds, double fps) {
        return Math.max(1, (int) Math.floor(Math.min(seconds, MAX_SECONDS) * fps + 0.01));
    }
}
