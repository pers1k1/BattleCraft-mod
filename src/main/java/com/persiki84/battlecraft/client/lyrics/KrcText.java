package com.persiki84.battlecraft.client.lyrics;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

// WHY: KRC Kugou приходит base64: заголовок «krc1», дальше байты, сложенные XOR с постоянным ключом
// WHY: из 16 байт, и zlib. Разжатый текст это «[начало,длительность]», слова «<смещение,длительность,0>»
// WHY: со смещением от начала строки. Он переводится в yrc NetEase с абсолютным временем слова и
// WHY: идёт через YrcParser: кеш, титры и проверка разумности у источников общие
final class KrcText {
    private static final byte[] HEAD = "krc1".getBytes(StandardCharsets.US_ASCII);
    private static final int[] KEY = {64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105};
    private static final int TEXT_LIMIT = 1024 * 1024;
    private static final Pattern LINE = Pattern.compile("^\\[(\\d{1,8}),(\\d{1,8})](.*)$");
    private static final Pattern WORD = Pattern.compile("<(\\d{1,8}),(\\d{1,8}),-?\\d{1,8}>");

    private KrcText() {}

    static String decode(String content) throws DataFormatException {
        byte[] raw = Base64.getDecoder().decode(content);
        if (raw.length <= HEAD.length || !startsWithHead(raw)) throw new DataFormatException("not krc1");
        byte[] packed = new byte[raw.length - HEAD.length];
        for (int index = 0; index < packed.length; index++) {
            packed[index] = (byte) (raw[index + HEAD.length] ^ KEY[index % KEY.length]);
        }
        return inflate(packed);
    }

    private static boolean startsWithHead(byte[] raw) {
        for (int index = 0; index < HEAD.length; index++) {
            if (raw[index] != HEAD[index]) return false;
        }
        return true;
    }

    // WHY: разжатый текст ограничен мегабайтом: ответ сервера чужой, и сжатая бомба не должна
    // WHY: съесть память игры
    private static String inflate(byte[] packed) throws DataFormatException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                out.write(buffer, 0, read);
                if (out.size() > TEXT_LIMIT) throw new DataFormatException("krc larger than " + TEXT_LIMIT);
            }
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            inflater.end();
        }
    }

    // WHY: первая строка KRC повторяет «название - исполнитель», это шапка, а не текст песни
    static String toYrc(String krc, String heading) {
        StringBuilder yrc = new StringBuilder();
        for (String raw : krc.split("\\r?\\n")) {
            Matcher line = LINE.matcher(raw.strip());
            if (!line.matches()) continue;
            long start = Long.parseLong(line.group(1));
            String body = line.group(3);
            if (plain(body).equalsIgnoreCase(heading)) continue;
            yrc.append('[').append(start).append(',').append(line.group(2)).append(']')
                    .append(absolute(body, start)).append('\n');
        }
        return yrc.toString();
    }

    private static String absolute(String body, long lineStart) {
        Matcher word = WORD.matcher(body);
        StringBuilder out = new StringBuilder();
        while (word.find()) {
            long at = lineStart + Long.parseLong(word.group(1));
            word.appendReplacement(out, Matcher.quoteReplacement("(" + at + "," + word.group(2) + ",0)"));
        }
        word.appendTail(out);
        return out.toString();
    }

    private static String plain(String body) {
        return WORD.matcher(body).replaceAll("").strip();
    }
}
