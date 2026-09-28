package com.persiki84.battlecraft.client.wallpaper;

public record WallpaperEntry(String id, String name, Kind kind, int frames, float fps, int width, int height) {
    public enum Kind {
        IMAGE,
        ANIMATED
    }
}
