package com.persiki84.battlecraft.client.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// WHY: у NetEase Cloud Music есть пословная разметка (yrc) с длительностью каждого слова, и для многих
// WHY: песен только она знает, где певец тянет слово. Песня ищется по названию и исполнителю, а
// WHY: подходит только та, что совпала по названию, исполнителю и длине, как и у LRCLIB
final class NeteaseClient {
    private static final String SEARCH = "https://music.163.com/api/search/get?type=1&limit=10&s=";
    private static final String LYRIC = "https://music.163.com/api/song/lyric/v1?lv=-1&yv=-1&tv=-1&rv=-1&kv=-1&id=";
    private static final long LOOKUP_PAUSE_MS = 400L;

    private final LyricsHttp http;

    NeteaseClient(LyricsHttp http) {
        this.http = http;
    }

    private record Song(long id, double score) {}

    LyricsOutcome find(TrackQuery query) throws InterruptedException {
        try {
            JsonElement found = json(SEARCH + LyricsHttp.encode(query.leadArtist() + " " + query.title()));
            Song song = best(query, found);
            if (song == null) return found == null ? LyricsOutcome.FAILED : LyricsOutcome.MISSING;
            Thread.sleep(LOOKUP_PAUSE_MS);
            JsonElement lyric = json(LYRIC + song.id());
            return lyric == null ? LyricsOutcome.FAILED : outcome(query, lyric);
        } catch (IOException | RuntimeException error) {
            return LyricsOutcome.FAILED;
        }
    }

    private JsonElement json(String url) throws IOException, InterruptedException {
        LyricsHttp.Reply reply = http.get(url);
        try (InputStream body = reply.body()) {
            if (reply.status() != 200) return null;
            return JsonParser.parseString(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Song best(TrackQuery query, JsonElement root) {
        JsonArray songs = array(object(object(root), "result"), "songs");
        Song best = null;
        for (JsonElement element : songs) {
            JsonObject song = object(element);
            String title = string(song, "name");
            String artist = artists(array(song, "artists"));
            double seconds = number(song, "duration") / 1000.0;
            if (!TrackMatch.fits(query, title, artist, seconds)) continue;
            double score = TrackMatch.score(query, title, artist, seconds);
            if (best == null || score > best.score()) best = new Song((long) number(song, "id"), score);
        }
        return best;
    }

    private static LyricsOutcome outcome(TrackQuery query, JsonElement root) {
        JsonObject lyric = object(root);
        LyricsOutcome worded = LyricsOutcome.found(string(object(lyric, "yrc"), "lyric"), LyricsOutcome.Format.YRC, query.durationMs());
        if (worded.found()) return worded;
        return LyricsOutcome.found(string(object(lyric, "lrc"), "lyric"), LyricsOutcome.Format.LRC, query.durationMs());
    }

    private static String artists(JsonArray artists) {
        StringBuilder joined = new StringBuilder();
        for (JsonElement artist : artists) {
            if (joined.length() > 0) joined.append(", ");
            joined.append(string(object(artist), "name"));
        }
        return joined.toString();
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonObject object(JsonObject parent, String key) {
        return object(parent.get(key));
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    private static double number(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : -1.0;
    }
}
