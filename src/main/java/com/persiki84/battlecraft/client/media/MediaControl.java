package com.persiki84.battlecraft.client.media;

// WHY: команда уходит мосту строкой, а ответ плеера виден только со следующим опросом SMTC через
// WHY: полсекунды-секунду. Интерфейс не ждёт его: модель сразу получает ожидаемое состояние, и оно
// WHY: держится поверх опросов, пока плеер его не подтвердит или не выйдет срок доверия. Срок
// WHY: длинный: MediaPlayer Windows на стенде не обновил шкалу SMTC после перемотки вовсе, и
// WHY: короткий срок откатывал полосу к старой позиции через две секунды после отпускания
public final class MediaControl {
    private static final long COMMAND_GAP_MS = 200L;
    private static final long TRUST_MS = 6000L;
    private static final long AGREE_SLACK_MS = 1500L;

    private static final Object lock = new Object();
    private static MediaTrack expected;
    private static long expectedUntil;
    private static long lastCommandAt;

    private MediaControl() {}

    public static boolean available() {
        MediaTrack track = MediaWatch.current();
        return MediaBridge.running() && track.present() && !track.blind() && track.controls() != 0;
    }

    public static boolean playing() {
        return MediaWatch.current().playing();
    }

    public static boolean canToggle() {
        return MediaBridge.running() && MediaWatch.current().allows(MediaTrack.CAN_TOGGLE);
    }

    public static boolean canNext() {
        return MediaBridge.running() && MediaWatch.current().allows(MediaTrack.CAN_NEXT);
    }

    public static boolean canPrevious() {
        return MediaBridge.running() && MediaWatch.current().allows(MediaTrack.CAN_PREVIOUS);
    }

    public static boolean canSeek() {
        MediaTrack track = MediaWatch.current();
        return MediaBridge.running() && track.allows(MediaTrack.CAN_SEEK) && track.durationMs() > 0L;
    }

    public static void togglePlay() {
        MediaTrack track = MediaWatch.current();
        if (!track.allows(MediaTrack.CAN_TOGGLE)) return;
        if (!send(track, track.playing() ? "pause" : "play", 0L)) return;

        long now = System.currentTimeMillis();
        expect(track.retimed(!track.playing(), track.elapsedMs(now), now), now);
    }

    public static void next() {
        MediaTrack track = MediaWatch.current();
        if (track.allows(MediaTrack.CAN_NEXT)) send(track, "next", 0L);
    }

    public static void previous() {
        MediaTrack track = MediaWatch.current();
        if (track.allows(MediaTrack.CAN_PREVIOUS)) send(track, "previous", 0L);
    }

    public static void seek(long positionMs) {
        MediaTrack track = MediaWatch.current();
        if (!track.allows(MediaTrack.CAN_SEEK) || track.durationMs() <= 0L) return;

        long target = Math.max(0L, Math.min(track.durationMs(), positionMs));
        if (!send(track, "seek", target)) return;

        long now = System.currentTimeMillis();
        expect(track.retimed(track.playing(), target, now), now);
    }

    // WHY: часы, переведённые назад, давали отрицательный промежуток, и кнопки молчали, пока время
    // WHY: не догоняло прошлую команду: отрицательный промежуток считается прошедшим
    private static boolean send(MediaTrack track, String verb, long positionMs) {
        long now = System.currentTimeMillis();
        long since = now - lastCommandAt;
        if (since >= 0L && since < COMMAND_GAP_MS) return false;
        if (!MediaBridge.command(verb, positionMs, track)) return false;

        lastCommandAt = now;
        return true;
    }

    private static void expect(MediaTrack optimistic, long now) {
        synchronized (lock) {
            expected = optimistic;
            expectedUntil = now + TRUST_MS;
            MediaWatch.publish(optimistic);
        }
    }

    static void deliver(MediaTrack reported) {
        synchronized (lock) {
            MediaWatch.publish(reconcile(reported, System.currentTimeMillis()));
        }
    }

    private static MediaTrack reconcile(MediaTrack reported, long now) {
        if (expected == null) return reported;
        if (now > expectedUntil || !reported.sameTrack(expected) || agrees(reported, now)) {
            expected = null;
            return reported;
        }
        return reported.retimed(expected.playing(), expected.positionMs(), expected.sampledAt());
    }

    // WHY: шкала, снятая плеером после команды, уже учитывает её, чем бы ни кончилась перемотка;
    // WHY: снятая раньше достраивается ходом часов от старой точки и подтверждает только совпадением
    private static boolean agrees(MediaTrack reported, long now) {
        if (reported.playing() != expected.playing()) return false;
        if (reported.sampledAt() > expected.sampledAt()) return true;
        return Math.abs(reported.elapsedMs(now) - expected.elapsedMs(now)) <= AGREE_SLACK_MS;
    }

    static void forget() {
        synchronized (lock) {
            expected = null;
        }
    }
}
