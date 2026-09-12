package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.media.MediaTrack;

import java.util.ArrayDeque;
import java.util.Deque;

// WHY: Windows не сообщает, вперёд или назад листнул игрок, поэтому сторона разворота достаётся
// WHY: из стопки уже слышанного: «предыдущий» возвращает на её вершину и снимает её, «следующий»
// WHY: уводит в незнакомый трек и кладёт уходящий сверху. Двойное нажатие назад так тоже читается
public final class IslandOrder {
    private static final int DEPTH = 8;
    private static final long ENDING_SLACK_MS = 3500L;

    private static final Deque<String> heard = new ArrayDeque<>();

    private static int pending = 1;

    private IslandOrder() {}

    public static void note(MediaTrack leaving, long elapsedMs, MediaTrack arriving) {
        if (key(arriving).equals(heard.peek()) && !finished(leaving, elapsedMs)) {
            heard.pop();
            pending = -1;
            return;
        }

        heard.push(key(leaving));
        while (heard.size() > DEPTH) heard.removeLast();
        pending = 1;
    }

    public static int pending() {
        return pending;
    }

    public static void forget() {
        heard.clear();
        pending = 1;
    }

    // WHY: доигравший до конца трек сменяется сам, и такая смена всегда вперёд: без этой проверки
    // WHY: плейлист из двух треков крутил бы карточку назад через раз
    private static boolean finished(MediaTrack leaving, long elapsedMs) {
        return leaving.durationMs() > 0L && elapsedMs >= leaving.durationMs() - ENDING_SLACK_MS;
    }

    private static String key(MediaTrack track) {
        return track.app() + '\u0000' + track.title() + '\u0000' + track.artist();
    }
}
