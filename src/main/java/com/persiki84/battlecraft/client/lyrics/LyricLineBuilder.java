package com.persiki84.battlecraft.client.lyrics;

import java.util.ArrayList;
import java.util.List;

// WHY: куски строки (слова с метками времени) чистятся по отдельности, а пробел между ними ставится,
// WHY: только если он был в исходнике, чтобы слово не склеилось и не разорвалось. Индекс первой
// WHY: буквы слова считается в кодовых точках, как раскладывает строку остров
final class LyricLineBuilder {
    private final StringBuilder text = new StringBuilder();
    private final List<long[]> marks = new ArrayList<>();
    private boolean spaced;

    void append(String piece, long startMs, long endMs) {
        String clean = LyricText.clean(piece, LrcParser.TEXT_LIMIT);
        boolean trailing = !piece.isEmpty() && Character.isWhitespace(piece.codePointBefore(piece.length()));
        if (clean.isEmpty()) {
            spaced = spaced || piece.codePoints().anyMatch(Character::isWhitespace);
            return;
        }
        int used = text.codePointCount(0, text.length());
        if (used < LrcParser.TEXT_LIMIT) place(clean, startMs, endMs, used, Character.isWhitespace(piece.codePointAt(0)));
        spaced = trailing;
    }

    private void place(String clean, long startMs, long endMs, int used, boolean leading) {
        int at = used;
        if (at > 0 && (spaced || leading)) {
            text.append(' ');
            at++;
        }
        int room = Math.min(clean.codePointCount(0, clean.length()), LrcParser.TEXT_LIMIT - at);
        if (room <= 0) return;

        String fitted = clean.substring(0, clean.offsetByCodePoints(0, room));
        marks.add(new long[] {startMs, endMs, at, room});
        text.append(fitted);
    }

    LyricLine build(long start, long end, boolean worded) {
        String finished = text.toString();
        if (!worded || marks.size() < 2) return new LyricLine(start, end, finished, List.of());

        List<LyricWord> words = new ArrayList<>(marks.size());
        for (int index = 0; index < marks.size(); index++) words.add(word(index, start, end));
        return new LyricLine(start, end, finished, words);
    }

    private LyricWord word(int index, long start, long end) {
        long[] mark = marks.get(index);
        long wordStart = Math.max(start, Math.min(end, mark[0]));
        long nextStart = index + 1 < marks.size() ? marks.get(index + 1)[0] : end;
        long wordEnd = mark[1] > 0L ? mark[1] : nextStart;
        return new LyricWord(wordStart, Math.max(wordStart + 1L, Math.min(end, wordEnd)), (int) mark[2], (int) mark[3]);
    }
}
