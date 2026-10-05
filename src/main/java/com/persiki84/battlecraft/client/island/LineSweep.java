package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.lyrics.LyricLine;
import com.persiki84.battlecraft.client.lyrics.LyricWord;
import com.persiki84.shared.client.ui.UiSweep;

import java.util.Arrays;
import java.util.List;

// WHY: каждой букве строки достаётся своё окно времени: по меткам слов, если они есть (слово идёт
// WHY: ровно свою длительность, и на затянутом слове волна ждёт), иначе по весу символа. Пробел и знак препинания не поются, и равная доля для них уводила волну вперёд
// WHY: голоса. Мягкий край тянется на SOFT_GLYPHS букв и потому идёт в темпе строки
final class LineSweep implements UiSweep {
    private static final float SOFT_GLYPHS = 1.5f;
    private static final long SOFT_MIN_MS = 110L;
    private static final long RAMP_FLOOR_MS = 40L;
    private static final long SOFT_MAX_MS = 700L;
    private static final float SPACE_WEIGHT = 0.3f;
    private static final float MARK_WEIGHT = 0.25f;
    private static final int ACCENT_COLUMN = 2;
    private static final float ACCENT_ROW = 0.85f;
    private static final long HELD_MIN_MS = 1000L;
    private static final int HELD_MAX_GLYPHS = 12;
    private static final float HELD_RISE_SHARE = 0.15f;
    private static final float HELD_SUSTAIN_SHARE = 0.6f;
    private static final long NOT_HELD = Long.MIN_VALUE;
    private static final float GROUP_SOFT = 0.5f;
    private static final long RISE_MS = 170L;
    private static final long SETTLE_MS = 420L;
    private static final long WAVE_MS = 70L;
    private static final float WAVE_SHARE = 0.25f;
    private static final long FADE_MS = 380L;

    private long[] starts = new long[0];
    private long[] ramps = new long[0];
    private long[] heldUntil = new long[0];
    private long[] ends = new long[0];
    private int[] group = new int[0];
    private long[] groupStart = new long[0];
    private long[] groupEnd = new long[0];
    private long[] wave = new long[0];
    private long at;

    void load(LyricLine line, long finishBy) {
        int[] points = line.text().codePoints().toArray();
        starts = new long[points.length];
        ramps = new long[points.length];
        heldUntil = new long[points.length];
        ends = new long[points.length];
        group = new int[points.length];
        Arrays.fill(heldUntil, NOT_HELD);
        Arrays.fill(group, Syllables.NONE);
        if (line.words().isEmpty()) {
            spread(points, 0, points.length, line.startMs(), line.endMs());
            Syllables.mark(points, 0, points.length, group, 0);
        } else {
            spreadWords(points, line);
        }
        gather(points.length);
        fit(line.startMs(), finishBy);
    }

    // WHY: окно слога это от начала его первой буквы до конца последней; мягкий край каждой буквы
    // WHY: растягивается на половину слога, поэтому горит сразу несколько букв, как градиент Spicy
    private void gather(int count) {
        groupStart = new long[count];
        groupEnd = new long[count];
        wave = new long[count];
        int from = 0;
        while (from < count) {
            int to = from + 1;
            while (to < count && group[from] != Syllables.NONE && group[to] == group[from]) to++;
            if (group[from] != Syllables.NONE) widen(from, to);
            from = to;
        }
    }

    private void widen(int from, int to) {
        long start = starts[from];
        long end = Math.max(start + 1L, ends[to - 1]);
        long soft = Math.min(SOFT_MAX_MS, Math.round((end - start) * GROUP_SOFT));
        long spread = Math.min(WAVE_MS, Math.round((end - start) * WAVE_SHARE));
        for (int index = from; index < to; index++) {
            groupStart[index] = start;
            groupEnd[index] = end;
            ramps[index] = Math.max(ramps[index], soft);
            wave[index] = to - from > 1 ? spread * (index - from) / (to - from - 1) : 0L;
        }
    }

    private void spreadWords(int[] points, LyricLine line) {
        List<LyricWord> words = line.words();
        spread(points, 0, words.get(0).firstGlyph(), line.startMs(), words.get(0).startMs());
        for (int index = 0; index < words.size(); index++) {
            LyricWord word = words.get(index);
            int next = index + 1 < words.size() ? words.get(index + 1).firstGlyph() : points.length;
            spread(points, word.firstGlyph(), next, word.startMs(), word.endMs());
            Syllables.mark(points, word.firstGlyph(), Math.min(next, points.length), group, word.firstGlyph());
            markHeld(points, word, next);
        }
    }

    // WHY: затянутое слово выделяется, как в Beautiful Lyrics: слово не короче секунды и не длиннее
    // WHY: HELD_MAX_GLYPHS букв горит каждой буквой от её начала до конца слова
    private void markHeld(int[] points, LyricWord word, int next) {
        if (word.endMs() - word.startMs() < HELD_MIN_MS || word.glyphCount() > HELD_MAX_GLYPHS) return;

        int last = Math.min(next, heldUntil.length);
        for (int index = Math.max(0, word.firstGlyph()); index < last; index++) {
            if (Character.isLetterOrDigit(points[index])) heldUntil[index] = word.endMs();
        }
    }

    // WHY: строка обязана догореть до своей смены: следующая выбирается с упреждением, и в yrc строки
    // WHY: идут почти встык, поэтому последние буквы уходили недогоревшими, синими и застывшими посреди
    // WHY: подъёма. Расписание строки сжимается целиком, пропорции слов и затянутые слова сохраняются
    private void fit(long lineStart, long finishBy) {
        long latest = lineStart;
        for (int index = 0; index < starts.length; index++) {
            latest = Math.max(latest, Math.max(starts[index] + ramps[index], heldUntil[index]));
        }
        if (latest <= finishBy || latest <= lineStart) return;

        double factor = Math.max(0.05, (finishBy - lineStart) / (double) (latest - lineStart));
        for (int index = 0; index < starts.length; index++) {
            ramps[index] = Math.max(RAMP_FLOOR_MS, Math.round(ramps[index] * factor));
            long scaled = scaled(starts[index], lineStart, factor);
            starts[index] = Math.max(lineStart, Math.min(scaled, finishBy - ramps[index]));
            groupStart[index] = scaled(groupStart[index], lineStart, factor);
            groupEnd[index] = Math.min(finishBy, scaled(groupEnd[index], lineStart, factor));
            wave[index] = Math.round(wave[index] * factor);
            if (heldUntil[index] != NOT_HELD) heldUntil[index] = Math.min(finishBy, scaled(heldUntil[index], lineStart, factor));
        }
    }

    private static long scaled(long time, long lineStart, double factor) {
        return lineStart + Math.round((time - lineStart) * factor);
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
            ends[index] = Math.round(cursor);
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

    // WHY: слог поднимается целиком, мягко и с лёгкой волной по буквам, стоит приподнятым, пока
    // WHY: поётся, и оседает после: колокол на каждую букву в своём коротком окне выходил дёрганым
    @Override
    public float motion(int index) {
        if (index < 0 || index >= group.length || group[index] == Syllables.NONE) return 0.0f;

        long begin = groupStart[index] + wave[index];
        long rise = Math.max(1L, Math.min(RISE_MS, (groupEnd[index] - groupStart[index]) * 3L / 5L));
        float up = smooth((at - begin) / (float) rise);
        float down = 1.0f - smooth((at - groupEnd[index] - wave[index]) / (float) SETTLE_MS);
        return up * down;
    }

    // WHY: слог держит цвет обложки, пока поётся, и тает FADE_MS после своего конца, как в Spicy Lyrics:
    // WHY: колокол по доле буквы гас раньше конца слога, и на смене слова подсветка пропадала разом
    @Override
    public float glow(int index) {
        if (index < 0 || index >= starts.length) return 0.0f;

        long litAt = starts[index] + ramps[index];
        long end = group[index] == Syllables.NONE ? litAt : Math.max(groupEnd[index], litAt);
        float rise = smooth((at - starts[index]) / (float) ramps[index]);
        return rise * (1.0f - smooth((at - end) / (float) FADE_MS));
    }

    @Override
    public int accent(int base) {
        return IslandTone.barAt(ACCENT_COLUMN, ACCENT_ROW);
    }

    @Override
    public float held(int index) {
        if (index < 0 || index >= heldUntil.length || heldUntil[index] == NOT_HELD) return 0.0f;

        long window = heldUntil[index] - starts[index];
        return window <= 0L ? 0.0f : envelope((at - starts[index]) / (float) window);
    }

    // WHY: свечение затянутой буквы идёт по кривой GlowRange из Beautiful Lyrics: разгорается за первые
    // WHY: 15% своего окна, держится до 60% и гаснет к концу слова, поэтому на смене строки его уже нет
    private static float envelope(float progress) {
        if (progress <= 0.0f || progress >= 1.0f) return 0.0f;
        if (progress < HELD_RISE_SHARE) return smooth(progress / HELD_RISE_SHARE);
        if (progress < HELD_SUSTAIN_SHARE) return 1.0f;
        return smooth((1.0f - progress) / (1.0f - HELD_SUSTAIN_SHARE));
    }

    private static float smooth(float share) {
        float clamped = Math.max(0.0f, Math.min(1.0f, share));
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }
}
