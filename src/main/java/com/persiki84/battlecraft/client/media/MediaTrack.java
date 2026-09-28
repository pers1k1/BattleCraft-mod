package com.persiki84.battlecraft.client.media;

public record MediaTrack(MediaSource source, MediaSource service, String app, String title, String artist,
                         String status, boolean playing, boolean blind, long positionMs, long durationMs,
                         long sampledAt, long artStamp, int controls) {

    public static final int CAN_TOGGLE = 1;
    public static final int CAN_NEXT = 2;
    public static final int CAN_PREVIOUS = 4;
    public static final int CAN_SEEK = 8;

    public static final MediaTrack NONE = new MediaTrack(MediaSource.GENERIC, MediaSource.GENERIC,
            "", "", "", "", false, false, 0L, 0L, 0L, 0L, 0);

    public boolean loading() {
        return status.equals("Changing") || status.equals("Opening");
    }

    // WHY: слепой трек это звук из приложения, которое перестало публиковать сессию Windows: названия
    // WHY: у него нет и не будет, но остров обязан считать его музыкой, а не пустотой
    public boolean present() {
        return !title.isEmpty() || blind;
    }

    public boolean sameTrack(MediaTrack other) {
        return other != null && title.equals(other.title) && artist.equals(other.artist)
                && app.equals(other.app) && blind == other.blind;
    }

    public boolean allows(int control) {
        return present() && !blind && (controls & control) != 0;
    }

    public MediaTrack retimed(boolean nowPlaying, long position, long sampled) {
        return new MediaTrack(source, service, app, title, artist, status, nowPlaying, blind, position,
                durationMs, sampled, artStamp, controls);
    }

    public MediaSource badge() {
        return service.known() ? service : source;
    }

    public String origin() {
        MediaSource shown = badge();
        return shown.known() ? shown.label() : MediaSource.nameOf(app);
    }

    public long elapsedMs(long now) {
        if (durationMs <= 0L) return positionMs;

        long drift = playing ? Math.max(0L, now - sampledAt) : 0L;
        return Math.min(durationMs, positionMs + drift);
    }

    public float progress(long now) {
        if (durationMs <= 0L) return 0.0f;
        return Math.min(1.0f, elapsedMs(now) / (float) durationMs);
    }
}
