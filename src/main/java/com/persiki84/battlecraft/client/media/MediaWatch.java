package com.persiki84.battlecraft.client.media;

public final class MediaWatch {
    public static final int BANDS = 6;

    private static final long STALL_WINDOW_MS = 1500L;
    private static final long STALL_SLACK_MS = 250L;

    private static volatile MediaTrack track = MediaTrack.NONE;
    private static volatile boolean stalled;
    private static volatile float[] bands = new float[BANDS];
    private static volatile long retryAt;
    private static boolean wanted;

    private static long anchorPosition;
    private static long anchorAt;

    private MediaWatch() {}

    public static void publish(MediaTrack value) {
        MediaTrack fresh = value == null ? MediaTrack.NONE : value;
        stalled = fresh.loading() || frozen(fresh);
        track = fresh;
    }

    private static boolean frozen(MediaTrack fresh) {
        if (!fresh.playing() || fresh.blind() || !fresh.sameTrack(track)) {
            anchorPosition = fresh.positionMs();
            anchorAt = fresh.sampledAt();
            return false;
        }

        long moved = fresh.positionMs() - anchorPosition;
        long waited = fresh.sampledAt() - anchorAt;
        if (moved > STALL_SLACK_MS || waited <= 0L) {
            anchorPosition = fresh.positionMs();
            anchorAt = fresh.sampledAt();
            return false;
        }
        return waited >= STALL_WINDOW_MS;
    }

    // WHY: полосы спектра приходят с моста в двадцать раз чаще, чем сведения о треке, и лежат
    // WHY: отдельно: их нельзя гасить публикацией трека и нельзя ждать до следующего опроса SMTC
    public static void bands(float[] fresh) {
        if (fresh != null && fresh.length == BANDS) bands = fresh;
    }

    public static float band(int index) {
        float[] shown = bands;
        return index >= 0 && index < shown.length ? shown[index] : 0.0f;
    }

    public static MediaTrack current() {
        return track;
    }

    public static boolean stalled() {
        return stalled;
    }

    public static void retryAfter(long stamp) {
        retryAt = stamp;
    }

    public static void want(boolean value) {
        if (wanted == value) return;

        wanted = value;
        if (value) {
            MediaBridge.start();
            return;
        }
        MediaBridge.stop();
        track = MediaTrack.NONE;
        bands = new float[BANDS];
    }

    public static void tick() {
        if (!wanted || MediaBridge.running() || !MediaBridge.available()) return;
        if (retryAt != 0L && System.currentTimeMillis() < retryAt) return;

        retryAt = 0L;
        MediaBridge.start();
    }
}
