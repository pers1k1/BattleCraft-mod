package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

// WHY: строка меняется по буквам, как числа и подписи в iOS: буквы, что стоят на своих местах в обеих
// WHY: строках, не трогаются, общий хвост перетекает на новое место, а остальные сменяются волной слева
// WHY: направо - старая уходит вверх и гаснет, новая приходит снизу. Перо рисует букву целым глифом,
// WHY: и размыть отдельную букву ему нечем, поэтому смена идёт прозрачностью и коротким ходом. Строка
// WHY: и в покое, и на смене рисуется одним пером с табличными цифрами, иначе вздрагивала бы на смене
// WHY: Смена посреди смены подхватывается с места: приходившие буквы уходят или доезжают оттуда, где
// WHY: их застала новая строка, а уходившие догорают отдельным слоем, а не пропадают кадром
public final class UiMorphText {
    static final float GLYPH_SECONDS = 0.3f;
    private static final float STAGGER_SECONDS = 0.02f;
    private static final float STAGGER_LIMIT = 0.3f;
    private static final float TRAVEL_UNITS = 2.0f;
    private static final float IDLE = Float.MAX_VALUE;
    private static final float FASTEST_TEMPO = 0.05f;
    private static final float GHOST_FLOOR = 0.004f;

    private final Side arriving = new Side(1.0f);
    private final Side leavingSide = new Side(-1.0f);
    private final Side steady = new Side(0.0f);
    private final Ghost fading = new Ghost();
    private Component value = Component.empty();
    private Component previous = Component.empty();
    private String raw = "";
    private int[] codes = new int[0];
    private int[] previousCodes = new int[0];
    private float[] stops = {0.0f};
    private float[] previousStops = {0.0f};
    private boolean regular;
    private static final float UNSUNG = 0.42f;
    private static final float LIFT_UNITS = 0.42f;
    private static final float MOTION_GROW = 0.035f;
    private static final float FOLLOW_SHARE = 0.45f;
    private static final float FOLLOW_RATE = 7.0f;
    private static final float ACCENT_SHARE = 0.55f;
    private static final float BLOOM_SHARE = 0.1f;
    private static final float ACCENT_LIGHT = 0.6f;
    private static final float HELD_GLOW = 0.16f;
    private static final float HELD_LIFT_UNITS = 0.3f;
    private static final float HELD_GROW = 0.1f;
    private static final float HELD_WEIGHT_UNITS = 0.35f;
    private static final float LEAVE_SHARE = 0.45f;
    private static final float HANDOFF_SHARE = 0.2f;

    private float clock = IDLE;
    private float pace;
    private float heading = 1.0f;
    private float shown;
    private float leaving;
    private float lastDelta;
    private float followed;
    private boolean followFresh = true;
    private boolean whole;
    private float[] litShown = new float[0];
    private int accentShown = -1;
    private float[] leavingLit = new float[0];
    private float[] glowShown = new float[0];
    private float[] leavingGlow = new float[0];
    private float[] motionShown = new float[0];
    private float[] leavingMotion = new float[0];
    private float[] heldShown = new float[0];
    private float[] leavingHeld = new float[0];
    private int leavingAccent = -1;
    private float scale = 1.0f;
    private UiSweep sweep = UiSweep.NONE;
    private int prefix;
    private int suffix;

    private float align;
    private float tracking;
    private float measuredPixels = -1.0f;
    private float measuredScale = -1.0f;
    private float measuredTracking;
    private boolean stopsFresh;
    private boolean previousFresh;
    private boolean related;
    private boolean[] keptNew = new boolean[0];
    private boolean[] keptOld = new boolean[0];
    private int[] matched = new int[0];
    private float[] keptShift = new float[0];
    private int keptCount;
    private int oldCount;
    private int lead;
    private int trail;

    private float[] entryShown = new float[0];
    private float[] entryDrift = new float[0];
    private float[] spareShown = new float[0];
    private float[] spareDrift = new float[0];
    private int entries;

    private Component ghost;
    private float[] ghostShown = new float[0];
    private float[] ghostDrift = new float[0];
    private int ghosts;
    private float ghostAim;
    private float ghostShift;
    private float ghostWidth;

    // WHY: строка рисуется начертанием экрана, как textLine, а не жирной гарнитурой подписей
    public UiMorphText regular() {
        regular = true;
        return this;
    }

    private void label(GuiGraphics graphics, Font font, Component text, float x, float y, float textScale,
                       int color, UiRender.GlyphTone tone) {
        label(graphics, font, text, x, y, textScale, 0.0f, color, tone);
    }

    private void label(GuiGraphics graphics, Font font, Component text, float x, float y, float textScale,
                       float textTracking, int color, UiRender.GlyphTone tone) {
        if (regular) {
            UiRender.labelTonedRegular(graphics, font, text, x, y, textScale, textTracking, color, tone);
            return;
        }
        UiRender.labelToned(graphics, font, text, x, y, textScale, textTracking, color, tone);
    }

    private float[] stopsOf(GuiGraphics graphics, Font font, Component text, float textScale, float textTracking) {
        return regular ? UiRender.trackedStopsRegular(graphics, font, text, textScale, textTracking)
                : UiRender.trackedStops(graphics, font, text, textScale, textTracking);
    }

    public void set(String next) {
        set(next, 0.0f);
    }

    // WHY: строки лирики идут в темпе песни: в быстром речитативе следующая строка приходит через
    // WHY: секунду, и полный морф съедал бы её половину. Смена укладывается в seconds, если оно
    // WHY: задано, иначе идёт своим обычным ходом
    public void set(String next, float seconds) {
        change(next, seconds, seconds > 0.0f, 1.0f);
    }

    // WHY: смена по буквам, сжатая в seconds: число, которое меняется чаще обычного хода, иначе
    // WHY: никогда не досматривалось бы до конца. Ход только сжимается, не растягивается. upward
    // WHY: false роняет новые буквы сверху, как у убывающего счётчика
    public void pace(String next, float seconds, boolean upward) {
        change(next, Math.max(0.0f, seconds), false, upward ? 1.0f : -1.0f);
    }

    public void snap(String next) {
        set(next);
        clock = IDLE;
    }

    public void clear() {
        snap("");
        entries = 0;
        ghost = null;
        ghosts = 0;
    }

    private void change(String next, float seconds, boolean wholeLine, float aim) {
        if (raw.equals(next)) return;

        hold(!wholeLine && morphing());
        previous = value;
        takeCodes(next);
        raw = next;
        value = Component.literal(next);
        whole = wholeLine;
        prefix = whole ? 0 : commonPrefix(previousCodes, codes);
        suffix = whole ? 0 : commonSuffix(previousCodes, codes, prefix);
        passSweep();
        clock = previousCodes.length == 0 ? IDLE : 0.0f;
        leaving = shown;
        shown = 0.0f;
        followFresh = true;
        heading = aim;
        pace = seconds;
        previousStops = stops;
        previousFresh = stopsFresh;
        stopsFresh = false;
        related = false;
    }

    private void takeCodes(String next) {
        int count = next.codePointCount(0, next.length());
        int[] spare = previousCodes;
        previousCodes = codes;
        codes = spare.length == count ? spare : new int[count];
        int offset = 0;
        for (int index = 0; index < count; index++) {
            int code = next.codePointAt(offset);
            codes[index] = code;
            offset += Character.charCount(code);
        }
    }

    // WHY: подсветка уходящей строки - та, с которой она горела в последнем кадре. Буферы меняются
    // WHY: местами, а не заводятся заново: число на бегу меняется по двадцать раз в секунду
    private void passSweep() {
        float[] lit = leavingLit;
        leavingLit = litShown;
        litShown = lit;
        float[] glow = leavingGlow;
        leavingGlow = glowShown;
        glowShown = glow;
        float[] motion = leavingMotion;
        leavingMotion = motionShown;
        motionShown = motion;
        float[] held = leavingHeld;
        leavingHeld = heldShown;
        heldShown = held;
        leavingAccent = accentShown;
    }

    private void hold(boolean interrupting) {
        if (!interrupting) {
            entries = 0;
            ghost = null;
            ghosts = 0;
            return;
        }
        keepGhost();
        keepEntries();
    }

    private void keepGhost() {
        int count = previousCodes.length;
        ghostShown = sized(ghostShown, count);
        ghostDrift = sized(ghostDrift, count);
        float brightest = 0.0f;
        for (int index = 0; index < count; index++) {
            ghostShown[index] = leavingShown(index, count);
            ghostDrift[index] = leavingDrift(index, count);
            brightest = Math.max(brightest, ghostShown[index]);
        }
        ghosts = count;
        ghost = brightest > GHOST_FLOOR ? previous : null;
        ghostAim = heading;
        ghostShift = leaving;
        ghostWidth = previousFresh ? previousStops[previousStops.length - 1] : 0.0f;
    }

    private void keepEntries() {
        int count = codes.length;
        spareShown = sized(spareShown, count);
        spareDrift = sized(spareDrift, count);
        for (int index = 0; index < count; index++) {
            spareShown[index] = arrivingShown(index, count);
            spareDrift[index] = arrivingDrift(index, count);
        }
        float[] shownSwap = entryShown;
        entryShown = spareShown;
        spareShown = shownSwap;
        float[] driftSwap = entryDrift;
        entryDrift = spareDrift;
        spareDrift = driftSwap;
        entries = count;
    }

    public void advance(float delta) {
        lastDelta = delta;
        if (clock != IDLE) clock += delta / tempo();
    }

    private float tempo() {
        if (pace <= 0.0f) return 1.0f;

        float tempo = Math.max(FASTEST_TEMPO, pace / duration());
        return whole ? tempo : Math.min(1.0f, tempo);
    }

    public boolean morphing() {
        return clock < duration();
    }

    public String raw() {
        return raw;
    }

    private int origin() {
        return align == 0.0f ? prefix : lead;
    }

    private int changedSpan() {
        return Math.max(1, codes.length - origin() - (align == 0.0f ? suffix : trail));
    }

    private float duration() {
        return GLYPH_SECONDS + Math.min(STAGGER_LIMIT, STAGGER_SECONDS * (changedSpan() - 1));
    }

    public float measure(GuiGraphics graphics, Font font, float textScale) {
        return measure(graphics, font, textScale, 0.0f);
    }

    // WHY: места букв запоминаются до смены строки, масштаба или плотности пикселей: замер на каждом
    // WHY: кадре заводил бы новый массив, а ширина нужна раскладке каждый кадр
    public float measure(GuiGraphics graphics, Font font, float textScale, float textTracking) {
        tracking = textTracking;
        freshStops(graphics, font, textScale);
        return stops[stops.length - 1];
    }

    public void draw(GuiGraphics graphics, Font font, float x, float y, float textScale, int color) {
        draw(graphics, font, x, y, textScale, color, UiSweep.NONE);
    }

    // WHY: подсветка лирики относится к строке в покое и к приходящей: уходящая уже пропета и уходит
    // WHY: полным цветом
    public void draw(GuiGraphics graphics, Font font, float x, float y, float textScale, int color, UiSweep lit) {
        mode(0.0f, 0.0f);
        if (!morphing()) {
            scale = textScale;
            sweep = lit;
            label(graphics, font, value, x, y, textScale, color, steady);
            return;
        }
        drawLeaving(graphics, font, x, y, textScale, color);
        drawArriving(graphics, font, x, y, textScale, color, lit);
    }

    // WHY: строка выровнена по якорю: align 0 держит голову, 1 хвост, 0.5 середину. Уходящая и
    // WHY: приходящая стоят каждая по своей ширине от якоря, поэтому у числа по правому краю младшие
    // WHY: разряды стоят на месте, а меняются только реально сменившиеся цифры
    public void drawAligned(GuiGraphics graphics, Font font, float anchorX, float y, float textScale,
                            float textTracking, float anchorShare, int color) {
        mode(anchorShare, textTracking);
        scale = textScale;
        sweep = UiSweep.NONE;
        freshStops(graphics, font, textScale);
        float x = anchorX - stops[stops.length - 1] * align;
        if (!morphing()) {
            label(graphics, font, value, x, y, textScale, tracking, color, steady);
            return;
        }
        freshRelation(graphics, font, textScale);
        float was = previousStops[previousStops.length - 1];
        paintGhost(graphics, font, anchorX - ghostWidth * align - ghostShift, y, color);
        label(graphics, font, previous, anchorX - was * align - leaving, y, textScale, tracking,
                color, leavingSide);
        label(graphics, font, value, x, y, textScale, tracking, color, arriving);
    }

    // WHY: на смене строки уходящая и приходящая рисуются порознь, чтобы каждая гасла у краёв слота по
    // WHY: своей прокрутке: общая кромка гасила конец одной и начало другой
    public void drawLeaving(GuiGraphics graphics, Font font, float x, float y, float textScale, int color) {
        mode(0.0f, 0.0f);
        scale = textScale;
        freshRelation(graphics, font, textScale);
        paintGhost(graphics, font, x - ghostShift, y, color);
        label(graphics, font, previous, x - leaving, y, textScale, color, leavingSide);
    }

    public void drawArriving(GuiGraphics graphics, Font font, float x, float y, float textScale, int color, UiSweep lit) {
        mode(0.0f, 0.0f);
        sweep = lit;
        scale = textScale;
        freshRelation(graphics, font, textScale);
        label(graphics, font, value, x, y, textScale, color, arriving);
    }

    private void paintGhost(GuiGraphics graphics, Font font, float x, float y, int color) {
        if (ghost == null || clock >= GLYPH_SECONDS) return;
        label(graphics, font, ghost, x, y, scale, tracking, color, fading);
    }

    public float leavingSpan(GuiGraphics graphics, Font font, float textScale) {
        freshStops(graphics, font, textScale);
        freshPrevious(graphics, font, textScale);
        return previousStops[previousStops.length - 1];
    }

    private void mode(float anchorShare, float textTracking) {
        if (anchorShare != align) related = false;
        align = anchorShare;
        tracking = textTracking;
    }

    private void freshStops(GuiGraphics graphics, Font font, float textScale) {
        float pixels = UiRender.restPixels(graphics);
        if (pixels != measuredPixels || textScale != measuredScale || tracking != measuredTracking) {
            measuredPixels = pixels;
            measuredScale = textScale;
            measuredTracking = tracking;
            stopsFresh = false;
            previousFresh = false;
        }
        if (stopsFresh) return;

        stops = stopsOf(graphics, font, value, textScale, tracking);
        stopsFresh = true;
        related = false;
    }

    private void freshPrevious(GuiGraphics graphics, Font font, float textScale) {
        if (previousFresh) return;

        previousStops = stopsOf(graphics, font, previous, textScale, tracking);
        previousFresh = true;
        related = false;
    }

    private void freshRelation(GuiGraphics graphics, Font font, float textScale) {
        freshStops(graphics, font, textScale);
        freshPrevious(graphics, font, textScale);
        if (!related) relate();
    }

    // WHY: буква стоит, если она та же на том же номере строки (строки равной длины: у часов 12:09 и
    // WHY: 12:10 стоят минуты и двоеточие) или лежит в общем начале или общем хвосте. Сверка по месту в
    // WHY: пикселях здесь не годится: цифры у шрифта разной ширины, и у строки по центру смена 09 на 10
    // WHY: сдвигала минуты на полпикселя, после чего они морфились без смены значения
    private void relate() {
        related = true;
        keptCount = Math.min(codes.length, stops.length - 1);
        oldCount = Math.min(previousCodes.length, previousStops.length - 1);
        keptNew = sized(keptNew, keptCount);
        keptOld = sized(keptOld, oldCount);
        matched = sized(matched, keptCount);
        keptShift = sized(keptShift, keptCount);
        Arrays.fill(keptNew, 0, keptCount, false);
        Arrays.fill(keptOld, 0, oldCount, false);
        Arrays.fill(matched, 0, keptCount, -1);
        Arrays.fill(keptShift, 0, keptCount, 0.0f);
        pair((stops[stops.length - 1] - previousStops[previousStops.length - 1]) * align);
        lead = 0;
        while (lead < keptCount && keptNew[lead]) lead++;
        trail = 0;
        while (trail < keptCount - lead && keptNew[keptCount - 1 - trail]) trail++;
    }

    private void pair(float offset) {
        if (codes.length == previousCodes.length) {
            int count = Math.min(keptCount, oldCount);
            for (int index = 0; index < count; index++) {
                if (codes[index] == previousCodes[index]) link(index, index, offset);
            }
            return;
        }
        int head = Math.min(prefix, Math.min(keptCount, oldCount));
        for (int index = 0; index < head; index++) link(index, index, offset);
        for (int step = 0; step < suffix; step++) {
            int index = codes.length - 1 - step;
            int other = previousCodes.length - 1 - step;
            if (index >= head && index < keptCount && other >= head && other < oldCount) link(index, other, offset);
        }
    }

    private void link(int index, int other, float offset) {
        keptNew[index] = true;
        keptOld[other] = true;
        matched[index] = other;
        keptShift[index] = previousStops[other] + offset - stops[index];
    }

    private static float[] sized(float[] array, int count) {
        return array.length >= count ? array : new float[Math.max(count, array.length * 2)];
    }

    private static boolean[] sized(boolean[] array, int count) {
        return array.length >= count ? array : new boolean[Math.max(count, array.length * 2)];
    }

    private static int[] sized(int[] array, int count) {
        return array.length >= count ? array : new int[Math.max(count, array.length * 2)];
    }

    // WHY: строка лирики длиннее слота едет за волной подсветки, а не по своим часам: пропеваемая
    // WHY: буква держится на FOLLOW_SHARE слота
    // WHY: уходящая строка уезжает с того места, где стояла: прокрученная бегущей строкой длинная
    // WHY: строка иначе в первый кадр смены отскакивала к своему началу
    public void scrolled(float shift) {
        shown = shift;
    }

    public float leaving() {
        return morphing() ? leaving : 0.0f;
    }

    private void remember(int index, int count, float lit, float glow, int accent) {
        if (litShown.length != count) litShown = new float[count];
        if (glowShown.length != count) glowShown = new float[count];
        if (motionShown.length != count) motionShown = new float[count];
        if (heldShown.length != count) heldShown = new float[count];
        if (index < count) {
            litShown[index] = lit;
            glowShown[index] = glow;
            motionShown[index] = Math.max(0.0f, Math.min(1.0f, sweep.motion(index)));
            heldShown[index] = Math.max(0.0f, Math.min(1.0f, sweep.held(index)));
        }
        accentShown = accent;
    }

    private static float frozenAt(float[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : 0.0f;
    }

    private float frozen(int index) {
        return index >= 0 && index < leavingLit.length ? leavingLit[index] : 1.0f;
    }

    private float frozenGlow(int index) {
        return index >= 0 && index < leavingGlow.length ? leavingGlow[index] : 0.0f;
    }

    public float follow(GuiGraphics graphics, Font font, float textScale, float slot, UiSweep lit) {
        mode(0.0f, 0.0f);
        freshStops(graphics, font, textScale);
        float target = followTarget(stops, slot, lit.head());
        if (followFresh) {
            followFresh = false;
            followed = target;
            return target;
        }
        followed += (target - followed) * (1.0f - (float) Math.exp(-FOLLOW_RATE * lastDelta));
        return followed;
    }

    // WHY: место голоса это дробный индекс буквы, а не первая недогоревшая буква: та прыгала на
    // WHY: соседнюю, как только догорала. Цель догоняется экспоненциально: метки слов дают ступени
    private static float followTarget(float[] marks, float slot, float head) {
        float span = marks[marks.length - 1];
        if (span <= slot || head < 0.0f || marks.length < 2) return 0.0f;

        int index = Math.min(marks.length - 2, (int) head);
        float place = marks[index] + (marks[index + 1] - marks[index]) * Math.min(1.0f, head - index);
        return Math.max(0.0f, Math.min(span - slot, place - slot * FOLLOW_SHARE));
    }

    public void drawRight(GuiGraphics graphics, Font font, float rightX, float y, float textScale, int color) {
        draw(graphics, font, rightX - measure(graphics, font, textScale), y, textScale, color);
    }

    private boolean heldNew(int index) {
        if (whole) return false;
        if (align == 0.0f && index < prefix) return true;
        return leaving == 0.0f && related && index < keptCount && keptNew[index];
    }

    private boolean heldOld(int index) {
        if (whole) return false;
        if (align == 0.0f && index < prefix) return true;
        return leaving == 0.0f && related && index < oldCount && keptOld[index];
    }

    private int departedOf(int index) {
        if (align == 0.0f && index < prefix) return index;
        return related && index < keptCount ? matched[index] : -1;
    }

    private float entryShownAt(int index) {
        return index >= 0 && index < entries ? entryShown[index] : 1.0f;
    }

    private float entryDriftAt(int index) {
        return index >= 0 && index < entries ? entryDrift[index] : 0.0f;
    }

    // WHY: буква, которую новая смена застала на подходе, а теперь она стоит на месте, доезжает с того
    // WHY: места, где её застали: иначе цифра, ещё наполовину прозрачная, вставала бы целой кадром
    private float settled(float from, float to) {
        return from + (to - from) * UiAnim.smoothstep(0.0f, GLYPH_SECONDS, clock);
    }

    private float arrivingShown(int index, int count) {
        if (heldNew(index)) return settled(entryShownAt(departedOf(index)), 1.0f);
        if (index >= count - suffix) return slide();
        return share(index, 1.0f);
    }

    private float arrivingDrift(int index, int count) {
        if (heldNew(index)) return settled(entryDriftAt(departedOf(index)), 0.0f);
        if (index >= count - suffix) return 0.0f;
        return -heading * (1.0f - share(index, 1.0f));
    }

    private boolean departsChanged(int index, int count) {
        return !heldOld(index) && index < count - suffix;
    }

    private float leavingShown(int index, int count) {
        if (heldOld(index)) return 0.0f;
        float from = entryShownAt(index);
        if (index >= count - suffix) return from * (1.0f - slide());
        return from * (1.0f - share(index, -1.0f));
    }

    private float leavingDrift(int index, int count) {
        if (heldOld(index)) return 0.0f;
        float from = entryDriftAt(index);
        if (index >= count - suffix) return from * (1.0f - slide());
        return from + (heading - from) * share(index, -1.0f);
    }

    // WHY: строка лирики сменяется передачей, а не наложением: уходящая целиком поднимается и гаснет за
    // WHY: первые LEAVE_SHARE смены, приходящая волна начинается с HANDOFF_SHARE и укладывается в тот же
    // WHY: срок. Иначе две разные строки на смене стояли друг на друге в одних и тех же местах
    private float share(int index, float direction) {
        if (whole && direction < 0.0f) return UiAnim.smoothstep(0.0f, duration() * LEAVE_SHARE, clock);

        float local = whole ? (clock - duration() * HANDOFF_SHARE) / (1.0f - HANDOFF_SHARE) : clock;
        int wave = Math.max(0, Math.min(index - origin(), changedSpan() - 1));
        float delay = Math.min(STAGGER_LIMIT, wave * STAGGER_SECONDS);
        return UiAnim.smoothstep(0.0f, 1.0f, (local - delay) / GLYPH_SECONDS);
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

    // WHY: уходившие буквы прерванной смены догорают с того места и той прозрачности, где их застала
    // WHY: новая строка, и доезжают в ту же сторону, куда ехали
    private final class Ghost implements UiRender.GlyphTone {
        @Override
        public int tint(int index, int count, int base) {
            float from = index < ghosts ? ghostShown[index] : 0.0f;
            return UiTheme.alpha(base, from * (1.0f - fade()));
        }

        @Override
        public float rise(int index, int count) {
            float from = index < ghosts ? ghostDrift[index] : 0.0f;
            return (from + (ghostAim - from) * fade()) * TRAVEL_UNITS * scale;
        }

        private float fade() {
            return UiAnim.smoothstep(0.0f, GLYPH_SECONDS, clock);
        }
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
        public float glow(int index, int count) {
            float lit = direction < 0.0f ? frozenGlow(index) : clamp(sweep.glow(index));
            float bloom = BLOOM_SHARE * lit + HELD_GLOW * held(index);
            return bloom * (direction < 0.0f ? shown(index, count) : 1.0f);
        }

        // WHY: буква затянутого слова держит акцент, пока слово тянется: подрастает, стоит выше, светится
        // WHY: цветом подсветки и густеет, как жирное начертание. Всё идёт по held, акцент разгорается и
        // WHY: гаснет плавно и к смене строки уже погас
        @Override
        public float grow(int index, int count) {
            return HELD_GROW * held(index) + MOTION_GROW * motion(index);
        }

        // WHY: уходящая строка уносит подъём и рост слога такими, какими они были в последнем кадре, и
        // WHY: уходит с ними вверх и гаснет. Раньше подъём на смене строки обнулялся, и только что
        // WHY: пропетое слово за один кадр падало на место
        private float motion(int index) {
            return direction < 0.0f ? frozenAt(leavingMotion, index) : clamp(sweep.motion(index));
        }

        @Override
        public float weigh(int index, int count) {
            return HELD_WEIGHT_UNITS * held(index);
        }

        private float held(int index) {
            return direction < 0.0f ? frozenAt(leavingHeld, index) : clamp(sweep.held(index));
        }

        // WHY: слог, который поётся сейчас, отдаёт в цвет подсветки и светится, пока поётся, и тает после
        @Override
        public int tint(int index, int count, int base) {
            float shown = shown(index, count);
            if (direction < 0.0f) return sung(base, shown, frozen(index), frozenGlow(index), leavingAccent, 0.0f);
            float lit = clamp(sweep.lit(index));
            float glow = clamp(sweep.glow(index));
            int accent = sweep.accent(base);
            remember(index, count, lit, glow, accent);
            return sung(base, shown, lit, glow, accent, held(index));
        }

        // WHY: уходящая строка уносит тот вид, каким горела в последнем кадре: без этого её буквы на
        // WHY: смене разом теряли приглушение, цвет и свечение и выглядели оторванными от текста.
        // WHY: Цвет подсветки сначала осветляется к белому: тёмный цвет иначе делал поющийся слог
        // WHY: тусклее уже пропетого, а в Spicy Lyrics он самый яркий
        private int sung(int base, float shown, float lit, float glow, int accentColor, float held) {
            int toned = UiTheme.alpha(base, shown * (UNSUNG + (1.0f - UNSUNG) * lit));
            float active = Math.max(glow, held);
            if (active <= 0.01f) return toned;
            int light = UiTheme.mix(0xFF000000 | accentColor, UiTheme.WHITE, ACCENT_LIGHT);
            return UiTheme.mix(toned, (toned & 0xFF000000) | (light & 0x00FFFFFF), ACCENT_SHARE * active);
        }

        private float shown(int index, int count) {
            if (direction == 0.0f) return 1.0f;
            return direction > 0.0f ? arrivingShown(index, count) : leavingShown(index, count);
        }

        @Override
        public float shift(int index, int count) {
            if (direction <= 0.0f || !heldNew(index) || index >= keptCount) return 0.0f;
            return keptShift[index] * (1.0f - slide());
        }

        @Override
        public float rise(int index, int count) {
            float lift = LIFT_UNITS * scale * motion(index) + HELD_LIFT_UNITS * scale * held(index);
            if (direction == 0.0f) return lift;

            float travel = TRAVEL_UNITS * scale;
            if (direction > 0.0f) return lift + arrivingDrift(index, count) * travel;
            float drift = leavingDrift(index, count) * travel;
            return departsChanged(index, count) ? drift : lift + drift;
        }

        private float clamp(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }
    }
}
