package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.media.MediaTrack;

public final class IslandProgress {
    private static final float SEEK_SECONDS = 0.46f;
    private static final long SEEK_JUMP_MS = 1200L;

    private static float shown;
    private static float anchor;
    private static float phase = 1.0f;

    private static MediaTrack watched = MediaTrack.NONE;
    private static long stampedAt;
    private static long stampedElapsed;

    private IslandProgress() {}

    public static void advance(MediaTrack track, float delta) {
        long now = System.currentTimeMillis();
        if (!track.present() || track.durationMs() <= 0L) {
            forget();
            return;
        }

        long elapsed = track.elapsedMs(now);
        if (track.sameTrack(watched) && jumped(track, elapsed, now)) {
            anchor = shown;
            phase = 0.0f;
        } else if (!track.sameTrack(watched)) {
            phase = 1.0f;
        }
        watched = track;
        stampedAt = now;
        stampedElapsed = elapsed;

        travel(elapsed / (float) track.durationMs(), delta);
    }

    // WHY: позиция от SMTC достраивается на клиенте ходом часов, поэтому обычное проигрывание
    // WHY: даёт ровно прошедшее время: расхождение с ним и есть перемотка, сделанная в плеере
    private static boolean jumped(MediaTrack track, long elapsed, long now) {
        long expected = stampedElapsed + (track.playing() ? now - stampedAt : 0L);
        return Math.abs(elapsed - expected) > SEEK_JUMP_MS;
    }

    private static void travel(float target, float delta) {
        if (phase >= 1.0f) {
            shown = target;
            return;
        }

        phase = Math.min(1.0f, phase + delta / SEEK_SECONDS);
        shown = anchor + (target - anchor) * ease(phase);
    }

    private static float ease(float weight) {
        float inv = 1.0f - weight;
        return 1.0f - inv * inv * inv;
    }

    public static void forget() {
        shown = 0.0f;
        anchor = 0.0f;
        phase = 1.0f;
        watched = MediaTrack.NONE;
        stampedAt = 0L;
        stampedElapsed = 0L;
    }

    public static float value() {
        return shown;
    }

    // WHY: сама поездка полосы читается плохо на трёхминутном треке, поэтому у кромки заливки
    // WHY: на время перемотки зажигается голова, и видно, куда полоса едет
    public static float surge() {
        if (phase >= 1.0f) return 0.0f;

        return (float) Math.sin(Math.PI * phase);
    }
}
