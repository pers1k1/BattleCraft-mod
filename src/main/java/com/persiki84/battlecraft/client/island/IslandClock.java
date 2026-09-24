package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.media.MediaTrack;

// WHY: Telegram и часть плееров публикуют трек без шкалы времени: позиция и длина нули, и строка
// WHY: стояла «0:00 / 0:00». Для такого трека время отсчитывается здесь, с его появления, и стоит на паузе
final class IslandClock {
    private static MediaTrack counted = MediaTrack.NONE;
    private static long playedMs;
    private static long lastAt;

    private IslandClock() {}

    static long elapsedMs(MediaTrack track, long now) {
        if (track.durationMs() > 0L || track.positionMs() > 0L) return track.elapsedMs(now);

        if (!track.sameTrack(counted)) {
            counted = track;
            playedMs = 0L;
            lastAt = now;
        }
        if (track.playing()) playedMs += Math.max(0L, now - lastAt);
        lastAt = now;
        return playedMs;
    }
}
