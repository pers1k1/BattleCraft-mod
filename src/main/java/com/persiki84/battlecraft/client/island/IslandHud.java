package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.battlecraft.client.lyrics.LyricLine;
import com.persiki84.battlecraft.client.lyrics.LyricText;
import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMarquee;
import com.persiki84.shared.client.ui.UiMorphText;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiSweep;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVeil;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class IslandHud {
    private static final float PILL_HEIGHT = 17.0f;
    private static final float CARD_HEIGHT = 42.0f;
    private static final float PAD = 5.0f;
    private static final float FACE = 13.0f;
    private static final float ART = 26.0f;
    private static final float GAP = 6.0f;
    private static final float WAVE_GAP = 6.0f;
    private static final float WAVE_CARD_CENTER = 15.5f;
    private static final float CAPSULE_GAP = 4.0f;
    private static final float CAPSULE_PAD = 6.0f;
    private static final float MAX_RADIUS = 13.0f;
    private static final float FLIGHT_EDGE = 0.002f;

    private static final float NICK_SCALE = 0.85f;
    private static final float TITLE_PILL_SCALE = 0.78f;
    private static final float TITLE_CARD_SCALE = 0.88f;
    private static final float ARTIST_SCALE = 0.72f;
    private static final float STAT_SCALE = 0.72f;
    private static final float UNIT_SCALE = 0.62f;
    private static final float TIME_SCALE = 0.58f;

    private static final float CARD_TITLE_TOP = 7.0f;
    private static final float CARD_ARTIST_TOP = 18.0f;
    private static final float CARD_BAR_TOP = 27.0f;
    private static final float CARD_TIME_TOP = 31.0f;
    private static final float BAR_HEIGHT = 2.5f;
    private static final float HAIRLINE = 1.4f;
    private static final float PILL_TITLE_TOP = 2.2f;
    private static final float PILL_ROW_CENTER = 6.2f;
    private static final float PILL_TIME_TOP = 10.6f;
    private static final float PILL_BAR_CENTER = 13.4f;
    private static final float PILL_TIME_GAP = 4.0f;
    private static final float BAR_SHIFT_SPEED = 12.0f;
    private static final float PILL_BAR_MIN = 28.0f;
    private static final float SEEK_HEAD_PILL = 1.6f;
    private static final float SEEK_HEAD_CARD = 1.15f;
    private static final float SEEK_HEAD_LIT = 0.55f;

    // WHY: цифры FPS, пинга и таймера меняют ширину строки на пару единиц (99 -> 100, 9:59 -> 10:00),
    // WHY: и остров прыгал шире и уже вместе с ними; ширина держит свой максимум и отпускает его,
    // WHY: только когда строка стала короче больше чем на запас (другой набор цифр или скрыт счётчик)
    private static final float HOLD_SLACK = 10.0f;
    private static final float TITLE_MIN = 36.0f;
    private static final float TITLE_MAX = 70.0f;
    private static final float CARD_TEXT_MIN = 84.0f;
    private static final float CARD_TEXT_MAX = 104.0f;
    private static final float STAT_INSET = 8.0f;
    private static final float STAT_BAND = 9.5f;
    private static final float UNIT_GAP = 2.5f;
    private static final float PAIR_GAP = 7.0f;

    private static final Component FPS_UNIT = Component.literal("FPS");
    private static final Component PING_UNIT = Component.literal("Ping");

    private static final UiMorphText title = new UiMorphText();
    private static final UiMorphText artist = new UiMorphText();
    private static final UiMorphText timing = new UiMorphText();
    private static final UiMorphText pillRow = new UiMorphText();
    private static final IslandLyrics lyrics = new IslandLyrics();
    private static final int FIELD_LIMIT = 200;
    private static final Frame frame = new Frame();
    private static final IslandMorph morph = new IslandMorph();
    // WHY: волосок таблетки стоит за удержанной наибольшей шириной таймера и доезжает к ней плавно:
    // WHY: от ширины текущей строки он прыгал на каждой смене цифры вместе с общим временем
    private static final Smooth barShift = new Smooth(BAR_SHIFT_SPEED);

    private static MediaTrack shownTrack = MediaTrack.NONE;
    private static String titleText = "";
    private static String artistText = "";
    private static String pairedArtist = "";
    private static Component titleValue = Component.empty();
    private static float widened;
    private static long shownPlayed = -1L;
    private static long shownWhole = -1L;
    private static long morphedFrame = -1L;
    private static float statsPeak;
    private static float timingPeak;

    private IslandHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (!HudConfig.island() || !HudLayout.visible(HudSlot.ISLAND)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, float screenWidth, float screenHeight) {
        Font font = Minecraft.getInstance().font;
        MediaTrack track = IslandModel.track();
        prepare(graphics, font, track, HudLayout.of(HudSlot.ISLAND).scale());

        HudBox box = HudLayout.place(HudSlot.ISLAND, Math.max(frame.steady, frame.capsule),
                frame.height + frame.tail, screenWidth, screenHeight);
        HudLayout.push(graphics, box);
        try {
            paint(graphics, font, track, box.x(), box.y(), box.alpha());
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void prepare(GuiGraphics graphics, Font font, MediaTrack track, float layoutScale) {
        IslandGlyph.drive();
        refresh(track);
        advanceMorph();
        measureAt(graphics, font, layoutScale);
    }

    // WHY: перо снапит кегль к пикселям той позы, в которой меряет, а остров рисуется уже внутри
    // WHY: масштаба своего слота: замер вне его давал другую ширину строки, и длинное название
    // WHY: ехало бегущей строкой не по той ширине и сдвигало части острова при размере не 1.0
    private static void measureAt(GuiGraphics graphics, Font font, float layoutScale) {
        if (layoutScale == 1.0f) {
            measure(graphics, font);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().scale(layoutScale, layoutScale, 1.0f);
        try {
            measure(graphics, font);
        } finally {
            graphics.pose().popPose();
        }
    }

    // WHY: превью в редакторе HUD и сам HUD могут звать остров в одном кадре, а морф идёт по времени
    // WHY: и шагнул бы дважды: защёлка по кадру, как у модели. Вне музыки морф стоит свёрнутым
    private static void advanceMorph() {
        long now = UiFrame.frame();
        if (now == morphedFrame) return;
        morphedFrame = now;
        advanceText(UiFrame.delta());
        if (IslandModel.media() <= 0.0f) {
            morph.snap(false);
            return;
        }
        morph.advance(IslandModel.carded(), UiFrame.delta());
    }

    private static void measure(GuiGraphics graphics, Font font) {
        frame.media = IslandModel.media();
        frame.blind = IslandModel.blind() * frame.media;
        frame.shape = morph.shape() * frame.media;
        frame.pill = lerp(1.0f, morph.pill(), frame.media);
        frame.card = morph.card() * frame.media;
        frame.height = morph.height(PILL_HEIGHT, CARD_HEIGHT);
        frame.face = HudConfig.islandAvatar() || showsArt() ? FACE : 0.0f;
        frame.art = HudConfig.islandAvatar() || showsArt() ? ART : 0.0f;
        statsPeak = held(statsPeak, statsWidth(graphics, font));
        frame.stats = statsPeak;
        frame.waveSlot = HudConfig.islandVisualizer() ? IslandGlyph.PILL_WIDTH + WAVE_GAP : 0.0f;
        frame.cardWaveSlot = HudConfig.islandVisualizer() ? IslandGlyph.CARD_WIDTH + WAVE_GAP : 0.0f;
        measureWidths(graphics, font);
        frame.capsule = frame.stats > 0.0f ? CAPSULE_PAD * 2.0f + frame.stats : 0.0f;
        frame.drop = UiAnim.easeOutBack(frame.media);
        frame.tail = frame.capsule > 0.0f ? frame.drop * (CAPSULE_GAP + PILL_HEIGHT) : 0.0f;
    }

    // WHY: остров тянется за названием, но верхний предел держит его подальше от «Счёта и точек»
    // WHY: в центре экрана: всё, что длиннее слота, уезжает бегущей строкой
    private static void measureWidths(GuiGraphics graphics, Font font) {
        float pillTitle = PAD + FACE + GAP + clamp(titleWidth(graphics, font, TITLE_PILL_SCALE),
                TITLE_MIN, TITLE_MAX) + frame.waveSlot + PAD;
        timingPeak = held(timingPeak, pillRow.measure(graphics, font, TIME_SCALE));
        float pillTiming = PAD + FACE + GAP + Math.min(timingPeak, TITLE_MAX)
                + (untimed() ? 0.0f : PILL_TIME_GAP + PILL_BAR_MIN) + PAD;
        float pill = Math.max(pillTitle, pillTiming * (1.0f - frame.blind));
        float idle = PAD + FACE + GAP + UiRender.measure(graphics, font, IslandModel.nick(), NICK_SCALE)
                + STAT_INSET + frame.stats + PAD;
        frame.cardWidth = PAD + ART + GAP + clamp(Math.max(
                titleWidth(graphics, font, TITLE_CARD_SCALE),
                artist.measure(graphics, font, ARTIST_SCALE)),
                CARD_TEXT_MIN, CARD_TEXT_MAX) + frame.cardWaveSlot + PAD;
        frame.pillWidth = lerp(idle, pill, frame.media);
        frame.steady = morph.steadyWidth(frame.pillWidth, frame.cardWidth);
        frame.width = morph.width(frame.pillWidth, frame.cardWidth);
    }

    // WHY: пока у песни есть лирика, ширина держится по самой длинной её строке и по названию:
    // WHY: строки сменяются каждые две-три секунды, и стекло не должно ходить за каждой
    private static float titleWidth(GuiGraphics graphics, Font font, float scale) {
        float titleWidth = UiRender.measureToned(graphics, font, titleValue, scale);
        if (!lyrics.engaged() || widened <= 0.0f) return titleWidth;
        return titleWidth + (Math.max(lyrics.widest(graphics, font, scale), titleWidth) - titleWidth) * widened;
    }

    private static float held(float peak, float measured) {
        return measured > peak || measured < peak - HOLD_SLACK ? measured : peak;
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Font font = Minecraft.getInstance().font;
        MediaTrack track = IslandModel.track();
        prepare(graphics, font, track, 1.0f);
        HudLayout.sample(HudSlot.ISLAND, Math.max(frame.steady, frame.capsule), frame.height + frame.tail);
        paint(graphics, font, track, box.x(), box.y(), alpha);
    }

    private static void paint(GuiGraphics graphics, Font font, MediaTrack track, float x, float y, float alpha) {
        float swelled = x - (frame.width - frame.steady) / 2.0f;
        paintAt(graphics, font, track, swelled, y, alpha);
    }

    // WHY: бокс острова ставится по ширине без толчка, а стекло вздувается вокруг неподвижного
    // WHY: центра: у острова, прижатого к краю экрана, толчок иначе сдвигал всё содержимое вбок
    private static void paintAt(GuiGraphics graphics, Font font, MediaTrack track, float x, float y, float alpha) {
        float radius = IslandMorph.radius(frame.height, PILL_HEIGHT);
        boolean outer = UiRender.floating(true);
        UiRender.floating(outer || !morph.resting());
        try {
            paintIsland(graphics, font, track, x, y, radius, alpha);
        } finally {
            UiRender.floating(outer);
        }
        drawCounter(graphics, font, x, y, alpha);
    }

    // WHY: окно острова не размывается никогда: стекло рисуется резко прямо в кадр, а на ходу в офскрин
    // WHY: уходит только содержимое и ложится обратно размытым, обрезанным по форме окна
    private static void paintIsland(GuiGraphics graphics, Font font, MediaTrack track, float x, float y,
                                    float radius, float alpha) {
        UiVital.card(graphics, x, y, frame.width, frame.height, radius, alpha);
        boolean veiled = morph.veiling() && frame.media > 0.02f && UiVeil.begin(graphics);
        try {
            paintContent(graphics, font, track, x, y, alpha);
        } finally {
            if (veiled) {
                UiVeil.end(graphics, x, y, frame.width, frame.height, radius, morph.blur() * IslandMorph.BLUR, 1.0f);
            }
        }
        if (!morph.veiling()) UiVeil.tidy();
    }

    private static void paintContent(GuiGraphics graphics, Font font, MediaTrack track, float x, float y,
                                     float alpha) {
        float pillX = x + (frame.width - frame.pillWidth) / 2.0f;
        float cardX = x + (frame.width - frame.cardWidth) / 2.0f;
        UiRender.clip(graphics, x, y, frame.width, frame.height);
        try {
            drawPill(graphics, font, track, pillX, y, alpha * frame.pill, settledOn(true) ? alpha : 0.0f);
            drawCard(graphics, font, track, cardX, y, alpha * frame.card, settledOn(false) ? alpha : 0.0f);
            if (flying()) drawFlight(graphics, font, track, pillX, cardX, y, alpha);
        } finally {
            graphics.disableScissor();
        }
    }

    // WHY: общие части (лицо, название, время с полосой, визуализатор) стоят в раскладке только когда
    // WHY: форма дошла до её края; между краями их ведёт полёт. Части одной раскладки (ник, исполнитель,
    // WHY: строка трека без длины) идут своей долей и перетекают под размытием
    private static void drawPill(GuiGraphics graphics, Font font, MediaTrack track, float x, float y, float solo,
                                 float shared) {
        if (solo <= 0.02f && shared <= 0.02f) return;

        if (frame.face > 0.0f && shared > 0.02f) {
            IslandFace.draw(graphics, x + PAD + FACE / 2.0f, y + PILL_HEIGHT / 2.0f, FACE, shared, showsArt(),
                    HudConfig.islandAvatar());
        }
        drawMainLine(graphics, font, x, y, solo, shared);
        drawHairline(graphics, font, track, x, y, untimed() ? solo : shared);
        drawPillBars(graphics, x, y, shared);
    }

    private static void drawCard(GuiGraphics graphics, Font font, MediaTrack track, float x, float y, float solo,
                                 float shared) {
        if (solo <= 0.02f && shared <= 0.02f) return;

        if (frame.art > 0.0f && shared > 0.02f) {
            IslandFace.draw(graphics, x + PAD + ART / 2.0f, y + CARD_HEIGHT / 2.0f, ART, shared, showsArt(),
                    HudConfig.islandAvatar());
        }
        drawCardText(graphics, font, x, y, solo, shared);
        drawCardBar(graphics, font, track, x, y, untimed() ? solo : shared);
        if (frame.cardWaveSlot > 0.0f && shared > 0.02f) {
            IslandGlyph.cardBars(graphics, x + frame.cardWidth - PAD - IslandGlyph.CARD_WIDTH / 2.0f,
                    y + WAVE_CARD_CENTER, shared);
        }
    }

    private static boolean settledOn(boolean pill) {
        return pill ? frame.shape <= FLIGHT_EDGE : frame.shape >= 1.0f - FLIGHT_EDGE;
    }

    // WHY: обложка живёт в том же месте, что и аватар, поэтому выключенный аватар не должен уносить её с собой
    private static boolean showsArt() {
        return HudConfig.islandCover() && frame.media > 0.02f && frame.blind < 0.5f && IslandArt.ready();
    }

    // WHY: ник и название стоят каждый в своём покое, своим кеглем. Переход между ними заявляется
    // WHY: позой покоя: уходящая строка едет и растёт к месту приходящей, а та приходит из места
    // WHY: уходящей, поэтому буквы движутся непрерывно, а не ступенями по пикселю
    private static void drawMainLine(GuiGraphics graphics, Font font, float x, float y, float solo,
                                     float shared) {
        float textX = x + PAD + frame.face + GAP;
        float reserve = lerp(frame.stats + STAT_INSET, frame.waveSlot, frame.media);
        float slot = x + frame.pillWidth - PAD - reserve - textX;
        if (slot <= 4.0f) return;

        float margin = UiMarquee.margin(slot, TITLE_PILL_SCALE);
        UiRender.clip(graphics, textX - margin, y, slot + margin * 2.0f, PILL_HEIGHT);
        try {
            if (HudConfig.islandNick()) drawNick(graphics, font, textX, y, slot, solo * (1.0f - frame.media));
            if (HudConfig.islandTitle()) drawTitle(graphics, font, textX, y, slot, shared * frame.media);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawNick(GuiGraphics graphics, Font font, float textX, float y, float slot, float alpha) {
        if (alpha <= 0.02f) return;

        float nickTop = UiRender.centerY(y, PILL_HEIGHT, NICK_SCALE);
        float scale = lerp(1.0f, TITLE_PILL_SCALE / NICK_SCALE, frame.media);
        UiRestFrame.push(graphics, textX, nickTop, scale, scale, 0.0f, (titleLive(y) - nickTop) * frame.media);
        try {
            line(graphics, font, IslandModel.nick(), IslandModel.nickRaw(), textX, nickTop, slot, NICK_SCALE,
                    UiTheme.alpha(inked(), alpha));
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    // WHY: у трека без управления название стоит по центру таблетки, у обычного у её верха; место
    // WHY: покоя берётся по ближней стороне, а переезд между ними идёт заявленным ходом
    private static void drawTitle(GuiGraphics graphics, Font font, float textX, float y, float slot, float alpha) {
        if (alpha <= 0.02f) return;

        float nickTop = UiRender.centerY(y, PILL_HEIGHT, NICK_SCALE);
        float centred = UiRender.centerY(y, PILL_HEIGHT, TITLE_PILL_SCALE);
        float titleTop = frame.blind >= 0.5f ? centred : y + PILL_TITLE_TOP;
        float scale = lerp(NICK_SCALE / TITLE_PILL_SCALE, 1.0f, frame.media);
        float move = lerp(nickTop - titleTop, titleLive(y) - titleTop, frame.media);
        UiRestFrame.push(graphics, textX, titleTop, scale, scale, 0.0f, move);
        try {
            morphLine(graphics, font, title, textX, titleTop, slot, TITLE_PILL_SCALE,
                    UiTheme.alpha(inked(), alpha), lyrics.sweep());
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private static float titleLive(float y) {
        return lerp(y + PILL_TITLE_TOP, UiRender.centerY(y, PILL_HEIGHT, TITLE_PILL_SCALE), frame.blind);
    }

    private static void drawCardText(GuiGraphics graphics, Font font, float x, float y, float solo,
                                     float shared) {
        float textX = x + PAD + frame.art + GAP;
        float slot = x + frame.cardWidth - PAD - frame.cardWaveSlot - textX;
        if (slot <= 4.0f) return;

        float margin = UiMarquee.margin(slot, TITLE_CARD_SCALE);
        UiRender.clip(graphics, textX - margin, y, slot + margin * 2.0f, CARD_HEIGHT);
        try {
            if (HudConfig.islandTitle()) {
                morphLine(graphics, font, title, textX, y + CARD_TITLE_TOP, slot, TITLE_CARD_SCALE,
                        UiTheme.alpha(inked(), shared), lyrics.sweep());
            }
            if (HudConfig.islandArtist()) {
                float rise = lerp(CARD_TITLE_TOP, CARD_ARTIST_TOP, frame.card);
                morphLine(graphics, font, artist, textX, y + rise, slot, ARTIST_SCALE,
                        UiTheme.alpha(inked(), solo * frame.card));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    // WHY: ключ бегущей строки берётся из хранимой строки: getString() собирал новую строку и
    // WHY: пересчитывал её хэш каждый кадр на каждую длинную строку острова
    private static void line(GuiGraphics graphics, Font font, Component value, String key, float x, float y,
                             float slot, float scale, int color) {
        if ((color >>> 24) < 3) return;

        float span = UiRender.measure(graphics, font, value, scale);
        if (span <= slot) {
            UiRender.labelScaled(graphics, font, value, x, y, scale, color);
            return;
        }

        float start = x - UiRender.marqueeShift(graphics, key, span - slot);
        UiRender.marqueeFrom(graphics, x, slot, start, span, scale);
        try {
            UiRender.labelScaled(graphics, font, value, start, y, scale, color);
        } finally {
            UiRender.marqueeDone();
        }
    }

    private static void drawHairline(GuiGraphics graphics, Font font, MediaTrack track, float x, float y,
                                     float alpha) {
        float fade = alpha * frame.media * (1.0f - frame.blind);
        if (fade <= 0.02f || !track.present()) return;

        float left = x + PAD + frame.face + GAP;
        float timed = 0.0f;
        if (HudConfig.islandTime()) {
            pillRow.draw(graphics, font, left, y + PILL_TIME_TOP, TIME_SCALE, UiTheme.alpha(inked(), fade));
            timed = barShift.get() + PILL_TIME_GAP;
        }
        if (!HudConfig.islandBar() || untimed()) return;

        float barLeft = left + timed;
        float span = x + frame.pillWidth - PAD - barLeft;
        if (span <= 4.0f) return;

        float value = IslandProgress.value();
        float top = y + PILL_BAR_CENTER - HAIRLINE / 2.0f;
        hair(graphics, barLeft, top, span, UiTheme.withAlpha(UiTheme.WHITE, 0.16f * fade));
        hair(graphics, barLeft, top, span * value, UiTheme.alpha(UiAccent.color(), fade));
        drawSeekHead(graphics, barLeft + span * value, y + PILL_BAR_CENTER, HAIRLINE * SEEK_HEAD_PILL, fade);
    }

    // WHY: волосок прогресса это полтора пикселя в высоту, и полигон с растушёвкой на такой
    // WHY: толщине рвётся так же, как полоски визуализатора: точная форма считается шейдером
    private static void hair(GuiGraphics graphics, float x, float y, float span, int color) {
        strip(graphics, x, y, span, HAIRLINE, color);
    }

    private static void strip(GuiGraphics graphics, float x, float y, float span, float thick, int color) {
        if (span <= 0.0f || (color >>> 24) == 0) return;

        if (UiCrisp.ready()) {
            UiCrisp.panel(graphics, x, y, span, thick, thick / 2.0f, color);
            return;
        }
        UiRender.panel(graphics, x, y, span, thick, thick / 2.0f, color);
    }

    private static boolean flying() {
        return frame.shape > FLIGHT_EDGE && frame.shape < 1.0f - FLIGHT_EDGE;
    }

    private static void drawFlight(GuiGraphics graphics, Font font, MediaTrack track, float pillX, float cardX,
                                   float y, float alpha) {
        float k = frame.shape;
        if (frame.face > 0.0f) {
            IslandFace.draw(graphics, lerp(pillX + PAD + FACE / 2.0f, cardX + PAD + ART / 2.0f, k),
                    y + lerp(PILL_HEIGHT, CARD_HEIGHT, k) / 2.0f, lerp(FACE, ART, k), alpha, showsArt(),
                    HudConfig.islandAvatar());
        }
        if (HudConfig.islandTitle()) flyTitle(graphics, font, pillX, cardX, y, k, alpha);
        if (!untimed()) flyTiming(graphics, font, track, pillX, cardX, y, k, alpha);
        if (frame.waveSlot > 0.0f) flyBars(graphics, pillX, cardX, y, k, alpha * frame.media);
    }

    private static void flyTitle(GuiGraphics graphics, Font font, float pillX, float cardX, float y, float k,
                                 float alpha) {
        float pillText = pillX + PAD + frame.face + GAP;
        float cardText = cardX + PAD + frame.art + GAP;
        float slot = lerp(pillX + frame.pillWidth - PAD - frame.waveSlot - pillText,
                cardX + frame.cardWidth - PAD - frame.cardWaveSlot - cardText, k);
        if (slot <= 4.0f) return;

        float scale = lerp(TITLE_PILL_SCALE / TITLE_CARD_SCALE, 1.0f, k);
        float left = lerp(pillText, cardText, k);
        float top = y + CARD_TITLE_TOP;
        float margin = UiMarquee.margin(slot, TITLE_CARD_SCALE * scale);
        UiRender.clip(graphics, left - margin, y, slot + margin * 2.0f, frame.height);
        UiRestFrame.push(graphics, cardText, top, scale, scale, left - cardText, (titleLive(y) - top) * (1.0f - k));
        try {
            morphLine(graphics, font, title, cardText, top, slot / scale, TITLE_CARD_SCALE,
                    UiTheme.alpha(inked(), alpha), lyrics.sweep());
        } finally {
            UiRestFrame.pop(graphics);
            graphics.disableScissor();
        }
    }

    private static void flyTiming(GuiGraphics graphics, Font font, MediaTrack track, float pillX, float cardX,
                                  float y, float k, float alpha) {
        float fade = alpha * (1.0f - frame.blind);
        if (fade <= 0.02f || !track.present()) return;

        float pillLeft = pillX + PAD + frame.face + GAP;
        float cardLeft = cardX + PAD + frame.art + GAP;
        float timed = 0.0f;
        if (HudConfig.islandTime()) {
            float back = 1.0f - k;
            UiRestFrame.shift(graphics, (pillLeft - cardLeft) * back, (PILL_TIME_TOP - CARD_TIME_TOP) * back);
            try {
                timing.draw(graphics, font, cardLeft, y + CARD_TIME_TOP, TIME_SCALE,
                        UiTheme.alpha(inked(), fade));
            } finally {
                UiRestFrame.pop(graphics);
            }
            timed = barShift.get() + PILL_TIME_GAP;
        }
        if (!HudConfig.islandBar()) return;

        float left = lerp(pillLeft + timed, cardLeft, k);
        float span = lerp(pillX + frame.pillWidth - PAD - pillLeft - timed,
                cardX + frame.cardWidth - PAD - cardLeft, k);
        flyBar(graphics, left, span, y, k, fade);
    }

    private static void flyBar(GuiGraphics graphics, float left, float span, float y, float k, float fade) {
        if (span <= 4.0f) return;

        float thick = lerp(HAIRLINE, BAR_HEIGHT, k);
        float top = lerp(y + PILL_BAR_CENTER, y + CARD_BAR_TOP + BAR_HEIGHT / 2.0f, k) - thick / 2.0f;
        float value = IslandProgress.value();
        float hair = fade * (1.0f - k);
        strip(graphics, left, top, span, thick, UiTheme.withAlpha(UiTheme.WHITE, 0.16f * hair));
        strip(graphics, left, top, span * value, thick, UiTheme.alpha(UiAccent.color(), hair));
        float glass = fade * k;
        UiGlass.sunken(graphics, left, top, span, thick, thick / 2.0f, glass * 0.9f);
        UiGlass.progress(graphics, left, top, span, thick, value, UiTheme.alpha(UiAccent.color(), glass), glass);
        drawSeekHead(graphics, left + span * value, top + thick / 2.0f,
                thick * lerp(SEEK_HEAD_PILL, SEEK_HEAD_CARD, k), fade);
    }

    private static void flyBars(GuiGraphics graphics, float pillX, float cardX, float y, float k, float fade) {
        float pillY = y + lerp(PILL_ROW_CENTER, PILL_HEIGHT / 2.0f, frame.blind);
        IslandGlyph.visualizer(graphics,
                lerp(pillX + frame.pillWidth - PAD - IslandGlyph.PILL_WIDTH / 2.0f,
                        cardX + frame.cardWidth - PAD - IslandGlyph.CARD_WIDTH / 2.0f, k),
                lerp(pillY, y + WAVE_CARD_CENTER, k),
                lerp(IslandGlyph.PILL_WIDTH, IslandGlyph.CARD_WIDTH, k),
                lerp(IslandGlyph.PILL_HEIGHT, IslandGlyph.CARD_HEIGHT, k), fade);
    }

    // WHY: пока строка меняется по буквам, она рисуется обеими раскладками на месте, без бегущей
    // WHY: строки: смена длится доли секунды, а ход бегущей строки начинается после паузы
    private static void morphLine(GuiGraphics graphics, Font font, UiMorphText text, float x, float y,
                                  float slot, float scale, int color) {
        morphLine(graphics, font, text, x, y, slot, scale, color, UiSweep.NONE);
    }

    // WHY: строка лирики длиннее слота едет за волной подсветки, обычная строка своей бегущей строкой
    private static void morphLine(GuiGraphics graphics, Font font, UiMorphText text, float x, float y,
                                  float slot, float scale, int color, UiSweep sweep) {
        if ((color >>> 24) < 3) return;

        float span = text.measure(graphics, font, scale);
        if (text.morphing()) {
            morphing(graphics, font, text, x, y, slot, scale, color, sweep, span);
            return;
        }
        if (span <= slot) {
            text.scrolled(0.0f);
            text.draw(graphics, font, x, y, scale, color, sweep);
            return;
        }
        float shift = sweep == UiSweep.NONE ? UiRender.marqueeShift(graphics, text.raw(), span - slot)
                : text.follow(graphics, font, scale, slot, sweep);
        float start = x - shift;
        text.scrolled(shift);
        UiRender.marqueeFrom(graphics, x, slot, start, span, scale);
        try {
            text.draw(graphics, font, start, y, scale, color, sweep);
        } finally {
            UiRender.marqueeDone();
        }
    }

    // WHY: строка посреди смены гаснет у краёв слота так же, как бегущая: уехавшие за край буквы
    // WHY: прокрученной строки иначе проступали на время морфа
    private static void morphing(GuiGraphics graphics, Font font, UiMorphText text, float x, float y, float slot,
                                 float scale, int color, UiSweep sweep, float span) {
        float leaving = text.leaving();
        if (span <= slot && leaving <= 0.0f) {
            text.draw(graphics, font, x, y, scale, color, sweep);
            return;
        }
        UiRender.marqueeFrom(graphics, x, slot, x - leaving, Math.max(span, slot) + leaving, scale);
        try {
            text.draw(graphics, font, x, y, scale, color, sweep);
        } finally {
            UiRender.marqueeDone();
        }
    }

    private static void advanceText(float delta) {
        barShift.to(timingPeak, delta);
        title.advance(delta);
        artist.advance(delta);
        timing.advance(delta);
        pillRow.advance(delta);
    }

    private static void drawCardBar(GuiGraphics graphics, Font font, MediaTrack track, float x, float y,
                                    float alpha) {
        float fade = alpha * (1.0f - frame.blind);
        if (fade <= 0.02f || !track.present()) return;

        float barX = x + PAD + frame.art + GAP;
        float span = x + frame.cardWidth - PAD - barX;
        if (span <= 8.0f) return;

        if (HudConfig.islandBar() && !untimed()) {
            float value = IslandProgress.value();
            UiGlass.sunken(graphics, barX, y + CARD_BAR_TOP, span, BAR_HEIGHT, BAR_HEIGHT / 2.0f, fade * 0.9f);
            UiGlass.progress(graphics, barX, y + CARD_BAR_TOP, span, BAR_HEIGHT, value,
                    UiTheme.alpha(UiAccent.color(), fade), fade);
            drawSeekHead(graphics, barX + span * value, y + CARD_BAR_TOP + BAR_HEIGHT / 2.0f,
                    BAR_HEIGHT * SEEK_HEAD_CARD, fade);
        }
        if (!HudConfig.islandTime()) return;

        timing.draw(graphics, font, barX, y + CARD_TIME_TOP, TIME_SCALE, UiTheme.alpha(inked(), fade));
    }

    // WHY: голова живёт только на перемотке: на обычном ходу она стояла бы на кромке заливки всегда
    // WHY: и читалась бы как лишняя точка в полосе
    private static void drawSeekHead(GuiGraphics graphics, float centerX, float centerY,
                                     float radius, float fade) {
        float surge = IslandProgress.surge();
        if (surge <= 0.02f) return;

        UiRender.dot(graphics, centerX, centerY, radius * (0.6f + 0.4f * surge),
                UiTheme.alpha(UiTheme.mix(UiAccent.color(), UiTheme.WHITE, SEEK_HEAD_LIT), fade * surge));
    }

    // WHY: полоски у таблетки стоят на строке названия, у трека без управления по центру: высота и
    // WHY: число полос общие с островом рабочего стола (IslandGlyph), визуализатор у них один
    private static void drawPillBars(GuiGraphics graphics, float x, float y, float alpha) {
        float fade = alpha * frame.media;
        if (fade <= 0.02f || frame.waveSlot <= 0.0f) return;

        IslandGlyph.pillBars(graphics, x + frame.pillWidth - PAD - IslandGlyph.PILL_WIDTH / 2.0f,
                y + lerp(PILL_ROW_CENTER, PILL_HEIGHT / 2.0f, frame.blind), fade);
    }

    // WHY: счётчик один и тот же в обоих состояниях, он переезжает, а не гаснет и зажигается заново:
    // WHY: без музыки живёт внутри острова справа, с музыкой съезжает под него в свою капсулу.
    // WHY: вниз уходит с перелётом, влево доезжает без него, поэтому он выпадает, а потом встаёт на место
    private static void drawCounter(GuiGraphics graphics, Font font, float x, float y, float alpha) {
        if (frame.capsule <= 0.0f || alpha <= 0.02f) return;

        float statsX = lerp(x + frame.width - PAD - frame.stats, x + CAPSULE_PAD,
                UiAnim.easeOut(frame.media));
        float centerY = lerp(y + frame.height / 2.0f,
                y + frame.height + CAPSULE_GAP + PILL_HEIGHT / 2.0f, frame.drop);
        if (frame.media > 0.02f) {
            UiVital.card(graphics, statsX - CAPSULE_PAD, centerY - PILL_HEIGHT / 2.0f,
                    frame.capsule, PILL_HEIGHT, Math.min(PILL_HEIGHT / 2.0f, MAX_RADIUS),
                    alpha * frame.media);
        }
        drawStats(graphics, font, statsX, centerY, alpha);
    }

    private static void drawStats(GuiGraphics graphics, Font font, float x, float centerY, float alpha) {
        float unitY = UiRender.centerY(centerY - STAT_BAND, STAT_BAND * 2.0f, UNIT_SCALE);
        float valueY = UiRender.centerY(centerY - STAT_BAND, STAT_BAND * 2.0f, STAT_SCALE);
        int unitInk = UiTheme.alpha(inked(), alpha);

        float cursor = x;
        if (HudConfig.islandFps()) {
            cursor += unit(graphics, font, FPS_UNIT, cursor, unitY, unitInk);
            cursor += value(graphics, font, IslandModel.frames(), cursor, valueY,
                    UiTheme.alpha(inked(), alpha)) + PAIR_GAP;
        }
        if (!HudConfig.islandPing()) return;

        cursor += unit(graphics, font, PING_UNIT, cursor, unitY, unitInk);
        value(graphics, font, IslandModel.latency(), cursor, valueY, UiTheme.alpha(pingTone(), alpha));
    }

    private static float unit(GuiGraphics graphics, Font font, Component label, float x, float y, int color) {
        UiRender.labelScaled(graphics, font, label, x, y, UNIT_SCALE, color);
        return UiRender.measure(graphics, font, label, UNIT_SCALE) + UNIT_GAP;
    }

    private static float value(GuiGraphics graphics, Font font, Component number, float x, float y, int color) {
        UiRender.labelScaled(graphics, font, number, x, y, STAT_SCALE, color);
        return UiRender.measure(graphics, font, number, STAT_SCALE);
    }

    private static float statsWidth(GuiGraphics graphics, Font font) {
        float span = 0.0f;
        if (HudConfig.islandFps()) {
            span += UiRender.measure(graphics, font, FPS_UNIT, UNIT_SCALE) + UNIT_GAP
                    + UiRender.measure(graphics, font, IslandModel.frames(), STAT_SCALE) + PAIR_GAP;
        }
        if (!HudConfig.islandPing()) return Math.max(0.0f, span - PAIR_GAP);

        return span + UiRender.measure(graphics, font, PING_UNIT, UNIT_SCALE) + UNIT_GAP
                + UiRender.measure(graphics, font, IslandModel.latency(), STAT_SCALE);
    }

    private static int inked() {
        return HudLayout.tint(HudSlot.ISLAND, ink());
    }

    private static int ink() {
        return HudInk.text();
    }

    private static int pingTone() {
        if (HudLayout.of(HudSlot.ISLAND).tint() != null) return inked();

        float quality = IslandModel.quality();
        if (quality >= 0.66f) return ink();
        return quality >= 0.33f ? 0xFFE0A33C : 0xFFD9503F;
    }

    private static float lerp(float from, float to, float weight) {
        return from + (to - from) * weight;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    private static void refresh(MediaTrack track) {
        if (!track.sameTrack(shownTrack)) retitle(track);

        long now = System.currentTimeMillis();
        sing(lyrics.line(track, now));
        widened = lyrics.widen(UiFrame.delta());
        stampTiming(Math.max(0L, IslandClock.elapsedMs(track, now) / 1000L),
                Math.max(0L, track.durationMs() / 1000L));
    }

    // WHY: у слепого трека название есть, только если плеер пишет его в заголовок окна, иначе в
    // WHY: строке стоит площадка или плеер, с которого шёл звук. Название и исполнитель приходят со
    // WHY: страницы: чистятся от управляющих символов, знака параграфа и залго и режутся по длине,
    // WHY: иначе строка из тысяч невидимых знаков рисовалась бы тысячами глифов за кадр
    private static void retitle(MediaTrack track) {
        if (IslandModel.media() > 0.02f && shownTrack.present()) {
            morph.pulse();
        }
        shownTrack = track;
        shownPlayed = -1L;
        String cleanTitle = LyricText.clean(track.title(), FIELD_LIMIT);
        String cleanArtist = LyricText.clean(track.artist(), FIELD_LIMIT);
        titleText = track.blind() && cleanTitle.isEmpty() ? track.origin() : cleanTitle;
        artistText = cleanArtist.isEmpty() ? track.origin() : cleanArtist;
        titleValue = Component.literal(titleText);
        pairedArtist = titleText.isEmpty() ? artistText : artistText + " \u00b7 " + titleText;
    }

    // WHY: пока поётся строка, она стоит на месте названия, а название уходит к исполнителю в
    // WHY: карточке: так видно и что поют, и что играет
    private static void sing(LyricLine line) {
        if (line == null) {
            title.set(titleText);
            artist.set(artistText);
            return;
        }
        title.set(line.text(), IslandLyrics.pace(line));
        artist.set(pairedArtist);
    }

    // WHY: у трека без длины полосе нечего показывать, и строка таблетки отдаётся исполнителю
    // WHY: с отсчётом: в таблетке больше негде увидеть, кто играет
    private static void stampTiming(long played, long whole) {
        if (played == shownPlayed && whole == shownWhole) return;

        shownPlayed = played;
        shownWhole = whole;
        if (whole > 0L) {
            timing.set(clock(played) + " / " + clock(whole));
            pillRow.set(clock(played) + " / " + clock(whole));
            return;
        }
        timing.set(clock(played));
        pillRow.set(artistText.isEmpty() ? clock(played) : artistText + " · " + clock(played));
    }

    private static boolean untimed() {
        return shownTrack.durationMs() <= 0L;
    }

    private static String clock(long seconds) {
        return seconds / 60L + (seconds % 60L < 10L ? ":0" : ":") + seconds % 60L;
    }

    private static final class Frame {
        private float media;
        private float blind;
        private float shape;
        private float pill;
        private float card;
        private float width;
        private float steady;
        private float height;
        private float pillWidth;
        private float cardWidth;
        private float face;
        private float art;
        private float stats;
        private float waveSlot;
        private float cardWaveSlot;
        private float capsule;
        private float drop;
        private float tail;
    }
}
