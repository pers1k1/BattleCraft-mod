package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: строка меняется по буквам, как числа и подписи в iOS: буквы, что стоят на своих местах в обеих
// WHY: строках, не трогаются, общий хвост перетекает на новое место, а остальные сменяются волной слева
// WHY: направо - старая уходит вверх и гаснет, новая приходит снизу. Перо рисует букву целым глифом,
// WHY: и размыть отдельную букву ему нечем, поэтому смена идёт прозрачностью и коротким ходом. Строка
// WHY: и в покое, и на смене рисуется одним пером с табличными цифрами, иначе вздрагивала бы на смене
public final class UiMorphText {
    private static final float GLYPH_SECONDS = 0.3f;
    private static final float STAGGER_SECONDS = 0.02f;
    private static final float STAGGER_LIMIT = 0.3f;
    private static final float TRAVEL_UNITS = 2.0f;
    private static final float IDLE = Float.MAX_VALUE;
    private static final float SAME_PLACE = 0.01f;

    private final Side arriving = new Side(1.0f);
    private final Side leavingSide = new Side(-1.0f);
    private final Side steady = new Side(0.0f);
    private Component value = Component.empty();
    private Component previous = Component.empty();
    private String raw = "";
    private int[] codes = new int[0];
    private int[] previousCodes = new int[0];
    private float[] stops = new float[0];
    private float[] previousStops = new float[0];
    private static final float UNSUNG = 0.42f;
    private static final float LIFT_UNITS = 0.55f;
    private static final float FOLLOW_SHARE = 0.45f;

    private float clock = IDLE;
    private float tempo = 1.0f;
    private float shown;
    private float leaving;
    private float scale = 1.0f;
    private UiSweep sweep = UiSweep.NONE;
    private int prefix;
    private int suffix;

    public void set(String next) {
        set(next, 0.0f);
    }

    // WHY: строки лирики идут в темпе песни: в быстром речитативе следующая строка приходит через
    // WHY: секунду, и полный морф съедал бы её половину. Смена укладывается в seconds, если оно
    // WHY: задано, иначе идёт своим обычным ходом
    public void set(String next, float seconds) {
        if (raw.equals(next)) return;

        previous = value;
        previousCodes = codes;
        raw = next;
        value = Component.literal(next);
        codes = next.codePoints().toArray();
        prefix = commonPrefix(previousCodes, codes);
        suffix = commonSuffix(previousCodes, codes, prefix);
        clock = previousCodes.length == 0 ? IDLE : 0.0f;
        leaving = shown;
        shown = 0.0f;
        tempo = seconds > 0.0f ? Math.max(0.05f, seconds / duration()) : 1.0f;
    }

    public void snap(String next) {
        set(next);
        clock = IDLE;
    }

    public void advance(float delta) {
        if (clock != IDLE) clock += delta / tempo;
    }

    public boolean morphing() {
        return clock < duration();
    }

    public String raw() {
        return raw;
    }

    private int changedSpan() {
        return Math.max(1, codes.length - prefix - suffix);
    }

    private float duration() {
        return GLYPH_SECONDS + Math.min(STAGGER_LIMIT, STAGGER_SECONDS * (changedSpan() - 1));
    }

    public float measure(GuiGraphics graphics, Font font, float textScale) {
        return UiRender.measureToned(graphics, font, value, textScale);
    }

    public void draw(GuiGraphics graphics, Font font, float x, float y, float textScale, int color) {
        draw(graphics, font, x, y, textScale, color, UiSweep.NONE);
    }

    // WHY: подсветка лирики относится к строке в покое и к приходящей: уходящая уже пропета и уходит
    // WHY: полным цветом
    public void draw(GuiGraphics graphics, Font font, float x, float y, float textScale, int color, UiSweep lit) {
        scale = textScale;
        sweep = lit;
        if (!morphing()) {
            UiRender.labelToned(graphics, font, value, x, y, textScale, color, steady);
            return;
        }
        stops = UiRender.stopsToned(graphics, font, value, textScale);
        previousStops = UiRender.stopsToned(graphics, font, previous, textScale);
        UiRender.labelToned(graphics, font, previous, x - leaving, y, textScale, color, this.leavingSide);
        UiRender.labelToned(graphics, font, value, x, y, textScale, color, arriving);
    }

    // WHY: строка лирики длиннее слота едет за волной подсветки, а не по своим часам: пропеваемая
    // WHY: буква держится на FOLLOW_SHARE слота
    // WHY: уходящая строка уезжает с того места, где стояла: прокрученная бегущей строкой длинная
    // WHY: строка иначе в первый кадр смены отскакивала к своему началу
    public void scrolled(float shift) {
        shown = shift;
    }

    public float follow(GuiGraphics graphics, Font font, float textScale, float slot, UiSweep lit) {
        float[] marks = UiRender.stopsToned(graphics, font, value, textScale);
        float span = marks[marks.length - 1];
        for (int index = 0; index + 1 < marks.length; index++) {
            float share = lit.lit(index);
            if (share >= 1.0f) continue;
            float edge = marks[index] + (marks[index + 1] - marks[index]) * share;
            return Math.max(0.0f, Math.min(span - slot, edge - slot * FOLLOW_SHARE));
        }
        return Math.max(0.0f, span - slot);
    }

    public void drawRight(GuiGraphics graphics, Font font, float rightX, float y, float textScale, int color) {
        draw(graphics, font, rightX - measure(graphics, font, textScale), y, textScale, color);
    }

    private boolean holds(int index) {
        return leaving == 0.0f && index < codes.length && index < previousCodes.length && codes[index] == previousCodes[index]
                && index < stops.length && index < previousStops.length
                && Math.abs(stops[index] - previousStops[index]) < SAME_PLACE;
    }

    private float share(int index) {
        float delay = Math.min(STAGGER_LIMIT, Math.min(index - prefix, changedSpan() - 1) * STAGGER_SECONDS);
        return UiAnim.smoothstep(0.0f, 1.0f, (clock - delay) / GLYPH_SECONDS);
    }

    private float slide() {
        return UiAnim.smoothstep(0.0f, duration(), clock);
    }

    private static int commonPrefix(int[] was, int[] now) {
        int length = 0;
        while (length < was.length && length < now.length && was[length] == now[length]) length++;
        return length;
    }

    private static int commonSuffix(int[] was, int[] now, int prefix) {
        int length = 0;
        while (length < was.length - prefix && length < now.length - prefix
                && was[was.length - 1 - length] == now[now.length - 1 - length]) {
            length++;
        }
        return length;
    }

    // WHY: направление +1 это приходящая строка (снизу вверх), -1 уходящая (вверх), 0 строка в покое.
    // WHY: Буквы на своих местах и общее начало рисует только приходящая строка, общий хвост
    // WHY: перетекает долей хода между двумя местами
    private final class Side implements UiRender.GlyphTone {
        private final float direction;

        private Side(float direction) {
            this.direction = direction;
        }

        @Override
        public int tint(int index, int count, int base) {
            float shown = shown(index, count);
            if (direction < 0.0f) return UiTheme.alpha(base, shown);
            return UiTheme.alpha(base, shown * (UNSUNG + (1.0f - UNSUNG) * clamp(sweep.lit(index))));
        }

        private float shown(int index, int count) {
            if (direction == 0.0f) return 1.0f;
            if (index < prefix || holds(index)) return direction > 0.0f ? 1.0f : 0.0f;
            if (index >= count - suffix) return direction > 0.0f ? slide() : 1.0f - slide();

            float share = share(index);
            return direction > 0.0f ? share : 1.0f - share;
        }

        @Override
        public float rise(int index, int count) {
            float lift = direction < 0.0f ? 0.0f : lift(sweep.lit(index));
            if (direction == 0.0f || index < prefix || index >= count - suffix || holds(index)) return lift;

            float share = share(index);
            float travel = TRAVEL_UNITS * scale;
            return direction > 0.0f ? lift - (1.0f - share) * travel : share * travel;
        }

        // WHY: буква, которая поётся сейчас, чуть приподнимается и опускается на место: колокол по её доле
        private float lift(float lit) {
            float share = clamp(lit);
            return -LIFT_UNITS * scale * 4.0f * share * (1.0f - share);
        }

        private float clamp(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }
    }
}
