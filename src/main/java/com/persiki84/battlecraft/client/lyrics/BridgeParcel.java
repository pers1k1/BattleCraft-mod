package com.persiki84.battlecraft.client.lyrics;

import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.StringReader;

// WHY: посылку шлёт расширение Spicetify, но порт слышит любой процесс машины: поля читаются с
// WHY: пределами, чужой тип пропускается, а текст разбирают те же парсеры, что и ответы серверов
final class BridgeParcel {
    private static final int VERSION = 1;
    private static final int FIELD_LIMIT = 300;
    private static final int TEXT_LIMIT = 512 * 1024;
    private static final double SHORTEST_MS = 15_000.0;
    private static final double LONGEST_MS = 3.0 * 60.0 * 60.0 * 1000.0;

    private int version;
    private String title = "";
    private String artist = "";
    private double durationMs = -1.0;
    private String format = "";
    private String text = "";

    private BridgeParcel() {}

    static BridgeDrop read(String body) throws IOException {
        BridgeParcel parcel = new BridgeParcel();
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            reader.beginObject();
            while (reader.hasNext()) parcel.field(reader, reader.nextName());
            reader.endObject();
        }
        return parcel.drop();
    }

    private void field(JsonReader reader, String name) throws IOException {
        switch (name) {
            case "v" -> version = (int) LyricsJson.number(reader);
            case "title" -> title = LyricText.clean(LyricsJson.text(reader, FIELD_LIMIT), FIELD_LIMIT);
            case "artist" -> artist = LyricText.clean(LyricsJson.text(reader, FIELD_LIMIT), FIELD_LIMIT);
            case "durationMs" -> durationMs = LyricsJson.number(reader);
            case "format" -> format = LyricsJson.text(reader, FIELD_LIMIT);
            case "text" -> text = LyricsJson.text(reader, TEXT_LIMIT);
            default -> reader.skipValue();
        }
    }

    private BridgeDrop drop() {
        boolean timed = Double.isFinite(durationMs) && durationMs >= SHORTEST_MS && durationMs <= LONGEST_MS;
        boolean known = format.equals(LyricsOutcome.Format.YRC.id()) || format.equals(LyricsOutcome.Format.LRC.id());
        if (version != VERSION || title.isEmpty() || !timed || !known) return null;

        long length = Math.round(durationMs);
        LyricsOutcome outcome = LyricsOutcome.found(text, LyricsOutcome.Format.byId(format), length);
        return outcome.found() ? new BridgeDrop(title, artist, length, outcome) : null;
    }
}
