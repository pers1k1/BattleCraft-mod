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
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
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
    private static final float WAVE_WIDTH = 11.0f;
    private static final float WAVE_HEIGHT = 9.5f;
    private static final float WAVE_GAP = 6.0f;
    private static final float WAVE_CARD_WIDTH = 14.0f;
    private static final float WAVE_CARD_HEIGHT = 13.0f;
    private static final float WAVE_CARD_CENTER = 15.5f;
    private static final float CAPSULE_GAP = 4.0f;
    private static final float CAPSULE_PAD = 6.0f;
    private static final float MAX_RADIUS = 13.0f;

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
    private static final float UNIT_GAP = 2.5f;
    private static final float PAIR_GAP = 7.0f;

    private static final float MARQUEE_SPEED = 16.0f;
    private static final float MARQUEE_DWELL = 1.6f;

    private static final Component FPS_UNIT = Component.literal("FPS");
    private static final Component PING_UNIT = Component.literal("Ping");

    private static final IslandText title = new IslandText();
    private static final IslandText artist = new IslandText();
    private static final IslandText timing = new IslandText();
    private static final Frame frame = new Frame();

    private static MediaTrack shownTrack = MediaTrack.NONE;
    private static long shownPlayed = -1L;
    private static long shownWhole = -1L;

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
        IslandModel.advance(UiFrame.delta());

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
        refresh(track);
        measure(graphics, font);
        IslandGlyph.pulse(IslandModel.energy());
    }

    private static void measure(GuiGraphics graphics, Font font) {
        frame.media = IslandModel.media();
        frame.blind = IslandModel.blind() * frame.media;
        frame.expand = IslandModel.expand() * frame.media;
        frame.height = lerp(PILL_HEIGHT, CARD_HEIGHT, frame.expand);
        frame.avatar = HudConfig.islandAvatar() || showsArt() ? lerp(FACE, ART, frame.expand) : 0.0f;
        frame.stats = statsWidth(graphics, font);
        frame.wave = HudConfig.islandVisualizer() ? lerp(WAVE_WIDTH, WAVE_CARD_WIDTH, frame.expand) : 0.0f;
        frame.waveSlot = frame.wave > 0.0f ? frame.wave + WAVE_GAP : 0.0f;

        // WHY: остров тянется за названием, но верхний предел держит его подальше от «Счёта и точек»
        // WHY: в центре экрана: всё, что длиннее слота, уезжает бегущей строкой
        float pillWave = HudConfig.islandVisualizer() ? WAVE_GAP + WAVE_WIDTH : 0.0f;
        float cardWave = HudConfig.islandVisualizer() ? WAVE_GAP + WAVE_CARD_WIDTH : 0.0f;
        float pillTitle = PAD + FACE + GAP + clamp(UiRender.measure(graphics, font, title.value(),
                TITLE_PILL_SCALE), TITLE_MIN, TITLE_MAX) + pillWave + PAD;
        float pillTiming = PAD + FACE + GAP + UiRender.measure(graphics, font, timing.value(), TIME_SCALE)
                + PILL_TIME_GAP + PILL_BAR_MIN + PAD;
        float pill = Math.max(pillTitle, pillTiming * (1.0f - frame.blind));
        float card = PAD + ART + GAP + clamp(Math.max(
                UiRender.measure(graphics, font, title.value(), TITLE_CARD_SCALE),
                UiRender.measure(graphics, font, artist.value(), ARTIST_SCALE)),
                CARD_TEXT_MIN, CARD_TEXT_MAX) + cardWave + PAD;
        float idle = PAD + FACE + GAP + UiRender.measure(graphics, font, IslandModel.nick(), NICK_SCALE)
                + STAT_INSET + frame.stats + PAD;

        frame.width = lerp(idle, lerp(pill, card, frame.expand), frame.media);
        frame.capsule = frame.stats > 0.0f ? CAPSULE_PAD * 2.0f + frame.stats : 0.0f;
        frame.drop = UiAnim.easeOutBack(frame.media);
        frame.tail = frame.capsule > 0.0f ? frame.drop * (CAPSULE_GAP + PILL_HEIGHT) : 0.0f;
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

        UiRender.clip(graphics, x, y, frame.width, frame.height);
        try {
            drawAvatar(graphics, x, y, alpha);
            drawMainLine(graphics, font, x, y, alpha);
            drawArtist(graphics, font, x, y, alpha);
            drawProgress(graphics, font, track, x, y, alpha);
            drawVisualizer(graphics, x, y, alpha);
        } finally {
            graphics.disableScissor();
        }

        drawCounter(graphics, font, x, y, alpha);
    }

    private static void drawAvatar(GuiGraphics graphics, float x, float y, float alpha) {
        if (frame.avatar <= 0.0f) return;

        IslandFace.draw(graphics, x + PAD + frame.avatar / 2.0f, y + frame.height / 2.0f,
                frame.avatar, alpha, showsArt(), HudConfig.islandAvatar());
    }

    // WHY: обложка живёт в том же месте, что и аватар, поэтому выключенный аватар не должен уносить её с собой
    private static boolean showsArt() {
        return HudConfig.islandCover() && frame.media > 0.02f && frame.blind < 0.5f && IslandArt.ready();
    }

    private static void drawMainLine(GuiGraphics graphics, Font font, float x, float y, float alpha) {
        float scale = lerp(NICK_SCALE, lerp(TITLE_PILL_SCALE, TITLE_CARD_SCALE, frame.expand), frame.media);
        float textX = x + PAD + frame.avatar + GAP;
        float reserve = lerp(frame.stats + STAT_INSET, frame.waveSlot, frame.media);
        float slot = x + frame.width - PAD - reserve - textX;
        if (slot <= 4.0f) return;

        float resting = lerp(UiRender.centerY(y, frame.height, scale), y + PILL_TITLE_TOP,
                frame.media * (1.0f - frame.blind));
        float top = lerp(resting, y + CARD_TITLE_TOP, frame.expand);
        UiRender.clip(graphics, textX, y, slot, frame.height);
        try {
            if (HudConfig.islandNick()) {
                line(graphics, font, IslandModel.nick(), textX, top, slot, scale,
                        UiTheme.alpha(inked(), alpha * (1.0f - frame.media)));
            }
            if (HudConfig.islandTitle()) {
                line(graphics, font, title.value(), textX, top, slot, scale,
                        UiTheme.alpha(inked(), alpha * frame.media));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static void line(GuiGraphics graphics, Font font, Component value, float x, float y,
                             float slot, float scale, int color) {
        if ((color >>> 24) < 3) return;

        float span = UiRender.measure(graphics, font, value, scale);
        UiRender.labelScaled(graphics, font, value, x + marquee(slot, span), y, scale, color);
    }

    private static void drawArtist(GuiGraphics graphics, Font font, float x, float y, float alpha) {
        float fade = alpha * frame.expand;
        if (fade <= 0.02f || !HudConfig.islandArtist()) return;

        float textX = x + PAD + frame.avatar + GAP;
        float slot = x + frame.width - PAD - frame.waveSlot - textX;
        if (slot <= 4.0f) return;

        UiRender.clip(graphics, textX, y, slot, frame.height);
        try {
            line(graphics, font, artist.value(), textX, y + CARD_ARTIST_TOP, slot, ARTIST_SCALE,
                    UiTheme.alpha(inked(), fade));
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawProgress(GuiGraphics graphics, Font font, MediaTrack track, float x, float y, float alpha) {
        float known = alpha * (1.0f - frame.blind);
        if (frame.media <= 0.02f || known <= 0.02f || !track.present()) return;

        float value = IslandProgress.value();
        drawHairline(graphics, font, x, y, value, known * frame.media * (1.0f - frame.expand));
        drawCardBar(graphics, font, x, y, value, known * frame.expand);
    }

    private static void drawHairline(GuiGraphics graphics, Font font, float x, float y, float value, float fade) {
        if (fade <= 0.02f) return;

        float left = x + PAD + frame.avatar + GAP;
        float timed = 0.0f;
        if (HudConfig.islandTime()) {
            UiRender.labelScaled(graphics, font, timing.value(), left, y + PILL_TIME_TOP, TIME_SCALE,
                    UiTheme.alpha(inked(), fade));
            timed = UiRender.measure(graphics, font, timing.value(), TIME_SCALE) + PILL_TIME_GAP;
        }
        if (!HudConfig.islandBar()) return;

        float barLeft = left + timed;
        float span = x + frame.width - PAD - barLeft;
        if (span <= 4.0f) return;

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

    private static void drawCardBar(GuiGraphics graphics, Font font, float x, float y, float value, float fade) {
        if (fade <= 0.02f) return;

        float barX = x + PAD + frame.avatar + GAP;
        float span = x + frame.width - PAD - barX;
        if (span <= 8.0f) return;

        if (HudConfig.islandBar()) {
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

    // WHY: полоски не гаснут на раскрытии, а переезжают: место под них держат оба вида, поэтому
    // WHY: визуализатор один и тот же и на карточке, и на таблетке, и на смене вида не начинается заново
    private static void drawVisualizer(GuiGraphics graphics, float x, float y, float alpha) {
        float fade = alpha * frame.media;
        if (fade <= 0.02f || frame.wave <= 0.0f) return;

        IslandGlyph.visualizer(graphics, x + frame.width - PAD - frame.wave / 2.0f,
                y + lerp(lerp(PILL_ROW_CENTER, frame.height / 2.0f, frame.blind),
                        WAVE_CARD_CENTER, frame.expand), frame.wave,
                lerp(WAVE_HEIGHT, WAVE_CARD_HEIGHT, frame.expand), fade);
    }

    private static void drawStats(GuiGraphics graphics, Font font, float x, float centerY, float alpha) {
        float unitY = UiRender.centerY(centerY - WAVE_HEIGHT, WAVE_HEIGHT * 2.0f, UNIT_SCALE);
        float valueY = UiRender.centerY(centerY - WAVE_HEIGHT, WAVE_HEIGHT * 2.0f, STAT_SCALE);
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

    private static float marquee(float slot, float span) {
        float overflow = span - slot;
        if (overflow <= 0.5f) return 0.0f;

        float travel = overflow / MARQUEE_SPEED;
        float period = (travel + MARQUEE_DWELL) * 2.0f;
        float phase = System.currentTimeMillis() % (long) (period * 1000.0f) / 1000.0f;

        if (phase < MARQUEE_DWELL) return 0.0f;
        if (phase < MARQUEE_DWELL + travel) {
            return -overflow * UiAnim.smoothstep(0.0f, travel, phase - MARQUEE_DWELL);
        }
        if (phase < MARQUEE_DWELL * 2.0f + travel) return -overflow;
        return -overflow * (1.0f - UiAnim.smoothstep(0.0f, travel, phase - MARQUEE_DWELL * 2.0f - travel));
    }

    private static float lerp(float from, float to, float weight) {
        return from + (to - from) * weight;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    // WHY: у слепого трека названия нет, вместо него в строке стоит площадка, с которой шёл звук
    private static void refresh(MediaTrack track) {
        if (!track.sameTrack(shownTrack)) {
            shownTrack = track;
            title.set(track.blind() ? track.origin() : track.title());
            artist.set(track.artist().isEmpty() ? track.origin() : track.artist());
        }

        stampTiming(Math.max(0L, track.elapsedMs(System.currentTimeMillis()) / 1000L),
                Math.max(0L, track.durationMs() / 1000L));
    }

    private static void stampTiming(long played, long whole) {
        if (played == shownPlayed && whole == shownWhole) return;

        shownPlayed = played;
        shownWhole = whole;
        timing.set(clock(played) + " / " + clock(whole));
    }

    private static String clock(long seconds) {
        return seconds / 60L + (seconds % 60L < 10L ? ":0" : ":") + seconds % 60L;
    }

    private static final class Frame {
        private float media;
        private float blind;
        private float expand;
        private float width;
        private float height;
        private float avatar;
        private float stats;
        private float wave;
        private float waveSlot;
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
