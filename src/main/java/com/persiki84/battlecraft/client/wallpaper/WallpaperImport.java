package com.persiki84.battlecraft.client.wallpaper;

public final class WallpaperImport {
    public enum State {
        RUNNING,
        DONE,
        FAILED
    }

    private final String id;
    private volatile State state = State.RUNNING;
    private volatile float progress;
    private volatile String failureKey = "";

    WallpaperImport(String id) {
        this.id = id;
    }

    static WallpaperImport failed(String id, String key) {
        WallpaperImport attempt = new WallpaperImport(id);
        attempt.fail(key);
        return attempt;
    }

    public String id() {
        return id;
    }

    public State state() {
        return state;
    }

    public float progress() {
        return progress;
    }

    public String failureKey() {
        return failureKey;
    }

    void advance(float share) {
        progress = Math.max(0.0f, Math.min(1.0f, share));
    }

    void finish() {
        progress = 1.0f;
        state = State.DONE;
    }

    void fail(String key) {
        failureKey = key;
        state = State.FAILED;
    }
}
