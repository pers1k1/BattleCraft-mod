package com.persiki84.battlecraft.client.lyrics;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// WHY: поиск отдаёт двадцать записей, и в каждой полный простой текст и YAML-копия: у рэпа ответ под
// WHY: мегабайт. Разбор идёт потоком и пропускает ненужные поля
final class LrclibClient {
    private static final String SEARCH = "https://lrclib.net/api/search?";
    private static final int ENTRY_LIMIT = 40;
    private static final int CANDIDATE_LIMIT = 12;
    private static final int LYRICS_LIMIT = 512 * 1024;
    private static final long FALLBACK_PAUSE_MS = 500L;

    private final LyricsHttp http;

    LrclibClient(LyricsHttp http) {
        this.http = http;
    }

    private record Entry(String title, String artist, double seconds, boolean instrumental, boolean wordSync,
                         String synced) {}

    // WHY: сначала точный поиск с полным исполнителем («Simon and Garfunkel», «Tyler, The Creator»),
    // WHY: потом с первым из соавторов, потом свободный запрос
    LyricsOutcome find(TrackQuery query) throws InterruptedException {
        LyricsOutcome outcome = search(query, exact(query.title(), query.artist()));
        if (outcome.kind() != LyricsOutcome.Kind.MISSING || query.artist().isEmpty()) return outcome;
        if (!query.leadArtist().equals(query.artist())) {
            Thread.sleep(FALLBACK_PAUSE_MS);
            outcome = search(query, exact(query.title(), query.leadArtist()));
            if (outcome.kind() != LyricsOutcome.Kind.MISSING) return outcome;
        }
        Thread.sleep(FALLBACK_PAUSE_MS);
        return search(query, "q=" + LyricsHttp.encode(query.leadArtist() + " " + query.title()));
    }

    private static String exact(String title, String artist) {
        return "track_name=" + LyricsHttp.encode(title) + (artist.isEmpty() ? "" : "&artist_name=" + LyricsHttp.encode(artist));
    }

    private LyricsOutcome search(TrackQuery query, String parameters) throws InterruptedException {
        try {
            LyricsHttp.Reply reply = http.get(SEARCH + parameters);
            try (InputStream body = reply.body()) {
                if (reply.status() != 200) return LyricsOutcome.FAILED;
                return choose(query, entries(body));
            }
        } catch (IOException | RuntimeException error) {
            return LyricsOutcome.FAILED;
        }
    }

    private static List<Entry> entries(InputStream body) throws IOException {
        List<Entry> found = new ArrayList<>();
        try (JsonReader reader = new JsonReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            reader.beginArray();
            while (reader.hasNext()) {
                if (found.size() >= ENTRY_LIMIT || reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue();
                    continue;
                }
                found.add(entry(reader));
            }
        }
        return found;
    }

    private static Entry entry(JsonReader reader) throws IOException {
        String title = "";
        String artist = "";
        double seconds = -1.0;
        boolean instrumental = false;
        boolean wordSync = false;
        String synced = "";
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "trackName" -> title = LyricsJson.text(reader, LrcParser.TEXT_LIMIT * 4);
                case "artistName" -> artist = LyricsJson.text(reader, LrcParser.TEXT_LIMIT * 4);
                case "duration" -> seconds = LyricsJson.number(reader);
                case "instrumental" -> instrumental = LyricsJson.flag(reader);
                case "hasWordSync" -> wordSync = LyricsJson.flag(reader);
                case "syncedLyrics" -> synced = LyricsJson.text(reader, LYRICS_LIMIT);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        return new Entry(title, artist, seconds, instrumental, wordSync, synced);
    }

    private record Candidate(Entry entry, double score) {}

    private static LyricsOutcome choose(TrackQuery query, List<Entry> entries) {
        List<Candidate> candidates = new ArrayList<>();
        for (Entry entry : entries) {
            boolean usable = entry.instrumental() || !entry.synced().isBlank();
            if (usable && TrackMatch.fits(query, entry.title(), entry.artist(), entry.seconds())) {
                double bonus = entry.wordSync() ? 1.0 : 0.0;
                candidates.add(new Candidate(entry, TrackMatch.score(query, entry.title(), entry.artist(), entry.seconds()) + bonus));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        for (int index = 0; index < candidates.size() && index < CANDIDATE_LIMIT; index++) {
            Entry entry = candidates.get(index).entry();
            if (entry.instrumental()) return LyricsOutcome.MISSING;
            LyricsOutcome outcome = LyricsOutcome.found(entry.synced(), LyricsOutcome.Format.LRC, query.durationMs());
            if (outcome.found()) return outcome;
        }
        return LyricsOutcome.MISSING;
    }
}
