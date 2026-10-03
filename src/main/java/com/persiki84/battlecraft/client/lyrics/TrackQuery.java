package com.persiki84.battlecraft.client.lyrics;

import com.persiki84.battlecraft.client.media.MediaTrack;

import java.util.Locale;
import java.util.regex.Pattern;

// WHY: площадки пишут в название шум, которого нет в базе текстов: «(Official Video)», «[Lyrics]»,
// WHY: «(feat. X)», а YouTube ставит канал «Исполнитель - Topic» или «...VEVO» и кладёт исполнителя
// WHY: в само название через дефис. Без чистки поиск промахивался мимо существующего текста
public record TrackQuery(String title, String artist, String leadArtist, long durationMs) {
    private static final int FIELD_LIMIT = 120;
    private static final long SHORTEST_MS = 15_000L;
    private static final long DURATION_SLACK_MS = 3000L;
    private static final Pattern NOISE = Pattern.compile(
            "\\s*[(\\[{][^)\\]}]*\\b(official|video|audio|lyrics?|visuali[sz]er|hd|hq|4k|mv|m/v|clip|explicit"
                    + "|feat\\.?|ft\\.?|remaster(ed)?)\\b[^)\\]}]*[)\\]}]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEATURING = Pattern.compile("\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANNEL = Pattern.compile("(\\s*-\\s*topic|vevo|\\s+official)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR = Pattern.compile("\\s+[-\u2013\u2014]\\s+");
    private static final Pattern CO_ARTISTS = Pattern.compile("\\s*(,|&| x | and |;)\\s*.*$",
            Pattern.CASE_INSENSITIVE);

    public static TrackQuery of(MediaTrack track) {
        String title = strip(LyricText.clean(track.title(), FIELD_LIMIT));
        String artist = CHANNEL.matcher(LyricText.clean(track.artist(), FIELD_LIMIT)).replaceAll("").trim();
        String[] halves = SEPARATOR.split(title, 2);
        if (halves.length == 2 && (artist.isEmpty() || sameName(halves[0], artist))) {
            artist = halves[0].trim();
            title = halves[1].trim();
        }
        String lead = CO_ARTISTS.matcher(artist).replaceAll("").trim();
        return new TrackQuery(title, artist, lead.isEmpty() ? artist : lead, track.durationMs());
    }

    private static String strip(String title) {
        return FEATURING.matcher(NOISE.matcher(title).replaceAll("")).replaceAll("").trim();
    }

    private static boolean sameName(String left, String right) {
        return fold(left).equals(fold(right)) || fold(right).startsWith(fold(left));
    }

    static String fold(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    // WHY: мост читает свойства трека и шкалу времени по отдельности, и новое название приходит то
    // WHY: без длины, то с длиной прошлого трека. Запрос пересобирается, когда длина разошлась больше
    // WHY: чем на допуск поиска, но не на каждом дрожании миллисекунд
    public boolean stale(long reported) {
        return Math.abs(reported - durationMs) > DURATION_SLACK_MS;
    }

    public boolean searchable() {
        return !title.isEmpty() && durationMs >= SHORTEST_MS;
    }

    // WHY: длина в ключе, чтобы «не найдено» для запроса с чужой длиной не закрыло тот же трек с верной
    public String key() {
        return fold(artist) + '\u0001' + fold(title) + '\u0001' + Math.round(durationMs / 1000.0);
    }
}
