package com.persiki84.battlecraft.client.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// WHY: формат yrc NetEase: строка «[начало,длительность]», дальше слова «(начало,длительность,0)текст».
// WHY: У каждого слова своя длительность, поэтому затянутое слово держит волну на себе, а строчный
// WHY: LRC этого не знает. Строки-объекты {...} и строки «作词: ...» это титры авторов, а не текст
public final class YrcParser {
    private static final int LINE_LIMIT = 2000;
    private static final int WORDS_PER_LINE = 256;
    private static final Pattern LINE = Pattern.compile("^\\[(\\d{1,8}),(\\d{1,8})](.*)$");
    private static final Pattern WORD = Pattern.compile("\\((\\d{1,8}),(\\d{1,8}),-?\\d{1,8}\\)");
    private static final Pattern CREDIT = Pattern.compile(
            "^\\s*(作词|作曲|编曲|制作人?|混音|母带|和声|监制|录音|吉他|贝斯|鼓|弦乐|Lyrics( by)?|Composer|Composed by|"
                    + "Written by|Producer|Produced by|Arranged by)\\s*[:：]",
            Pattern.CASE_INSENSITIVE);

    private YrcParser() {}

    public static Lyrics parse(String source, long durationMs) {
        if (source == null || source.isBlank()) return Lyrics.NONE;

        List<LyricLine> lines = new ArrayList<>();
        for (String raw : source.split("\\r?\\n")) {
            if (lines.size() >= LINE_LIMIT) break;
            Matcher line = LINE.matcher(raw.strip());
            if (!line.matches()) continue;
            LyricLine parsed = line(Long.parseLong(line.group(1)), Long.parseLong(line.group(2)), line.group(3));
            if (!parsed.text().isEmpty() && !CREDIT.matcher(parsed.text()).find()) lines.add(parsed);
        }
        List<LyricLine> ordered = ordered(lines);
        return LrcParser.sane(ordered, durationMs) ? new Lyrics(ordered) : Lyrics.NONE;
    }

    private static LyricLine line(long start, long duration, String body) {
        Matcher word = WORD.matcher(body);
        LyricLineBuilder builder = new LyricLineBuilder();
        long wordStart = start;
        long wordEnd = 0L;
        int cursor = 0;
        int count = 0;
        while (count < WORDS_PER_LINE && word.find()) {
            if (count > 0) builder.append(body.substring(cursor, word.start()), wordStart, wordEnd);
            wordStart = Long.parseLong(word.group(1));
            wordEnd = wordStart + Long.parseLong(word.group(2));
            cursor = word.end();
            count++;
        }
        builder.append(body.substring(cursor), wordStart, wordEnd);
        return builder.build(start, start + Math.max(1L, duration), count > 1);
    }

    // WHY: строки в yrc иногда идут не по порядку и заходят друг на друга: порядок по началу, а конец
    // WHY: строки не позже начала следующей, иначе поиск строки по времени держал бы уже спетую
    private static List<LyricLine> ordered(List<LyricLine> lines) {
        lines.sort((left, right) -> Long.compare(left.startMs(), right.startMs()));
        List<LyricLine> ordered = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            LyricLine line = lines.get(index);
            long next = index + 1 < lines.size() ? lines.get(index + 1).startMs() : Long.MAX_VALUE;
            long end = Math.max(line.startMs() + 1L, Math.min(line.endMs(), next));
            ordered.add(end == line.endMs() ? line : new LyricLine(line.startMs(), end, line.text(), clipped(line, end)));
        }
        return ordered;
    }

    private static List<LyricWord> clipped(LyricLine line, long end) {
        List<LyricWord> words = new ArrayList<>(line.words().size());
        for (LyricWord word : line.words()) {
            long start = Math.min(word.startMs(), end - 1L);
            words.add(new LyricWord(start, Math.max(start + 1L, Math.min(word.endMs(), end)), word.firstGlyph(), word.glyphCount()));
        }
        return words;
    }
}
