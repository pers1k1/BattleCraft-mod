package com.persiki84.battlecraft.client.setup;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlow;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class Greeting implements SetupPage {
    private static final float MARK_DELAY = 0.0f;
    private static final float RULE_DELAY = 0.30f;
    private static final float HEAD_DELAY = 0.44f;
    private static final float LEAD_DELAY = 1.02f;
    private static final float HINT_DELAY = 1.60f;
    private static final float LINE_SECONDS = 0.80f;
    private static final float READY = HINT_DELAY + LINE_SECONDS;
    private static final float SCORE_AT = 2.30f;
    private static final float GLOW_RISE = 0.45f;

    private static final float MARK_SCALE = 0.42f;
    private static final float HEAD_SCALE = 1.1f;
    private static final float LEAD_SCALE = 1.0f;
    private static final float HINT_SCALE = 0.7f;

    private static final float MARK_TRACKING = 3.4f;
    private static final float HEAD_TRACKING = -0.35f;
    private static final float LEAD_TRACKING = 1.8f;
    private static final float HINT_TRACKING = 2.6f;

    private static final float MARK_SPREAD = 7.0f;
    private static final float HEAD_SPREAD = 3.2f;
    private static final float LEAD_SPREAD = 4.0f;

    private static final float MARK_TOP = -66.0f;
    private static final float RULE_TOP = -52.0f;
    private static final float HEAD_TOP = -14.0f;
    private static final float LEAD_TOP = 13.0f;
    private static final float HINT_TOP = 46.0f;

    private static final float RULE_WIDTH = 58.0f;
    private static final float RULE_HEIGHT = 1.0f;
    private static final float RULE_ALPHA = 0.42f;

    private static final float WORD_RULE_TOP = 11.0f;
    private static final float WORD_RULE_HEIGHT = 1.0f;
    private static final float WORD_RULE_ALPHA = 0.55f;

    private static final float LIFT = 16.0f;
    private static final float HEAD_LIFT = 10.0f;
    private static final float LETTER_LIFT = 2.6f;

    private static final float HALO = 0.85f;
    private static final float GLOW_SPEED = 1.7f;
    private static final float GLOW_READY = 0.5f;
    private static final float MARK_GLOW = 0.34f;
    private static final float HINT_GLOW = 0.30f;
    private static final float HEAD_GLOW_REST = 0.22f;
    private static final float HEAD_GLOW_PEAK = 0.95f;
    private static final float LETTER_LIGHTEN = 0.85f;

    private static final float HINT_BOB = 2.2f;
    private static final float HINT_BOB_PERIOD = 3100.0f;

    private static final float BREATH_PERIOD = 3400.0f;
    private static final float BREATH_LOW = 0.62f;
    private static final float BREATH_HIGH = 1.0f;

    private static final float WAVE_SECONDS = 2.4f;
    private static final float WAVE_SPREAD = 2.6f;
    private static final float BETWEEN = 0.55f;
    private static final float AGAIN = 1.4f;

    private final UiWave wave = new UiWave(WAVE_SECONDS, WAVE_SPREAD);
    private final Smooth glow = new Smooth(0.0f, GLOW_SPEED);

    private SetupScreen host;
    private WordRule rule;
    private String[] words;
    private int[] starts;
    private float elapsed;
    private float beat;
    private long stamp = -1L;
    private boolean ruleNext;

    @Override
    public Component title() {
        return Component.translatable("battlecraft.setup.greeting.head");
    }

    @Override
    public Component note() {
        return Component.translatable("battlecraft.setup.greeting.lead");
    }

    @Override
    public boolean framed() {
        return false;
    }

    @Override
    public int contentHeight() {
        return 0;
    }

    @Override
    public void build(SetupScreen screen, int left, int top, int width) {
        host = screen;
        elapsed = 0.0f;
        beat = AGAIN;
        stamp = -1L;
        ruleNext = false;
        glow.snap(0.0f);
        wave.stop();
        rule().stop();
    }

    public boolean settled() {
        return elapsed >= READY;
    }

    public void hurry() {
        elapsed = READY;
    }

    // WHY: в приветствии всё время что-то плывёт, поэтому строки рисуются мимо пиксельной сетки:
    // WHY: привязка к ней превращает медленный подъём подсказки в ступеньки
    @Override
    public void paint(GuiGraphics graphics, Font font, int left, int top, int width, float leaving) {
        advance();

        float centerX = width / 2.0f;
        float centerY = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2.0f;
        boolean snapped = UiRender.floating(true);
        try {
            UiGlow.halo(graphics, HALO * glow.get() * (1.0f - leaving),
                    () -> paintLight(graphics, font, centerX, centerY, leaving));
            paintInk(graphics, font, centerX, centerY, leaving);
        } finally {
            UiRender.floating(snapped);
        }
    }

    private void paintInk(GuiGraphics graphics, Font font, float centerX, float centerY, float leaving) {
        paintMark(graphics, font, centerX, centerY, leaving, UiAccent.textFaint());
        paintRule(graphics, centerX, centerY, leaving);
        paintHead(graphics, font, centerX, centerY, leaving, false);
        paintLead(graphics, font, centerX, centerY, leaving);
        paintWordRules(graphics, font, centerX, centerY, leaving, false);
        paintHint(graphics, font, centerX, centerY, leaving, UiAccent.textFaint());
    }

    // WHY: свет снимается тем же набором надписей, только цвет несёт яркость свечения, а не чернила:
    // WHY: полотно ореола чёрное, поэтому альфа буквы и есть сила её света
    private void paintLight(GuiGraphics graphics, Font font, float centerX, float centerY, float leaving) {
        paintMark(graphics, font, centerX, centerY, leaving,
                UiTheme.alpha(UiAccent.color(), MARK_GLOW * breath()));
        paintHead(graphics, font, centerX, centerY, leaving, true);
        paintWordRules(graphics, font, centerX, centerY, leaving, true);
        paintHint(graphics, font, centerX, centerY, leaving,
                UiTheme.alpha(UiAccent.color(), HINT_GLOW * breath()));
    }

    // WHY: каскад ждёт только свет проявления: строки должны пойти сразу за ним, а не под ним,
    // WHY: иначе игрок видит уже собранный заголовок и движения не остаётся
    private boolean revealing() {
        return host != null && host.revealing();
    }

    private void advance() {
        long frame = UiFrame.frame();
        if (frame == stamp) return;

        stamp = frame;
        glow.to(elapsed >= SCORE_AT - GLOW_RISE ? 1.0f : 0.0f, UiFrame.delta());
        if (elapsed < READY && revealing()) return;

        elapsed = Math.min(READY, elapsed + UiFrame.delta());
        if (elapsed < SCORE_AT) return;

        wave.advance();
        rule().advance();
        score();
    }

    // WHY: первый такт идёт ровно на SCORE_AT: beat начинается с полного счёта, ореол трогается
    // WHY: за GLOW_RISE до него и успевает пройти порог, иначе перед движением копятся две паузы
    // WHY: сначала свет проходит по заголовку, следом черта пробегает по словам подзаголовка,
    // WHY: и только потом партитура начинается заново: два движения никогда не идут разом
    private void score() {
        if (wave.running() || rule().running() || glow.get() < GLOW_READY) return;

        beat += UiFrame.delta();
        if (beat < (ruleNext ? BETWEEN : AGAIN)) return;

        beat = 0.0f;
        if (ruleNext) {
            rule().begin();
        } else {
            wave.begin();
        }
        ruleNext = !ruleNext;
    }

    private WordRule rule() {
        if (rule == null) rule = new WordRule(words().length);
        return rule;
    }

    private String[] words() {
        if (words == null) words = note().getString().split(" ");
        return words;
    }

    private void paintMark(GuiGraphics graphics, Font font, float centerX, float centerY,
                           float leaving, int color) {
        float shown = shown(MARK_DELAY);
        if (shown <= 0.0f) return;

        UiRender.textTitle(graphics, font, Component.translatable("battlecraft.menu.title"),
                centerX, centerY + MARK_TOP + rise(shown, LIFT), MARK_SCALE,
                MARK_TRACKING + spread(shown, leaving, MARK_SPREAD), tint(color, shown, leaving));
    }

    // WHY: черта раскрывается из середины наружу, поэтому у неё своя ширина, а не альфа
    private void paintRule(GuiGraphics graphics, float centerX, float centerY, float leaving) {
        float shown = shown(RULE_DELAY);
        if (shown <= 0.0f) return;

        float span = RULE_WIDTH * shown * (1.0f - leaving);
        if (span <= 0.5f) return;

        UiRender.panel(graphics, centerX - span / 2.0f, centerY + RULE_TOP, span, RULE_HEIGHT,
                RULE_HEIGHT / 2.0f, UiTheme.alpha(UiAccent.color(), RULE_ALPHA * (1.0f - leaving)));
    }

    private void paintHead(GuiGraphics graphics, Font font, float centerX, float centerY,
                           float leaving, boolean light) {
        float shown = shown(HEAD_DELAY);
        if (shown <= 0.0f) return;

        int base = light ? UiAccent.color() : UiAccent.text();
        UiRender.textHeroToned(graphics, font, title(), centerX,
                centerY + HEAD_TOP + rise(shown, HEAD_LIFT), HEAD_SCALE,
                HEAD_TRACKING + spread(shown, leaving, HEAD_SPREAD),
                tint(base, shown, leaving), letters(light, shown, leaving));
    }

    private UiRender.GlyphTone letters(boolean light, float shown, float leaving) {
        return new UiRender.GlyphTone() {
            @Override
            public int tint(int index, int count, int base) {
                float weight = wave.weight(index, count);
                if (light) {
                    float force = HEAD_GLOW_REST + (HEAD_GLOW_PEAK - HEAD_GLOW_REST) * weight;
                    return UiTheme.alpha(base, force * shown * (1.0f - leaving));
                }
                return UiTheme.alpha(UiTheme.lighten(base, LETTER_LIGHTEN * weight),
                        shown * (1.0f - leaving));
            }

            @Override
            public float rise(int index, int count) {
                return wave.weight(index, count) * LETTER_LIFT;
            }
        };
    }

    private void paintLead(GuiGraphics graphics, Font font, float centerX, float centerY, float leaving) {
        float shown = shown(LEAD_DELAY);
        if (shown <= 0.0f) return;

        UiRender.textTracked(graphics, font, note(), centerX, centerY + LEAD_TOP + rise(shown, LIFT),
                LEAD_SCALE, LEAD_TRACKING + spread(shown, leaving, LEAD_SPREAD),
                tint(UiAccent.textDim(), shown, leaving));
    }

    private void paintWordRules(GuiGraphics graphics, Font font, float centerX, float centerY,
                                float leaving, boolean light) {
        float shown = shown(LEAD_DELAY);
        if (shown < 1.0f || !rule().running()) return;

        float[] marks = UiRender.trackedStops(graphics, font, note(), LEAD_SCALE, LEAD_TRACKING);
        float gap = UiRender.trackedGap(graphics, LEAD_SCALE, LEAD_TRACKING);
        float left = centerX - marks[marks.length - 1] / 2.0f;
        float y = centerY + LEAD_TOP + WORD_RULE_TOP;
        for (int index = 0; index < words().length; index++) {
            paintWordRule(graphics, marks, gap, index, left, y, leaving, light);
        }
    }

    private void paintWordRule(GuiGraphics graphics, float[] marks, float gap, int index,
                               float left, float y, float leaving, boolean light) {
        int last = marks.length - 1;
        int begin = Math.min(starts()[index], last);
        int end = Math.min(begin + words()[index].length(), last);
        float from = left + marks[begin];
        float span = marks[end] - marks[begin] - (end < last ? gap : 0.0f);
        float to = from + span * Math.max(0.0f, rule().drawn(index) - rule().wiped(index));
        if (to - from <= 0.4f) return;

        boolean tail = index == words().length - 1;
        float force = light ? rule().shine() * (tail ? 1.0f : 0.0f) : WORD_RULE_ALPHA * rule().fade();
        if (force <= 0.01f) return;

        UiRender.panel(graphics, from, y, to - from, WORD_RULE_HEIGHT, WORD_RULE_HEIGHT / 2.0f,
                UiTheme.alpha(UiAccent.color(), force * (1.0f - leaving)));
    }

    // WHY: слова считаются по глифам строки, а не измеряются поштучно: только так черта ложится
    // WHY: ровно под слово, с тем же округлением пера, что и сами буквы
    private int[] starts() {
        if (starts != null) return starts;

        String[] parts = words();
        starts = new int[parts.length];
        int at = 0;
        for (int index = 0; index < parts.length; index++) {
            starts[index] = at;
            at += parts[index].length() + 1;
        }
        return starts;
    }

    private void paintHint(GuiGraphics graphics, Font font, float centerX, float centerY,
                           float leaving, int color) {
        float shown = shown(HINT_DELAY);
        if (shown <= 0.0f) return;

        UiRender.textTracked(graphics, font, Component.translatable("battlecraft.setup.greeting.hint"),
                centerX, centerY + HINT_TOP + rise(shown, LIFT) - bob(shown), HINT_SCALE, HINT_TRACKING,
                tint(color, shown, leaving));
    }

    // WHY: подсказка не мигает, а всплывает: медленная синусоида поднимает её на пару единиц,
    // WHY: и подъём набирает силу вместе с появлением строки, иначе она дёрнется на первом кадре
    private static float bob(float shown) {
        return UiAnim.pulse(HINT_BOB_PERIOD, 0.0f, HINT_BOB) * shown;
    }

    private float breath() {
        return settled() ? UiAnim.pulse(BREATH_PERIOD, BREATH_LOW, BREATH_HIGH) : 1.0f;
    }

    private float shown(float delay) {
        return UiAnim.easeOut((elapsed - delay) / LINE_SECONDS);
    }

    private static float rise(float shown, float lift) {
        return (1.0f - shown) * lift;
    }

    // WHY: на входе буквы съезжаются из разгона, на выходе разъезжаются обратно, и это одна и та же величина
    private static float spread(float shown, float leaving, float amount) {
        return (1.0f - shown + leaving) * amount;
    }

    private static int tint(int color, float shown, float leaving) {
        return UiTheme.alpha(color, shown * (1.0f - leaving));
    }
}
