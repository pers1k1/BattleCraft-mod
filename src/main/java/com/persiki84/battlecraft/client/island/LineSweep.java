package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.lyrics.LyricLine;
import com.persiki84.battlecraft.client.lyrics.LyricWord;
import com.persiki84.shared.client.ui.UiSweep;

import java.util.List;

// WHY: каждой букве строки достаётся своё окно времени: по меткам слов, если они есть, иначе по
// WHY: весу символа. Пробел и знак препинания не поются, и равная доля для них уводила волну вперёд
// WHY: голоса. Мягкий край тянется на SOFT_GLYPHS букв и потому идёт в темпе строки
final class LineSweep implements UiSweep {
    private static final float SOFT_GLYPHS = 1.5f;
    private static final long SOFT_MIN_MS = 60L;
    private static final long SOFT_MAX_MS = 700L;
    private static final float SPACE_WEIGHT = 0.3f;
    private static final float MARK_WEIGHT = 0.25f;
    private static final int ACCENT_COLUMN = 2;
    private static final float ACCENT_ROW = 0.85f;

    private long[] starts = new long[0];
    private long[] ramps = new long[0];
    private long at;

    void load(LyricLine line) {
        int[] points = line.text().codePoints().toArray();
        starts = new long[points.length];
        ramps = new long[points.length];
        if (line.words().isEmpty()) {
            spread(points, 0, points.length, line.startMs(), line.endMs());
            return;
        }
        List<LyricWord> words = line.words();
        spread(points, 0, words.get(0).firstGlyph(), line.startMs(), words.get(0).startMs());
        for (int index = 0; index < words.size(); index++) {
            LyricWord word = words.get(index);
            int next = index + 1 < words.size() ? words.get(index + 1).firstGlyph() : points.length;
            long end = index + 1 < words.size() ? words.get(index + 1).startMs() : line.endMs();
            spread(points, word.firstGlyph(), next, word.startMs(), end);
        }
    }

    private void spread(int[] points, int from, int to, long startMs, long endMs) {
        int last = Math.min(to, starts.length);
        float total = 0.0f;
        for (int index = from; index < last; index++) total += weight(points[index]);
        if (last <= from || total <= 0.0f) return;

        double perWeight = Math.max(1L, endMs - startMs) / (double) total;
        double cursor = startMs;
        long ramp = SOFT_MIN_MS;
        for (int index = from; index < last; index++) {
            double length = perWeight * weight(points[index]);
            starts[index] = Math.round(cursor);
            if (length > 0.0) ramp = Math.max(SOFT_MIN_MS, Math.min(SOFT_MAX_MS, Math.round(length * SOFT_GLYPHS)));
            ramps[index] = ramp;
            cursor += length;
        }
    }

    private static float weight(int point) {
        if (Character.isWhitespace(point)) return SPACE_WEIGHT;
        int type = Character.getType(point);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) return 0.0f;
        return Character.isLetterOrDigit(point) ? 1.0f : MARK_WEIGHT;
    }

    void time(long positionMs) {
        at = positionMs;
    }

    @Override
    public float lit(int index) {
        if (index < 0 || index >= starts.length) return 1.0f;
        return Math.max(0.0f, Math.min(1.0f, (at - starts[index]) / (float) ramps[index]));
    }

    // WHY: место голоса это дробный индекс буквы между началами соседних окон: оно течёт ровно и не
    // WHY: прыгает, когда очередная буква догорает
    @Override
    public float head() {
        int count = starts.length;
        if (count == 0) return -1.0f;
        if (at <= starts[0]) return 0.0f;

        for (int index = 0; index < count; index++) {
            long next = index + 1 < count ? starts[index + 1] : starts[index] + ramps[index];
            if (at < next) return index + (at - starts[index]) / (float) Math.max(1L, next - starts[index]);
        }
        return count;
    }

    @Override
    public int accent(int base) {
        return IslandTone.barAt(ACCENT_COLUMN, ACCENT_ROW);
    }
}
