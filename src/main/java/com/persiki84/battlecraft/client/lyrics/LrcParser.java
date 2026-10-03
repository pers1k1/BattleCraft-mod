package com.persiki84.battlecraft.client.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    static final int TEXT_LIMIT = 200;
    private static final int LINE_LIMIT = 2000;
    private static final int MIN_LINES = 4;
    private static final long END_SLACK_MS = 5000L;
    private static final long GLYPH_MS = 110L;
    private static final long LINE_MIN_MS = 1200L;
    private static final long LINE_MAX_MS = 9000L;
    private static final long OFFSET_LIMIT_MS = 600_000L;
    private static final int TAGS_PER_LINE = 64;
    private static final Pattern TAG = Pattern.compile("^\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d{1,7})\\s*]",
            Pattern.CASE_INSENSITIVE);

    private LrcParser() {}

    private record Stamped(long startMs, String body) {}

    public static Lyrics parse(String source, long durationMs) {
        if (source == null || source.isBlank()) return Lyrics.NONE;

        List<Stamped> stamped = new ArrayList<>();
        long offset = 0L;
        for (String raw : source.split("\\r?\\n")) {
            if (stamped.size() >= LINE_LIMIT) break;
            Matcher shift = OFFSET.matcher(raw.strip());
            if (shift.find()) offset = clampOffset(Long.parseLong(shift.group(1)));
            else read(raw, stamped);
        }
        return assemble(stamped, offset, durationMs);
    }

    private static long clampOffset(long value) {
        return Math.max(-OFFSET_LIMIT_MS, Math.min(OFFSET_LIMIT_MS, value));
    }

    private static void read(String raw, List<Stamped> into) {
        String rest = raw.strip();
        List<Long> times = new ArrayList<>();
        Matcher tag = TAG.matcher(rest);
        while (times.size() < TAGS_PER_LINE && tag.find()) {
            long time = millis(tag);
            if (time >= 0L) times.add(time);
            rest = rest.substring(tag.end()).strip();
            tag = TAG.matcher(rest);
        }
        for (long time : times) into.add(new Stamped(time, rest));
    }

    private static long millis(Matcher tag) {
        long seconds = Long.parseLong(tag.group(2));
        if (seconds > 59L) return -1L;

        String fraction = tag.group(3);
        long part = 0L;
        if (fraction != null) part = Long.parseLong(fraction) * (long) Math.pow(10, 3 - fraction.length());
        return Long.parseLong(tag.group(1)) * 60_000L + seconds * 1000L + part;
    }

    // WHY: смещение [offset:] по формату LRC положительное, когда текст должен идти раньше
    private static Lyrics assemble(List<Stamped> stamped, long offset, long durationMs) {
        List<Stamped> sorted = new ArrayList<>(stamped.size());
        for (Stamped entry : stamped) sorted.add(new Stamped(Math.max(0L, entry.startMs() - offset), entry.body()));
        sorted.sort(Comparator.comparingLong(Stamped::startMs));
        List<LyricLine> lines = new ArrayList<>();
        long previousStart = -1L;
        boolean previousSpoken = false;
        for (int index = 0; index < sorted.size(); index++) {
            Stamped entry = sorted.get(index);
            if (entry.startMs() == previousStart && previousSpoken) continue;
            previousStart = entry.startMs();
            previousSpoken = !entry.body().isBlank();
            long end = endOf(sorted, index, durationMs);
            LyricLine line = line(entry.startMs(), end, entry.body(), offset);
            if (!line.text().isEmpty()) lines.add(line);
        }
        return sane(lines, durationMs) ? new Lyrics(lines) : Lyrics.NONE;
    }

    private static long endOf(List<Stamped> sorted, int index, long durationMs) {
        long start = sorted.get(index).startMs();
        String spoken = WORD.matcher(sorted.get(index).body()).replaceAll("");
        long natural = start + Math.max(LINE_MIN_MS, Math.min(LINE_MAX_MS,
                spoken.codePointCount(0, spoken.length()) * GLYPH_MS));
        for (int next = index + 1; next < sorted.size(); next++) {
            if (sorted.get(next).startMs() > start) return Math.min(natural, sorted.get(next).startMs());
        }
        return durationMs > start ? Math.min(natural, durationMs) : natural;
    }

    private static boolean sane(List<LyricLine> lines, long durationMs) {
        if (lines.size() < MIN_LINES) return false;
        long last = lines.get(lines.size() - 1).startMs();
        return durationMs <= 0L || last <= durationMs + END_SLACK_MS;
    }

    // WHY: в расширенном LRC метка <mm:ss.xx> стоит перед своим словом. Куски чистятся по отдельности,
    // WHY: а пробел между ними ставится, только если он был в исходнике, чтобы слово не склеилось
    private static LyricLine line(long start, long end, String body, long offset) {
        Matcher word = WORD.matcher(body);
        List<long[]> marks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int cursor = 0;
        long wordStart = start;
        boolean spaced = false;
        while (word.find()) {
            spaced = append(text, marks, body.substring(cursor, word.start()), wordStart, spaced);
            long stamp = millis(word);
            wordStart = stamp >= 0L ? Math.max(0L, stamp - offset) : wordStart;
            cursor = word.end();
        }
        append(text, marks, body.substring(cursor), wordStart, spaced);
        String finished = text.toString();
        return new LyricLine(start, end, finished, words(marks, finished, start, end));
    }

    private static boolean append(StringBuilder text, List<long[]> marks, String piece, long startMs,
                                  boolean spaced) {
        String clean = LyricText.clean(piece, TEXT_LIMIT);
        int used = text.codePointCount(0, text.length());
        boolean trailing = !piece.isEmpty() && Character.isWhitespace(piece.codePointBefore(piece.length()));
        if (clean.isEmpty()) return spaced || piece.codePoints().anyMatch(Character::isWhitespace);
        if (used >= TEXT_LIMIT) return trailing;

        boolean gap = used > 0 && (spaced || Character.isWhitespace(piece.codePointAt(0)));
        if (gap) {
            text.append(' ');
            used++;
        }
        String fitted = clean.substring(0, clean.offsetByCodePoints(0,
                Math.min(clean.codePointCount(0, clean.length()), TEXT_LIMIT - used)));
        marks.add(new long[] {startMs, used, fitted.codePointCount(0, fitted.length())});
        text.append(fitted);
        return trailing;
    }

    private static List<LyricWord> words(List<long[]> marks, String text, long start, long end) {
        if (marks.size() < 2) return List.of();

        List<LyricWord> words = new ArrayList<>(marks.size());
        for (long[] mark : marks) {
            long clamped = Math.max(start, Math.min(end, mark[0]));
            words.add(new LyricWord(clamped, (int) mark[1], (int) mark[2]));
        }
        return words;
    }
}
