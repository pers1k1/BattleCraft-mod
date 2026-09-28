package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMarquee;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVeil;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

// WHY: таблетка и карточка раскладываются каждая в своём покое, а между ними идёт общий с рабочим
// WHY: столом морф через размытие (IslandMorph): старое содержимое уходит в размытие, форма
// WHY: меняется пустой, новое приходит из размытия. Ни одна строка не ведётся лерпом координат
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
    private static final float EMPTY_WIDTH = 40.0f;
    private static final float PILL_DRIFT = 1.5f;
    private static final float CARD_DRIFT = -3.0f;

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
    private static final float PILL_BAR_MIN = 28.0f;
    private static final float SEEK_HEAD_PILL = 1.6f;
    private static final float SEEK_HEAD_CARD = 1.15f;
    private static final float SEEK_HEAD_LIT = 0.55f;

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

    private static final IslandText title = new IslandText();
    private static final IslandText artist = new IslandText();
    private static final IslandText timing = new IslandText();
    private static final IslandText pillRow = new IslandText();
    private static final Frame frame = new Frame();
    private static final IslandMorph morph = new IslandMorph();

    private static MediaTrack shownTrack = MediaTrack.NONE;
    private static long shownPlayed = -1L;
    private static long shownWhole = -1L;
    private static long morphedFrame = -1L;

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
        prepare(graphics, font, track);

        HudBox box = HudLayout.place(HudSlot.ISLAND, Math.max(frame.width, frame.capsule),
                frame.height + frame.tail, screenWidth, screenHeight);
        HudLayout.push(graphics, box);
        try {
            paint(graphics, font, track, box.x(), box.y(), box.alpha());
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void prepare(GuiGraphics graphics, Font font, MediaTrack track) {
        IslandGlyph.drive();
        refresh(track);
        advanceMorph();
        measure(graphics, font);
    }

    // WHY: превью в редакторе HUD и сам HUD могут звать остров в одном кадре, а морф идёт по времени
    // WHY: и шагнул бы дважды: защёлка по кадру, как у модели. Вне музыки морф стоит свёрнутым
    private static void advanceMorph() {
        long now = UiFrame.frame();
        if (now == morphedFrame) return;
        morphedFrame = now;
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
        frame.height = lerp(PILL_HEIGHT, CARD_HEIGHT, frame.shape);
        frame.face = HudConfig.islandAvatar() || showsArt() ? FACE : 0.0f;
        frame.art = HudConfig.islandAvatar() || showsArt() ? ART : 0.0f;
        frame.stats = statsWidth(graphics, font);
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
        float pillTitle = PAD + FACE + GAP + clamp(UiRender.measure(graphics, font, title.value(),
                TITLE_PILL_SCALE), TITLE_MIN, TITLE_MAX) + frame.waveSlot + PAD;
        float pillTiming = PAD + FACE + GAP + UiRender.measure(graphics, font, pillRow.value(), TIME_SCALE)
                + (untimed() ? 0.0f : PILL_TIME_GAP + PILL_BAR_MIN) + PAD;
        float pill = Math.max(pillTitle, pillTiming * (1.0f - frame.blind));
        float idle = PAD + FACE + GAP + UiRender.measure(graphics, font, IslandModel.nick(), NICK_SCALE)
                + STAT_INSET + frame.stats + PAD;
        frame.cardWidth = PAD + ART + GAP + clamp(Math.max(
                UiRender.measure(graphics, font, title.value(), TITLE_CARD_SCALE),
                UiRender.measure(graphics, font, artist.value(), ARTIST_SCALE)),
                CARD_TEXT_MIN, CARD_TEXT_MAX) + frame.cardWaveSlot + PAD;
        frame.pillWidth = lerp(idle, pill, frame.media);
        float collapsed = lerp(frame.pillWidth, Math.min(EMPTY_WIDTH, frame.pillWidth),
                morph.squeeze() * frame.media);
        frame.width = lerp(collapsed, frame.cardWidth, frame.shape);
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Font font = Minecraft.getInstance().font;
        MediaTrack track = IslandModel.track();
        prepare(graphics, font, track);
        HudLayout.sample(HudSlot.ISLAND, Math.max(frame.width, frame.capsule), frame.height + frame.tail);
        paint(graphics, font, track, box.x(), box.y(), alpha);
    }

    private static void paint(GuiGraphics graphics, Font font, MediaTrack track, float x, float y, float alpha) {
        float radius = Math.min(frame.height / 2.0f, MAX_RADIUS);
        UiVital.card(graphics, x, y, frame.width, frame.height, radius, alpha);

        boolean outer = UiRender.floating(true);
        UiRender.floating(outer || !morph.resting());
        try {
            paintContent(graphics, font, track, x, y, radius, alpha);
        } finally {
            UiRender.floating(outer);
        }
        drawCounter(graphics, font, x, y, alpha);
    }

    // WHY: в покое содержимое рисуется прямо в кадр; офскрин размытия живёт только на время морфа
    private static void paintContent(GuiGraphics graphics, Font font, MediaTrack track, float x, float y,
                                     float radius, float alpha) {
        boolean veiled = !morph.resting() && frame.media > 0.02f && UiVeil.begin(graphics);
        if (!veiled) UiRender.clip(graphics, x, y, frame.width, frame.height);
        try {
            drawPill(graphics, font, track, x + (frame.width - frame.pillWidth) / 2.0f, y, alpha * frame.pill);
            drawCard(graphics, font, track, x + (frame.width - frame.cardWidth) / 2.0f, y, alpha * frame.card);
        } finally {
            if (veiled) {
                UiVeil.end(graphics, x, y, frame.width, frame.height, radius, morph.blur() * IslandMorph.BLUR, 1.0f);
            } else {
                graphics.disableScissor();
            }
        }
        if (morph.resting()) UiVeil.tidy();
    }

    private static void drawPill(GuiGraphics graphics, Font font, MediaTrack track, float x, float y, float alpha) {
        if (alpha <= 0.02f) return;

        IslandMorph.pose(graphics, x + frame.pillWidth / 2.0f, y + PILL_HEIGHT / 2.0f, frame.pill, PILL_DRIFT);
        try {
            if (frame.face > 0.0f) {
                IslandFace.draw(graphics, x + PAD + FACE / 2.0f, y + PILL_HEIGHT / 2.0f, FACE, alpha, showsArt(),
                        HudConfig.islandAvatar());
            }
            drawMainLine(graphics, font, x, y, alpha);
            drawHairline(graphics, font, track, x, y, alpha);
            drawPillBars(graphics, x, y, alpha);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private static void drawCard(GuiGraphics graphics, Font font, MediaTrack track, float x, float y, float alpha) {
        if (alpha <= 0.02f) return;

        IslandMorph.pose(graphics, x + frame.cardWidth / 2.0f, y + CARD_HEIGHT / 2.0f, frame.card, CARD_DRIFT);
        try {
            if (frame.art > 0.0f) {
                IslandFace.draw(graphics, x + PAD + ART / 2.0f, y + CARD_HEIGHT / 2.0f, ART, alpha, showsArt(),
                        HudConfig.islandAvatar());
            }
            drawCardText(graphics, font, x, y, alpha);
            drawCardBar(graphics, font, track, x, y, alpha);
            if (frame.cardWaveSlot > 0.0f) {
                IslandGlyph.cardBars(graphics, x + frame.cardWidth - PAD - IslandGlyph.CARD_WIDTH / 2.0f,
                        y + WAVE_CARD_CENTER, alpha);
            }
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    // WHY: обложка живёт в том же месте, что и аватар, поэтому выключенный аватар не должен уносить её с собой
    private static boolean showsArt() {
        return HudConfig.islandCover() && frame.media > 0.02f && frame.blind < 0.5f && IslandArt.ready();
    }

    // WHY: ник и название стоят каждый в своём покое, своим кеглем. Переход между ними заявляется
    // WHY: позой покоя: уходящая строка едет и растёт к месту приходящей, а та приходит из места
    // WHY: уходящей, поэтому буквы движутся непрерывно, а не ступенями по пикселю
    private static void drawMainLine(GuiGraphics graphics, Font font, float x, float y, float alpha) {
        float textX = x + PAD + frame.face + GAP;
        float reserve = lerp(frame.stats + STAT_INSET, frame.waveSlot, frame.media);
        float slot = x + frame.pillWidth - PAD - reserve - textX;
        if (slot <= 4.0f) return;

        float margin = UiMarquee.margin(slot, TITLE_PILL_SCALE);
        UiRender.clip(graphics, textX - margin, y, slot + margin * 2.0f, PILL_HEIGHT);
        try {
            if (HudConfig.islandNick()) drawNick(graphics, font, textX, y, slot, alpha * (1.0f - frame.media));
            if (HudConfig.islandTitle()) drawTitle(graphics, font, textX, y, slot, alpha * frame.media);
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
            line(graphics, font, title.value(), title.raw, textX, titleTop, slot, TITLE_PILL_SCALE,
                    UiTheme.alpha(inked(), alpha));
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private static float titleLive(float y) {
        return lerp(y + PILL_TITLE_TOP, UiRender.centerY(y, PILL_HEIGHT, TITLE_PILL_SCALE), frame.blind);
    }

    private static void drawCardText(GuiGraphics graphics, Font font, float x, float y, float alpha) {
        float textX = x + PAD + frame.art + GAP;
        float slot = x + frame.cardWidth - PAD - frame.cardWaveSlot - textX;
        if (slot <= 4.0f) return;

        float margin = UiMarquee.margin(slot, TITLE_CARD_SCALE);
        UiRender.clip(graphics, textX - margin, y, slot + margin * 2.0f, CARD_HEIGHT);
        try {
            if (HudConfig.islandTitle()) {
                line(graphics, font, title.value(), title.raw, textX, y + CARD_TITLE_TOP, slot, TITLE_CARD_SCALE,
                        UiTheme.alpha(inked(), alpha));
            }
            if (HudConfig.islandArtist()) {
                line(graphics, font, artist.value(), artist.raw, textX, y + CARD_ARTIST_TOP, slot, ARTIST_SCALE,
                        UiTheme.alpha(inked(), alpha));
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
            UiRender.labelScaled(graphics, font, pillRow.value(), left, y + PILL_TIME_TOP, TIME_SCALE,
                    UiTheme.alpha(inked(), fade));
            timed = UiRender.measure(graphics, font, pillRow.value(), TIME_SCALE) + PILL_TIME_GAP;
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
        if (span <= 0.0f) return;

        if (UiCrisp.ready()) {
            UiCrisp.panel(graphics, x, y, span, HAIRLINE, HAIRLINE / 2.0f, color);
            return;
        }
        UiRender.panel(graphics, x, y, span, HAIRLINE, HAIRLINE / 2.0f, color);
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

        UiRender.labelScaled(graphics, font, timing.value(), barX, y + CARD_TIME_TOP, TIME_SCALE,
                UiTheme.alpha(inked(), fade));
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

        stampTiming(Math.max(0L, IslandClock.elapsedMs(track, System.currentTimeMillis()) / 1000L),
                Math.max(0L, track.durationMs() / 1000L));
    }

    // WHY: новое название встаёт, только когда старое уже ушло в размытие: на раскрытии это середина
    // WHY: морфа, а если форма не меняется (карточка уже открыта или трек без управления), остров
    // WHY: сам проходит смену через размытие. У слепого трека название есть, только если плеер
    // WHY: пишет его в заголовок окна, иначе в строке стоит площадка или плеер, с которого шёл звук
    private static void retitle(MediaTrack track) {
        if (IslandModel.media() > 0.02f && !morph.hollow()) {
            if (morph.resting() && IslandModel.carded() == morph.opened()) morph.swap();
            return;
        }
        shownTrack = track;
        shownPlayed = -1L;
        title.set(track.blind() && track.title().isEmpty() ? track.origin() : track.title());
        artist.set(track.artist().isEmpty() ? track.origin() : track.artist());
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
        pillRow.set(artist.raw.isEmpty() ? clock(played) : artist.raw + " · " + clock(played));
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

    private static final class IslandText {
        private String raw = "";
        private Component value = Component.empty();

        private void set(String next) {
            if (raw.equals(next)) return;

            raw = next;
            value = Component.literal(next);
        }

        private Component value() {
            return value;
        }
    }
}
