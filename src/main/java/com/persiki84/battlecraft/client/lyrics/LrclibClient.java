package com.persiki84.battlecraft.client.lyrics;

import com.persiki84.battlecraft.BattleCraftMod;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// WHY: поиск отдаёт двадцать записей, и в каждой полный простой текст и YAML-копия: у рэпа ответ
// WHY: под мегабайт. Разбор идёт потоком и пропускает ненужные поля, ответ режется по BODY_LIMIT, а
// WHY: чтение тела закрывается по общему дедлайну: таймаут запроса в JDK кончается на заголовках,
// WHY: и оборванная посреди тела связь вешала бы единственный поток лирики навсегда
final class LrclibClient {
    private static final String SEARCH = "https://lrclib.net/api/search?";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4L);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6L);
    private static final long BODY_DEADLINE_SECONDS = 10L;
    private static final long BODY_LIMIT = 4L * 1024L * 1024L;
    private static final int ENTRY_LIMIT = 40;
    private static final int CANDIDATE_LIMIT = 12;
    private static final int LYRICS_LIMIT = 512 * 1024;
    private static final long DURATION_SLACK_MS = 3000L;
    private static final long FALLBACK_PAUSE_MS = 500L;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final String agent;

    LrclibClient(String agent) {
        this.agent = agent;
    }

    enum Kind { FOUND, MISSING, FAILED }

    record Outcome(Kind kind, Lyrics lyrics, String source) {
        static final Outcome MISSING = new Outcome(Kind.MISSING, Lyrics.NONE, "");
        static final Outcome FAILED = new Outcome(Kind.FAILED, Lyrics.NONE, "");
    }

    private record Entry(String title, String artist, double seconds, boolean instrumental, boolean wordSync,
                         String synced) {}

    // WHY: сначала точный поиск с полным исполнителем («Simon and Garfunkel», «Tyler, The Creator»),
    // WHY: потом с первым из соавторов, потом свободный запрос
    Outcome find(TrackQuery query) throws InterruptedException {
        Outcome outcome = search(query, exact(query.title(), query.artist()));
        if (outcome.kind() != Kind.MISSING || query.artist().isEmpty()) return outcome;
        if (!query.leadArtist().equals(query.artist())) {
            Thread.sleep(FALLBACK_PAUSE_MS);
            outcome = search(query, exact(query.title(), query.leadArtist()));
            if (outcome.kind() != Kind.MISSING) return outcome;
        }
        Thread.sleep(FALLBACK_PAUSE_MS);
        return search(query, "q=" + encode(query.leadArtist() + " " + query.title()));
    }

    private static String exact(String title, String artist) {
        return "track_name=" + encode(title) + (artist.isEmpty() ? "" : "&artist_name=" + encode(artist));
    }

    private Outcome search(TrackQuery query, String parameters) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH + parameters)).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", agent).header("Accept", "application/json").GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            InputStream body = response.body();
            CompletableFuture.delayedExecutor(BODY_DEADLINE_SECONDS, TimeUnit.SECONDS).execute(() -> close(body));
            try (InputStream bounded = new Bounded(body)) {
                if (response.statusCode() != 200) return Outcome.FAILED;
                return choose(query, entries(bounded));
            }
        } catch (IOException | RuntimeException error) {
            return Outcome.FAILED;
        }
    }

    private static void close(InputStream body) {
        try {
            body.close();
        } catch (IOException error) {
            BattleCraftMod.LOGGER.debug("lyrics response not closed: {}", error.toString());
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
            String name = reader.nextName();
            switch (name) {
                case "trackName" -> title = text(reader, LrcParser.TEXT_LIMIT * 4);
                case "artistName" -> artist = text(reader, LrcParser.TEXT_LIMIT * 4);
                case "duration" -> seconds = number(reader);
                case "instrumental" -> instrumental = flag(reader);
                case "hasWordSync" -> wordSync = flag(reader);
                case "syncedLyrics" -> synced = text(reader, LYRICS_LIMIT);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        return new Entry(title, artist, seconds, instrumental, wordSync, synced);
    }

    private static String text(JsonReader reader, int limit) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            reader.skipValue();
            return "";
        }
        String value = reader.nextString();
        return value.length() > limit ? "" : value;
    }

    private static double number(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) {
            reader.skipValue();
            return -1.0;
        }
        return reader.nextDouble();
    }

    private static boolean flag(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BOOLEAN) {
            reader.skipValue();
            return false;
        }
        return reader.nextBoolean();
    }

    private record Candidate(Entry entry, double score) {}

    private static Outcome choose(TrackQuery query, List<Entry> entries) {
        List<Candidate> candidates = new ArrayList<>();
        for (Entry entry : entries) {
            if (fits(query, entry)) candidates.add(new Candidate(entry, score(query, entry)));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        for (int index = 0; index < candidates.size() && index < CANDIDATE_LIMIT; index++) {
            Entry entry = candidates.get(index).entry();
            if (entry.instrumental()) return Outcome.MISSING;
            Lyrics lyrics = LrcParser.parse(entry.synced(), query.durationMs());
            if (lyrics.present()) return new Outcome(Kind.FOUND, lyrics, entry.synced());
        }
        return Outcome.MISSING;
    }

    // WHY: длительности мало: свободный запрос «Pink Floyd Echoes» отдаёт Money, Time и Hey You, и
    // WHY: любая песня артиста подходящей длины встала бы чужим текстом. Название обязано совпасть,
    // WHY: исполнитель тоже, если он известен
    private static boolean fits(TrackQuery query, Entry entry) {
        boolean timed = Double.isFinite(entry.seconds()) && entry.seconds() > 0.0
                && Math.abs(entry.seconds() * 1000.0 - query.durationMs()) <= DURATION_SLACK_MS;
        if (!timed || (!entry.instrumental() && entry.synced().isBlank())) return false;
        if (!overlaps(TrackQuery.fold(entry.title()), TrackQuery.fold(query.title()))) return false;
        return query.artist().isEmpty() || overlaps(TrackQuery.fold(entry.artist()), TrackQuery.fold(query.leadArtist()));
    }

    private static boolean overlaps(String found, String wanted) {
        return !found.isEmpty() && !wanted.isEmpty() && (found.contains(wanted) || wanted.contains(found));
    }

    private static double score(TrackQuery query, Entry entry) {
        String title = TrackQuery.fold(entry.title());
        String artist = TrackQuery.fold(entry.artist());
        double score = title.equals(TrackQuery.fold(query.title())) ? 4.0 : 2.0;
        if (!query.artist().isEmpty()) score += artist.equals(TrackQuery.fold(query.artist())) ? 3.0 : 1.0;
        if (entry.wordSync()) score += 1.0;
        return score - Math.abs(entry.seconds() * 1000.0 - query.durationMs()) / 1000.0;
    }

    // WHY: на пробел, закодированный плюсом, сервер стабильно отвечает 503 «server is busy», а тот же
    // WHY: запрос с %20 отдаёт 200: URLEncoder кодирует форму, а не путь запроса
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static final class Bounded extends FilterInputStream {
        private long left = BODY_LIMIT;

        private Bounded(InputStream source) {
            super(source);
        }

        @Override
        public int read() throws IOException {
            if (left <= 0L) throw new IOException("lyrics response larger than " + BODY_LIMIT);
            int value = super.read();
            if (value >= 0) left--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (left <= 0L) throw new IOException("lyrics response larger than " + BODY_LIMIT);
            int read = super.read(buffer, offset, (int) Math.min(length, left));
            if (read > 0) left -= read;
            return read;
        }
    }
}
