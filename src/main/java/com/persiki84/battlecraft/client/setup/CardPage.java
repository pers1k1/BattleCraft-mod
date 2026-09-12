package com.persiki84.battlecraft.client.setup;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class CardPage implements SetupPage {
    public interface Sample {
        void paint(GuiGraphics graphics, Font font, float centerX, int top, int width);
    }

    public record Probe(Component label, Runnable press, Runnable point) {}

    public interface Extra {
        AbstractWidget build(int left, int top, int width);

        int height();
    }

    public record Card(Component label, Component note, Sample sample, Runnable pick, Runnable hear,
                       List<Probe> probes) {
        public Card(Component label, Component note, Sample sample, Runnable pick, Runnable hear) {
            this(label, note, sample, pick, hear, List.of());
        }
    }

    private static final int CARD_GAP = 12;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 8;
    private static final int NAME_TOP = 9;
    private static final int NOTE_TOP = 24;
    private static final int SAMPLE_TOP = 42;
    private static final int PROBE_HEIGHT = 16;
    private static final int PROBE_GAP = 8;
    private static final int PROBE_INSET = 10;
    private static final int EXTRA_GAP = 14;
    private static final float CARD_RADIUS = 8.0f;
    private static final float NAME_SCALE = 0.86f;
    private static final float NAME_TRACKING = 1.2f;
    private static final float NOTE_SCALE = 0.62f;

    private final SetupStep step;
    private final List<Card> cards;
    private final int cardHeight;
    private final List<UiButton> buttons = new ArrayList<>();
    private final List<UiButton> probes = new ArrayList<>();
    private final List<Runnable> points = new ArrayList<>();
    private final boolean[] heard;

    private boolean[] touched = new boolean[0];
    private Extra extra;
    private Runnable release;

    public CardPage(SetupStep step, List<Card> cards, int cardHeight) {
        this.step = step;
        this.cards = cards;
        this.cardHeight = cardHeight;
        this.heard = new boolean[cards.size()];
    }

    public CardPage extra(Extra value) {
        extra = value;
        return this;
    }

    public CardPage onRelease(Runnable action) {
        release = action;
        return this;
    }

    @Override
    public void release() {
        if (release != null) release.run();
    }

    @Override
    public Component title() {
        return Component.translatable(step.titleKey());
    }

    @Override
    public Component note() {
        return Component.translatable(step.noteKey());
    }

    @Override
    public int contentHeight() {
        int stack = cardHeight + BUTTON_GAP + BUTTON_HEIGHT;
        return extra == null ? stack : stack + EXTRA_GAP + extra.height();
    }

    private int cardWidth(int width) {
        return (width - CARD_GAP * (cards.size() - 1)) / cards.size();
    }

    private int cardLeft(int left, int width, int column) {
        return left + column * (cardWidth(width) + CARD_GAP);
    }

    @Override
    public void build(SetupScreen screen, int left, int top, int width) {
        buttons.clear();
        probes.clear();
        points.clear();
        int y = top + cardHeight + BUTTON_GAP;
        for (int column = 0; column < cards.size(); column++) {
            Card card = cards.get(column);
            buttons.add(screen.place(new UiButton(cardLeft(left, width, column), y,
                    cardWidth(width), BUTTON_HEIGHT, card.label(), button -> card.pick().run())));
            buildProbes(screen, card, cardLeft(left, width, column), top, cardWidth(width));
        }
        if (extra != null) screen.place(extra.build(left, y + BUTTON_HEIGHT + EXTRA_GAP, width));
        touched = new boolean[probes.size()];
    }

    // WHY: образец надо услышать, ничего не выбрав, поэтому пробники живут внутри карточки,
    // WHY: молчат своим щелчком и играют ту схему, о которой карточка рассказывает
    private void buildProbes(SetupScreen screen, Card card, int left, int top, int width) {
        int total = card.probes().size();
        if (total == 0) return;

        int span = (width - PROBE_INSET * 2 - PROBE_GAP * (total - 1)) / total;
        for (int index = 0; index < total; index++) {
            Probe probe = card.probes().get(index);
            probes.add(screen.place(new UiButton(left + PROBE_INSET + index * (span + PROBE_GAP),
                    top + SAMPLE_TOP, span, PROBE_HEIGHT, probe.label(),
                    button -> probe.press().run()).muted()));
            points.add(probe.point());
        }
    }

    @Override
    public void paint(GuiGraphics graphics, Font font, int left, int top, int width, float leaving) {
        for (int column = 0; column < cards.size(); column++) {
            paintCard(graphics, font, cards.get(column), cardLeft(left, width, column), top, cardWidth(width));
            listen(column);
        }
        listenProbes();
    }

    private void listenProbes() {
        for (int index = 0; index < probes.size(); index++) {
            boolean pointed = probes.get(index).isHovered();
            if (pointed == touched[index]) continue;

            touched[index] = pointed;
            Runnable point = points.get(index);
            if (pointed && point != null) point.run();
        }
    }

    // WHY: карточку звука надо услышать раньше, чем выбрать, поэтому образец играет от наведения на кнопку
    private void listen(int column) {
        if (column >= buttons.size()) return;

        boolean pointed = buttons.get(column).isHovered();
        if (pointed == heard[column]) return;

        heard[column] = pointed;
        Runnable hear = cards.get(column).hear();
        if (pointed && hear != null) hear.run();
    }

    private void paintCard(GuiGraphics graphics, Font font, Card card, int left, int top, int width) {
        UiGlass.window(graphics, left, top, width, cardHeight, CARD_RADIUS, 1.0f);

        float centerX = left + width / 2.0f;
        UiRender.textTracked(graphics, font, card.label(), centerX, top + NAME_TOP,
                NAME_SCALE, NAME_TRACKING, UiAccent.textDim());
        UiRender.textCentered(graphics, font, card.note(), centerX, top + NOTE_TOP,
                NOTE_SCALE, UiAccent.textFaint(), false);
        if (card.sample() != null) card.sample().paint(graphics, font, centerX, top + SAMPLE_TOP, width);
    }
}
