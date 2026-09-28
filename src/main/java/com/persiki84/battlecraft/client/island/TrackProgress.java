package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.media.MediaTrack;

public final class TrackProgress {
    private static final float SEEK_SECONDS = 0.46f;
    private static final long SEEK_JUMP_MS = 1200L;

    private float shown;
    private float anchor;
    private float phase = 1.0f;

    private MediaTrack watched = MediaTrack.NONE;
    private long stampedAt;
    private long stampedElapsed;

    public void advance(MediaTrack track, float delta) {
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
        stamp(track, elapsed, now);
        travel(elapsed / (float) track.durationMs(), delta);
    }

    // WHY: позиция от SMTC достраивается на клиенте ходом часов, поэтому обычное проигрывание
    // WHY: даёт ровно прошедшее время: расхождение с ним и есть перемотка, сделанная в плеере
    private boolean jumped(MediaTrack track, long elapsed, long now) {
        long expected = stampedElapsed + (track.playing() ? now - stampedAt : 0L);
        return Math.abs(elapsed - expected) > SEEK_JUMP_MS;
    }

    private void stamp(MediaTrack track, long elapsed, long now) {
        watched = track;
        stampedAt = now;
        stampedElapsed = elapsed;
    }

    // WHY: перемотка, которую игрок сам дотащил до места, не должна ехать туда второй раз: полоса
    // WHY: уже стоит под курсором, и поездка от старой точки выглядела бы откатом
    public void land(MediaTrack track) {
        if (!track.present() || track.durationMs() <= 0L) return;

        long now = System.currentTimeMillis();
        long elapsed = track.elapsedMs(now);
        stamp(track, elapsed, now);
        shown = elapsed / (float) track.durationMs();
        phase = 1.0f;
    }

    private void travel(float target, float delta) {
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

    public void forget() {
        shown = 0.0f;
        anchor = 0.0f;
        phase = 1.0f;
        watched = MediaTrack.NONE;
        stampedAt = 0L;
        stampedElapsed = 0L;
    }

    public float value() {
        return shown;
    }

    // WHY: сама поездка полосы читается плохо на трёхминутном треке, поэтому у кромки заливки
    // WHY: на время перемотки зажигается голова, и видно, куда полоса едет
    public float surge() {
        if (phase >= 1.0f) return 0.0f;

        return (float) Math.sin(Math.PI * phase);
    }
}
