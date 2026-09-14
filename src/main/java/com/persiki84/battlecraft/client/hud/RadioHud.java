package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.voice.RadioTalk;
import com.persiki84.battlecraft.client.voice.VoiceTape;
import com.persiki84.battlecraft.client.voice.VoiceTraffic;
import com.persiki84.battlecraft.compat.walkie.Walkie;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.UUID;

// WHY: эфир это не значок, а карточка передачи: кто говорит, сколько он уже говорит и как идёт
// WHY: его запись. Своя передача стоит первой, соперник на нашей частоте виден, но не назван
public final class RadioHud {
    private static final float WIDTH = 122.0f;
    private static final float HEIGHT = 33.0f;
    private static final float PAD = 7.0f;
    private static final float ROW_GAP = 4.0f;
    private static final float NAME_SCALE = 0.76f;
    private static final float TIME_SCALE = 0.68f;
    private static final float NAME_TOP = 5.5f;
    private static final float TRACE_CENTER = 22.5f;
    private static final float TRACE_HEIGHT = 13.0f;
    private static final float REC_RADIUS = 1.8f;
    private static final float REC_LEFT = 4.0f;
    private static final float NAME_LEFT = 11.5f;
    private static final float NAME_ROOM = 52.0f;
    private static final float BARS_WIDTH = 9.0f;
    private static final float BARS_GAP = 3.5f;
    private static final float SLIDE_TRAVEL = 20.0f;
    private static final float CIPHER_TRACKING = 0.06f;
    private static final long HOLD_MS = 600L;
    private static final int SHOWN_AT_ONCE = 4;
    private static final String SAMPLE_KEY = "battlecraft.custom.sample.radio";
    private static final String OWN_KEY = "battlecraft.radio.own";

    private static final Component OWN_LABEL = Component.translatable(OWN_KEY);
    private static final Component SAMPLE_LABEL = Component.translatable(SAMPLE_KEY);

    private static final Card own = new Card();
    private static final Card[] cards = new Card[VoiceTraffic.VOICES];
    private static final Card sample = new Card();
    private static final VoiceTape sampleTape = new VoiceTape();

    static {
        for (int index = 0; index < cards.length; index++) {
            cards[index] = new Card();
        }
    }

    private RadioHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (!HudConfig.radioHud() || !Walkie.available() || !HudLayout.visible(HudSlot.RADIO)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, mc, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    public static void forget() {
        VoiceTraffic.forget();
        own.forget();
        for (Card card : cards) {
            card.forget();
        }
    }

    private static void render(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight) {
        long now = System.currentTimeMillis();
        float delta = UiFrame.delta();
        VoiceTraffic.advance(now, UiFrame.frame());

        float height = measure(mc, now, delta);
        if (height <= 0.0f) return;

        HudBox box = HudLayout.place(HudSlot.RADIO, WIDTH, height, screenWidth, screenHeight);
        stack(box);

        HudLayout.push(graphics, box);
        try {
            paintStack(graphics, mc, now, delta, box.alpha());
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    // WHY: одновременно говорящих на частоте может быть сколько угодно, но стопка карточек не
    // WHY: имеет права занять полэкрана: лишние ведут свою ленту, просто не показываются
    private static float measure(Minecraft mc, long now, float delta) {
        float total = 0.0f;
        int room = SHOWN_AT_ONCE;
        own.alphaValue = own.update(sending(mc, now), true, now, delta);
        if (own.alphaValue > 0.01f) {
            total += (HEIGHT + ROW_GAP) * UiAnim.easeOut(own.alphaValue);
            room--;
        }

        for (int index = 0; index < cards.length; index++) {
            VoiceTraffic.Radio radio = VoiceTraffic.at(index);
            Card card = cards[index];
            card.follow(mc, radio);
            boolean talking = radio.live(now) && radio.tape().talking(now);
            card.alphaValue = card.update(talking, room > 0, now, delta);
            if (card.alphaValue <= 0.01f) continue;

            total += (HEIGHT + ROW_GAP) * UiAnim.easeOut(card.alphaValue);
            room--;
        }
        return total <= 0.0f ? 0.0f : total - ROW_GAP;
    }

    private static boolean sending(Minecraft mc, long now) {
        return RadioTalk.speaking(mc.player, now);
    }

    private static void stack(HudBox box) {
        float y = box.y();
        y = seat(own, box.x(), y);
        for (Card card : cards) {
            y = seat(card, box.x(), y);
        }
    }

    private static float seat(Card card, float x, float y) {
        if (card.alphaValue <= 0.01f) return y;

        card.targetX = x;
        card.targetY = y;
        return y + (HEIGHT + ROW_GAP) * UiAnim.easeOut(card.alphaValue);
    }

    private static void paintStack(GuiGraphics graphics, Minecraft mc, long now, float delta, float boxAlpha) {
        if (own.alphaValue > 0.01f) {
            paint(graphics, mc, own, VoiceTraffic.own(), now, delta, own.alphaValue * boxAlpha,
                    Tone.OWN, 1.0f);
        }
        for (int index = 0; index < cards.length; index++) {
            Card card = cards[index];
            if (card.alphaValue <= 0.01f) continue;

            VoiceTraffic.Radio radio = VoiceTraffic.at(index);
            paint(graphics, mc, card, radio.tape(), now, delta, card.alphaValue * boxAlpha,
                    radio.friend() ? Tone.FRIEND : Tone.FOE, radio.signal());
        }
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        sampleTape.feed(0.35f + 0.45f * (float) Math.abs(Math.sin((now % 100000L) / 260.0)));
        sampleTape.advance(now);
        sample.nameText = SAMPLE_LABEL;

        HudLayout.sample(HudSlot.RADIO, WIDTH, HEIGHT);
        sample.targetX = box.x();
        sample.targetY = box.y();
        sample.y.snap(box.y());
        paint(graphics, mc, sample, sampleTape, now, UiFrame.delta(), alpha, Tone.FRIEND, 0.65f);
    }

    private static void paint(GuiGraphics graphics, Minecraft mc, Card card, VoiceTape tape,
                              long now, float delta, float alpha, Tone tone, float signal) {
        float eased = UiAnim.easeOut(alpha);
        float x = card.targetX + HudLayout.slideX(HudSlot.RADIO, (1.0f - eased) * SLIDE_TRAVEL);
        float y = card.y.to(card.targetY, delta);
        int accent = tone.color();

        UiVital.card(graphics, x, y, WIDTH, HEIGHT, UiMetrics.radius(HEIGHT), alpha);
        paintHead(graphics, mc, card, tape, now, x, y, alpha, accent, tone);
        if (tone != Tone.OWN) paintSignal(graphics, card, x, y, alpha, delta, accent, signal);
        VoiceTrace.paint(graphics, x + PAD, y + TRACE_CENTER, TRACE_HEIGHT, tape, now, accent, alpha);
    }

    private static void paintHead(GuiGraphics graphics, Minecraft mc, Card card, VoiceTape tape,
                                  long now, float x, float y, float alpha, int accent, Tone tone) {
        float pulse = tape.talking(now) ? UiAnim.pulse(760.0f, 0.45f, 1.0f) : 0.35f;
        UiRender.dot(graphics, x + REC_LEFT + REC_RADIUS, y + NAME_TOP + REC_RADIUS + 1.0f, REC_RADIUS,
                UiTheme.alpha(accent, alpha * pulse));

        if (tone == Tone.FOE) {
            UiRender.textTrackedToned(graphics, mc.font, card.cipher.text(now),
                    x + NAME_LEFT + NAME_ROOM / 2.0f, y + NAME_TOP, NAME_SCALE, CIPHER_TRACKING,
                    UiTheme.alpha(accent, alpha), card.cipher);
        } else {
            UiRender.textTrackedBox(graphics, mc.font, card.label(tone), x + NAME_LEFT,
                    y + NAME_TOP - 1.0f, mc.font.lineHeight * NAME_SCALE, NAME_ROOM, NAME_SCALE, 0.0f,
                    UiTheme.alpha(tone == Tone.OWN ? accent : HudInk.text(), alpha), true, 0.0f);
        }

        UiRender.labelRight(graphics, mc.font, card.clock(tape.spokenFor(now)), x + WIDTH - PAD,
                y + NAME_TOP, TIME_SCALE, UiTheme.alpha(HudInk.textDim(), alpha));
    }

    // WHY: помехи растут с расстоянием, и без шкалы это читается как поломка звука: три деления
    // WHY: говорят, что связь на пределе, ровно так же, как это слышно
    private static void paintSignal(GuiGraphics graphics, Card card, float x, float y, float alpha,
                                    float delta, int accent, float signal) {
        float shown = card.signal.to(UiAnim.clamp01(signal), delta);
        float left = x + WIDTH - PAD - card.timeWidth - BARS_GAP - BARS_WIDTH;
        float step = BARS_WIDTH / 3.0f;
        for (int index = 0; index < 3; index++) {
            float tall = 2.0f + index * 1.6f;
            boolean lit = shown > index / 3.0f;
            UiRender.panel(graphics, left + index * step, y + NAME_TOP + 5.5f - tall, step - 1.0f, tall,
                    0.4f, UiTheme.alpha(lit ? accent : HudInk.textFaint(), alpha * (lit ? 0.9f : 0.4f)));
        }
    }

    private enum Tone {
        OWN, FRIEND, FOE;

        private int color() {
            return switch (this) {
                case OWN -> HudLayout.tint(HudSlot.RADIO, UiAccent.color());
                case FRIEND -> HudLayout.tint(HudSlot.RADIO, UiAccent.dim());
                case FOE -> UiPalette.alert();
            };
        }
    }

    private static final class Card {
        private final Toggle visibility = new Toggle(9.0f, 0L);
        private final Spring y = new Spring(0.34f, 0.92f);
        private final Smooth signal = new Smooth(8.0f);
        private final VoiceCipher cipher = new VoiceCipher();
        private final StringBuilder clock = new StringBuilder(8);

        private Component nameText = Component.empty();
        private String time = "0:00.0";
        private UUID named;
        private int tenths = -1;
        private float timeWidth;
        private float alphaValue;
        private float targetX;
        private float targetY;
        private long heardAt;

        private float update(boolean talking, boolean allowed, long now, float delta) {
            if (talking) heardAt = now;
            return visibility.update(allowed && (talking || now - heardAt < HOLD_MS), delta);
        }

        // WHY: ник берётся из списка игроков, а не из пакета: сервер не имеет права рассылать
        // WHY: имя соперника, а для своих оно у клиента и так есть
        private void follow(Minecraft mc, VoiceTraffic.Radio radio) {
            UUID id = radio.id();
            if (id == null || id.equals(named)) return;

            named = id;
            cipher.forget();
            PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
            nameText = info == null ? Component.empty() : Component.literal(info.getProfile().getName());
        }

        private Component label(Tone tone) {
            return tone == Tone.OWN ? OWN_LABEL : nameText;
        }

        private String clock(long spokenMs) {
            int now = (int) Math.min(5999L, spokenMs / 100L);
            if (now == tenths) return time;

            tenths = now;
            int seconds = now / 10;
            clock.setLength(0);
            clock.append(seconds / 60).append(':');
            if (seconds % 60 < 10) clock.append('0');
            clock.append(seconds % 60).append('.').append(now % 10);
            time = clock.toString();
            timeWidth = UiRender.widthLabel(Minecraft.getInstance().font, time) * TIME_SCALE;
            return time;
        }

        private void forget() {
            named = null;
            nameText = Component.empty();
            tenths = -1;
            heardAt = 0L;
            cipher.forget();
        }
    }
}
