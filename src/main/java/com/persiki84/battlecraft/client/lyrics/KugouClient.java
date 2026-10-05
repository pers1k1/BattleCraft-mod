package com.persiki84.battlecraft.client.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;

// WHY: у Kugou пословный текст KRC без токена, и он закрывает западные песни, которых нет в yrc
// WHY: NetEase. Каталог китайский: кириллицу поиск не понимает и отдаёт посторонние песни, поэтому
// WHY: для кириллических названий запрос не шлётся. Песня подходит только при совпадении названия,
// WHY: исполнителя и длины, как у остальных источников
final class KugouClient {
    private static final String SEARCH = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=&keyword=";
    private static final String DOWNLOAD = "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=krc&charset=utf8&id=";
    private static final long LOOKUP_PAUSE_MS = 400L;

    private final LyricsHttp http;

    KugouClient(LyricsHttp http) {
        this.http = http;
    }

    private record Candidate(String id, String accessKey, String heading, double score) {}

    LyricsOutcome find(TrackQuery query) throws InterruptedException {
        if (cyrillic(query.title()) || cyrillic(query.leadArtist())) return LyricsOutcome.MISSING;
        try {
            String keyword = LyricsHttp.encode(query.leadArtist() + " - " + query.title());
            JsonElement found = json(SEARCH + keyword + "&duration=" + query.durationMs());
            Candidate best = best(query, found);
            if (best == null) return found == null ? LyricsOutcome.FAILED : LyricsOutcome.MISSING;
            Thread.sleep(LOOKUP_PAUSE_MS);
            JsonElement lyric = json(DOWNLOAD + LyricsHttp.encode(best.id()) + "&accesskey=" + LyricsHttp.encode(best.accessKey()));
            return lyric == null ? LyricsOutcome.FAILED : outcome(query, best, lyric);
        } catch (IOException | DataFormatException | RuntimeException error) {
            return LyricsOutcome.FAILED;
        }
    }

    private static boolean cyrillic(String text) {
        return text.codePoints().anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.CYRILLIC);
    }

    private JsonElement json(String url) throws IOException, InterruptedException {
        LyricsHttp.Reply reply = http.get(url);
        try (InputStream body = reply.body()) {
            if (reply.status() != 200) return null;
            return JsonParser.parseString(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Candidate best(TrackQuery query, JsonElement root) {
        Candidate best = null;
        for (JsonElement element : array(object(root), "candidates")) {
            JsonObject candidate = object(element);
            String title = string(candidate, "song");
            String artist = string(candidate, "singer");
            double seconds = number(candidate, "duration") / 1000.0;
            if (!TrackMatch.fits(query, title, artist, seconds)) continue;
            double score = TrackMatch.score(query, title, artist, seconds);
            if (best == null || score > best.score()) {
                best = new Candidate(string(candidate, "id"), string(candidate, "accesskey"), title + " - " + artist, score);
            }
        }
        return best == null || best.id().isEmpty() || best.accessKey().isEmpty() ? null : best;
    }

    private static LyricsOutcome outcome(TrackQuery query, Candidate candidate, JsonElement root)
            throws DataFormatException {
        String content = string(object(root), "content");
        if (content.isEmpty()) return LyricsOutcome.MISSING;
        String yrc = KrcText.toYrc(KrcText.decode(content), candidate.heading());
        return LyricsOutcome.found(yrc, LyricsOutcome.Format.YRC, query.durationMs());
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static double number(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : -1.0;
    }
}
