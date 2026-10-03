package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.lyrics.LyricLine;
import com.persiki84.battlecraft.client.lyrics.LyricWord;
import com.persiki84.shared.client.ui.UiSweep;

import java.util.List;

// WHY: каждой букве строки достаётся своё окно времени: по меткам слов, если они есть, иначе строка
// WHY: делится поровну. Мягкий край волны тянется на SOFT_GLYPHS букв и потому зависит от темпа: в
// WHY: быстрой строке он короткий и не мылит, в медленной длинный и не дёргается
final class LineSweep implements UiSweep {
    private static final float SOFT_GLYPHS = 1.5f;
    private static final long SOFT_MIN_MS = 60L;
    private static final long SOFT_MAX_MS = 700L;

    private long[] starts = new long[0];
    private long[] ramps = new long[0];
    private long at;

    void load(LyricLine line) {
        int count = line.text().codePointCount(0, line.text().length());
        starts = new long[count];
        ramps = new long[count];
        if (line.words().isEmpty()) {
            spread(0, count, line.startMs(), line.endMs());
            return;
        }
        List<LyricWord> words = line.words();
        spread(0, words.get(0).firstGlyph(), line.startMs(), words.get(0).startMs());
        for (int index = 0; index < words.size(); index++) {
            LyricWord word = words.get(index);
            int next = index + 1 < words.size() ? words.get(index + 1).firstGlyph() : count;
            long end = index + 1 < words.size() ? words.get(index + 1).startMs() : line.endMs();
            spread(word.firstGlyph(), next, word.startMs(), end);
        }
    }

    private void spread(int from, int to, long startMs, long endMs) {
        int count = Math.max(0, Math.min(to, starts.length) - from);
        if (count == 0) return;

        double step = Math.max(1L, endMs - startMs) / (double) count;
        long ramp = Math.max(SOFT_MIN_MS, Math.min(SOFT_MAX_MS, Math.round(step * SOFT_GLYPHS)));
        for (int index = 0; index < count; index++) {
            starts[from + index] = startMs + Math.round(step * index);
            ramps[from + index] = ramp;
        }
    }

    void time(long positionMs) {
        at = positionMs;
    }

    @Override
    public float lit(int index) {
        if (index < 0 || index >= starts.length) return 1.0f;
        return Math.max(0.0f, Math.min(1.0f, (at - starts[index]) / (float) ramps[index]));
    }
}
