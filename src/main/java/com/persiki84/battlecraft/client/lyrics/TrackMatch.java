package com.persiki84.battlecraft.client.lyrics;

// WHY: длительности мало: свободный запрос «Pink Floyd Echoes» отдаёт Money, Time и Hey You, и
// WHY: любая песня артиста подходящей длины встала бы чужим текстом. Название обязано совпасть,
// WHY: исполнитель тоже, если он известен, длина в пределах DURATION_SLACK_MS
final class TrackMatch {
    private static final long DURATION_SLACK_MS = 3000L;

    private TrackMatch() {}

    static boolean fits(TrackQuery query, String title, String artist, double seconds) {
        boolean timed = Double.isFinite(seconds) && seconds > 0.0
                && Math.abs(seconds * 1000.0 - query.durationMs()) <= DURATION_SLACK_MS;
        if (!timed || !overlaps(TrackQuery.fold(title), TrackQuery.fold(query.title()))) return false;
        return query.artist().isEmpty() || overlaps(TrackQuery.fold(artist), TrackQuery.fold(query.leadArtist()));
    }

    static double score(TrackQuery query, String title, String artist, double seconds) {
        double score = TrackQuery.fold(title).equals(TrackQuery.fold(query.title())) ? 4.0 : 2.0;
        if (!query.artist().isEmpty()) score += TrackQuery.fold(artist).equals(TrackQuery.fold(query.artist())) ? 3.0 : 1.0;
        return score - Math.abs(seconds * 1000.0 - query.durationMs()) / 1000.0;
    }

    private static boolean overlaps(String found, String wanted) {
        return !found.isEmpty() && !wanted.isEmpty() && (found.contains(wanted) || wanted.contains(found));
    }
}
