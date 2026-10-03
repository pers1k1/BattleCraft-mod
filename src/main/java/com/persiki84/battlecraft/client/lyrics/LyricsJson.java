package com.persiki84.battlecraft.client.lyrics;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;

// WHY: поля ответов приходят от чужих серверов и бывают null, числом вместо строки или огромными:
// WHY: чужой тип пропускается, длинная строка считается пустой
final class LyricsJson {
    private LyricsJson() {}

    static String text(JsonReader reader, int limit) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            reader.skipValue();
            return "";
        }
        String value = reader.nextString();
        return value.length() > limit ? "" : value;
    }

    static double number(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) {
            reader.skipValue();
            return -1.0;
        }
        return reader.nextDouble();
    }

    static boolean flag(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BOOLEAN) {
            reader.skipValue();
            return false;
        }
        return reader.nextBoolean();
    }
}
