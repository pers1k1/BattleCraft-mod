package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.island.IslandArt;
import com.persiki84.battlecraft.client.island.IslandFace;
import com.persiki84.battlecraft.client.island.IslandGlyph;
import com.persiki84.battlecraft.client.island.IslandImage;
import com.persiki84.battlecraft.client.island.IslandMorph;
import com.persiki84.battlecraft.client.island.TrackProgress;
import com.persiki84.battlecraft.client.media.MediaControl;
import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiMarquee;
import com.persiki84.shared.client.ui.UiMorphText;
import com.persiki84.shared.client.ui.UiSweep;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVeil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

// WHY: остров рабочего стола повторяет остров HUD, но залит чёрным, как вырез экрана: стекло
// WHY: цвета акцента на пёстрых обоях читалось бы пятном. Свёрнутый он стоит в строке меню на
// WHY: уровне её пунктов, раскрытый опускается карточкой от неё. Морф и визуализатор общие с HUD.
// WHY: Без трека остров уходит, а не висит заглушкой, а трек на паузе он держит
public final class DesktopIsland {
    private static final float SHOW_RESPONSE = 0.42f;
    private static final float SHOW_DAMPING = 0.9f;
    private static final float WIDTH_RESPONSE = 0.42f;
    private static final float WIDTH_DAMPING = 0.9f;
    private static final float HIDDEN_SCALE = 0.86f;
    private static final float VISIBLE_SHARE = 0.05f;
    private static final float RIM = 0.5f;
    private static final float RIM_ALPHA = 0.11f;
    private static final float RIM_HOVER_ALPHA = 0.22f;
    private static final float HOVER_SPEED = 12.0f;
    private static final float NOTE_SHARE = 0.5f;
    private static final float STILL = 0.001f;
    private static final float FLIGHT_EDGE = 0.002f;
    private static final int BODY = 0xFF000000;
    private static final int TITLE_INK = 0xFFFFFFFF;
    private static final int DIM_INK = 0xFF8E8E93;
    private static final int PLACEHOLDER = 0xFF2C2C2E;

    private final IslandMorph morph = new IslandMorph();
    private final Spring shown = new Spring(SHOW_RESPONSE, SHOW_DAMPING, 0.0f);
    private final Spring pillWidth = new Spring(WIDTH_RESPONSE, WIDTH_DAMPING);
    private final Spring cardWidth = new Spring(WIDTH_RESPONSE, WIDTH_DAMPING);
    private final Smooth hover = new Smooth(0.0f, HOVER_SPEED);
    private final TrackProgress progress = new TrackProgress();
    private final DesktopIslandText text = new DesktopIslandText();
    private final DesktopIslandFrame frame = new DesktopIslandFrame();
    private final DesktopIslandKeys keys = new DesktopIslandKeys();
    private final DesktopIslandBar bar = new DesktopIslandBar();
    private MediaTrack held = MediaTrack.NONE;
    private boolean wanted;
    private boolean bars;
    private long stamp = -1L;
    private float laidPill;
    private float laidCard;
    private boolean moving;
    private boolean pressing;

    public void render(GuiGraphics graphics, Font font, float screenWidth, float room, int mouseX, int mouseY,
                       float alpha) {
        advance(graphics, font, screenWidth, room, mouseX, mouseY);
        float visible = alpha * UiAnim.clamp01(shown.get());
        if (visible <= 0.01f) return;

        float scale = HIDDEN_SCALE + (1.0f - HIDDEN_SCALE) * UiAnim.clamp01(shown.get());
        boolean outer = UiRender.floating(true);
        UiRender.floating(outer || moving || !morph.resting());
        UiRestFrame.push(graphics, frame.centerX, frame.y + frame.height / 2.0f, scale, scale, 0.0f, 0.0f);
        try {
            paint(graphics, font, visible);
        } finally {
            UiRestFrame.pop(graphics);
            UiRender.floating(outer);
        }
    }

    private void advance(GuiGraphics graphics, Font font, float screenWidth, float room, int mouseX, int mouseY) {
        long now = UiFrame.frame();
        if (now == stamp) return;
        stamp = now;

        float delta = UiFrame.delta();
        bars = HudConfig.islandVisualizer();
        MediaTrack live = follow();
        if (text.describe(held, delta, shown.get() > VISIBLE_SHARE)) morph.pulse();
        text.measure(graphics, font);
        progress.advance(held, delta);
        text.time(shownElapsed(), held.durationMs());
        DesktopIslandText.Face face = text.shown();
        pillWidth.to(DesktopIslandFrame.pillWidth(face.pillTitleWidth(), room, bars), delta);
        cardWidth.to(DesktopIslandFrame.cardWidth(face.cardTitleWidth(), face.artistWidth(), screenWidth), delta);
        morph.advance(wanted, delta);
        boolean fits = DesktopIslandFrame.fits(room, bars);
        if (!fits) collapse();
        shown.to(live.present() && fits ? 1.0f : 0.0f, delta);
        lay(screenWidth);
        hover.to(!wanted && frame.inside(mouseX, mouseY) ? 1.0f : 0.0f, delta);
        keys.enable(MediaControl.canPrevious(), MediaControl.canToggle(), MediaControl.canNext());
        keys.advance(frame, mouseX, mouseY, live.playing(), delta);
        bar.advance(frame, mouseX, mouseY, MediaControl.canSeek(), delta);
    }

    private void lay(float screenWidth) {
        float top = (DesktopMenuBar.HEIGHT - DesktopIslandFrame.PILL_HEIGHT) / 2.0f;
        frame.lay(screenWidth / 2.0f, top, morph.width(pillWidth.get(), cardWidth.get()),
                morph.height(DesktopIslandFrame.PILL_HEIGHT, DesktopIslandFrame.CARD_HEIGHT), pillWidth.get(),
                cardWidth.get(), bars);
        frame.layBar(text.timeSlot());
        frame.reveal = morph.card();
        settle();
    }

    // WHY: на смене ширины строки едут вместе с краем таблетки по долям единицы, и привязка пера к
    // WHY: пикселю вела бы их ступенями; в покое перо снова встаёт на сетку ради чёткости
    private void settle() {
        moving = Math.abs(frame.pillLeft - laidPill) > STILL || Math.abs(frame.cardLeft - laidCard) > STILL;
        laidPill = frame.pillLeft;
        laidCard = frame.cardLeft;
    }

    private long shownElapsed() {
        if (bar.dragging()) return (long) (bar.shown(0.0f) * held.durationMs());
        return held.elapsedMs(System.currentTimeMillis());
    }

    // WHY: в меню HUD не рисуется, и модель острова, обложка и визуализатор стоят, пока их не
    // WHY: продвинет тот, кто их показывает. Трек держится и после пропажи: остров уходит с ним
    private MediaTrack follow() {
        IslandGlyph.drive();
        MediaTrack live = MediaWatch.current();
        if (live.present()) {
            held = live;
        } else {
            collapse();
        }
        return live;
    }

    // WHY: окно не размывается никогда: капсула и ободок рисуются резко прямо в кадр, а на ходу в
    // WHY: офскрин уходит только содержимое и ложится обратно размытым, обрезанным по форме окна
    private void paint(GuiGraphics graphics, Font font, float alpha) {
        paintBody(graphics, alpha);
        boolean veiled = morph.veiling() && UiVeil.begin(graphics);
        try {
            paintContent(graphics, font, alpha);
        } finally {
            if (veiled) {
                UiVeil.end(graphics, frame.x, frame.y, frame.width, frame.height, frame.radius,
                        morph.blur() * IslandMorph.BLUR, 1.0f);
            }
        }
        if (!morph.veiling()) UiVeil.tidy();
    }

    // WHY: общие части (обложка, название, полоски) стоят в раскладке, только когда форма дошла до её
    // WHY: края, между краями их ведёт полёт; исполнитель и кнопки идут долей карточки под размытием
    private void paintContent(GuiGraphics graphics, Font font, float alpha) {
        float shape = morph.shape();
        UiRender.clip(graphics, frame.x, frame.y, frame.width, frame.height);
        try {
            if (shape <= FLIGHT_EDGE) paintPill(graphics, font, alpha);
            paintCard(graphics, font, alpha * morph.card(), shape >= 1.0f - FLIGHT_EDGE ? alpha : 0.0f);
            if (shape > FLIGHT_EDGE && shape < 1.0f - FLIGHT_EDGE) paintFlight(graphics, font, shape, alpha);
        } finally {
            graphics.disableScissor();
        }
    }

    // WHY: тонкий светлый ободок отделяет чёрную капсулу от тёмных обоев; под ней он не виден.
    // WHY: Под курсором свёрнутой капсулы он чуть светлеет: это единственный знак, что она нажимается
    private void paintBody(GuiGraphics graphics, float alpha) {
        graphics.flush();
        int rim = UiTheme.withAlpha(UiTheme.WHITE, (RIM_ALPHA + (RIM_HOVER_ALPHA - RIM_ALPHA) * hover.get()) * alpha);
        shape(graphics, frame.x - RIM, frame.y - RIM, frame.width + RIM * 2.0f, frame.height + RIM * 2.0f,
                frame.radius + RIM, rim);
        shape(graphics, frame.x, frame.y, frame.width, frame.height, frame.radius, UiTheme.alpha(BODY, alpha));
    }

    private static void shape(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                              int color) {
        if (UiCrisp.ready()) {
            UiCrisp.panel(graphics, x, y, width, height, radius, color);
            return;
        }
        UiRender.panel(graphics, x, y, width, height, radius, color);
    }

    private void paintPill(GuiGraphics graphics, Font font, float alpha) {
        if (alpha <= 0.01f) return;

        paintCover(graphics, frame.pillCoverX, frame.pillCoverY, frame.faceSize(), alpha);
        pillTitle(graphics, font, text.shown(), alpha);
        if (bars) IslandGlyph.pillBars(graphics, frame.pillWaveX, frame.pillCoverY, alpha);
    }

    private void paintCard(GuiGraphics graphics, Font font, float solo, float shared) {
        if (solo <= 0.01f && shared <= 0.01f) return;

        if (shared > 0.01f) {
            paintCover(graphics, frame.coverX, frame.coverY, frame.coverSize(), shared);
            cardTitle(graphics, font, text.shown(), shared);
            if (bars) IslandGlyph.cardBars(graphics, frame.waveX, frame.coverY, shared);
        }
        cardArtist(graphics, font, text.shown(), solo * morph.card());
        paintControls(graphics, font, solo);
    }

    // WHY: пока обложка ждёт моста, место держит пустая площадка, а нота встаёт только у трека,
    // WHY: у которого обложки нет вовсе: иначе на каждой смене песни нота мелькала бы перед картинкой
    private void paintCover(GuiGraphics graphics, float centerX, float centerY, float size, float alpha) {
        if (!held.blind() && IslandArt.ready()) {
            IslandFace.draw(graphics, centerX, centerY, size, alpha, true, false);
            return;
        }
        UiRender.panel(graphics, centerX - size / 2.0f, centerY - size / 2.0f, size, size,
                size * IslandImage.CORNER_SHARE, UiTheme.alpha(PLACEHOLDER, alpha));
        if (held.blind() || !IslandArt.pending()) {
            UiIcon.draw(graphics, UiIcon.Kind.NOTE, centerX, centerY, size * NOTE_SHARE,
                    UiTheme.alpha(DIM_INK, alpha));
        }
    }

    private void pillTitle(GuiGraphics graphics, Font font, DesktopIslandText.Face face, float alpha) {
        if (alpha <= 0.01f) return;

        line(graphics, font, face.title(), face.pillTitleWidth(), frame.pillTextX,
                frame.pillTitleY, frame.pillTitleSlot, DesktopIslandText.PILL_TITLE_SCALE,
                UiTheme.alpha(TITLE_INK, alpha), text.sweep());
    }

    private void cardTitle(GuiGraphics graphics, Font font, DesktopIslandText.Face face, float alpha) {
        if (alpha <= 0.01f) return;

        line(graphics, font, face.title(), face.cardTitleWidth(), frame.textX, frame.titleY,
                frame.titleSlot, DesktopIslandText.CARD_TITLE_SCALE, UiTheme.alpha(TITLE_INK, alpha), text.sweep());
    }

    private void cardArtist(GuiGraphics graphics, Font font, DesktopIslandText.Face face, float alpha) {
        if (alpha <= 0.01f) return;

        line(graphics, font, face.artist(), face.artistWidth(), frame.textX,
                frame.titleY + (frame.artistY - frame.titleY) * morph.card(),
                frame.titleSlot, DesktopIslandText.ARTIST_SCALE, UiTheme.alpha(DIM_INK, alpha));
    }

    private void paintFlight(GuiGraphics graphics, Font font, float k, float alpha) {
        paintCover(graphics, lerp(frame.pillCoverX, frame.coverX, k), lerp(frame.pillCoverY, frame.coverY, k),
                lerp(frame.faceSize(), frame.coverSize(), k), alpha);
        flyTitle(graphics, font, text.shown(), k, alpha);
        if (!bars) return;

        IslandGlyph.visualizer(graphics, lerp(frame.pillWaveX, frame.waveX, k),
                lerp(frame.pillCoverY, frame.coverY, k), lerp(IslandGlyph.PILL_WIDTH, IslandGlyph.CARD_WIDTH, k),
                lerp(IslandGlyph.PILL_HEIGHT, IslandGlyph.CARD_HEIGHT, k), alpha);
    }

    private void flyTitle(GuiGraphics graphics, Font font, DesktopIslandText.Face face, float k, float alpha) {
        if (alpha <= 0.01f) return;

        float scale = lerp(DesktopIslandText.PILL_TITLE_SCALE / DesktopIslandText.CARD_TITLE_SCALE, 1.0f, k);
        float left = lerp(frame.pillTextX, frame.textX, k);
        float slot = lerp(frame.pillTitleSlot, frame.titleSlot, k);
        UiRestFrame.push(graphics, frame.textX, frame.titleY, scale, scale, left - frame.textX,
                (frame.pillTitleY - frame.titleY) * (1.0f - k));
        try {
            line(graphics, font, face.title(), face.cardTitleWidth(), frame.textX, frame.titleY,
                    slot / scale, DesktopIslandText.CARD_TITLE_SCALE, UiTheme.alpha(TITLE_INK, alpha), text.sweep());
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private static float lerp(float from, float to, float weight) {
        return from + (to - from) * weight;
    }

    private void paintControls(GuiGraphics graphics, Font font, float alpha) {
        if (!held.blind() && held.durationMs() > 0L) {
            bar.paint(graphics, font, frame, text, bar.shown(progress.value()), DIM_INK, alpha);
        }
        keys.paint(graphics, frame, TITLE_INK, alpha);
    }

    // WHY: пока строка меняется по буквам, она рисуется на месте без бегущей строки: смена длится
    // WHY: доли секунды, а ход бегущей строки начинается после паузы
    private void line(GuiGraphics graphics, Font font, UiMorphText text, float span, float x, float y,
                      float slot, float scale, int color) {
        line(graphics, font, text, span, x, y, slot, scale, color, UiSweep.NONE);
    }

    // WHY: у строки лирики удержанная ширина острова больше её самой: бегущую строку решает ширина
    // WHY: текущей строки, а едет она за волной подсветки
    private void line(GuiGraphics graphics, Font font, UiMorphText text, float span, float x, float y,
                      float slot, float scale, int color, UiSweep sweep) {
        if (slot <= 4.0f || (color >>> 24) < 3) return;

        float actual = sweep == UiSweep.NONE ? span : text.measure(graphics, font, scale);
        if (text.morphing() && (actual > slot || text.leaving() > 0.0f)) {
            morphing(graphics, font, text, x, y, slot, scale, color, sweep, actual);
            return;
        }
        if (actual <= slot || text.morphing()) {
            if (!text.morphing()) text.scrolled(0.0f);
            text.draw(graphics, font, x, y, scale, color, sweep);
            return;
        }
        float margin = UiMarquee.margin(slot, scale);
        float shift = sweep == UiSweep.NONE ? UiRender.marqueeShift(graphics, text.raw(), actual - slot)
                : text.follow(graphics, font, scale, slot, sweep);
        float start = x - shift;
        text.scrolled(shift);
        UiRender.clip(graphics, x - margin, frame.y, slot + margin * 2.0f, frame.height);
        UiRender.marqueeFrom(graphics, x, slot, start, actual, scale);
        try {
            text.draw(graphics, font, start, y, scale, color, sweep);
        } finally {
            UiRender.marqueeDone();
            graphics.disableScissor();
        }
    }

    // WHY: строка посреди смены гаснет у краёв слота так же, как бегущая: уехавшие за край буквы
    // WHY: прокрученной строки иначе проступали на время морфа. Кромка у уходящей и приходящей своя, по
    // WHY: их прокрутке, иначе гасли конец уходящей и начало приходящей
    private void morphing(GuiGraphics graphics, Font font, UiMorphText text, float x, float y, float slot,
                          float scale, int color, UiSweep sweep, float span) {
        float leaving = text.leaving();
        float was = text.leavingSpan(graphics, font, scale);
        float margin = UiMarquee.margin(slot, scale);
        UiRender.clip(graphics, x - margin, frame.y, slot + margin * 2.0f, frame.height);
        try {
            UiRender.marquee(graphics, x, slot, x - leaving, was, scale,
                    () -> text.drawLeaving(graphics, font, x, y, scale, color));
            UiRender.marquee(graphics, x, slot, x, span, scale,
                    () -> text.drawArriving(graphics, font, x, y, scale, color, sweep));
        } finally {
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            collapse();
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (!wanted) return press();
        return clickCard(mouseX, mouseY);
    }

    private boolean clickCard(double mouseX, double mouseY) {
        DesktopIslandKeys.Key key = keys.at(frame, mouseX, mouseY);
        if (key != null) {
            trigger(key);
            return true;
        }
        if (MediaControl.canSeek() && bar.press(frame, mouseX, mouseY)) return true;
        if (morph.opened() && !frame.onHeader(mouseX, mouseY)) return true;
        return press();
    }

    // WHY: по записи iOS остров вздувается под пальцем и меняет состояние, только когда его отпустили
    private boolean press() {
        pressing = true;
        morph.press(true);
        return true;
    }

    private void release() {
        pressing = false;
        morph.press(false);
        UiSound.press();
        if (wanted) {
            collapse();
            return;
        }
        wanted = true;
    }

    private void trigger(DesktopIslandKeys.Key key) {
        keys.pressed(key);
        UiSound.press();
        switch (key) {
            case PREVIOUS -> MediaControl.previous();
            case TOGGLE -> MediaControl.togglePlay();
            case NEXT -> MediaControl.next();
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!bar.dragging()) return false;

        bar.drag(frame, mouseX);
        return true;
    }

    public boolean mouseReleased() {
        if (pressing) {
            release();
            return true;
        }
        if (!bar.dragging()) return false;

        long target = (long) (bar.release() * held.durationMs());
        MediaControl.seek(target);
        MediaTrack moved = MediaWatch.current();
        if (moved.sameTrack(held) && moved.positionMs() == target) progress.land(moved);
        return true;
    }

    public boolean contains(double mouseX, double mouseY) {
        return shown.get() > 0.5f && frame.inside(mouseX, mouseY);
    }

    public boolean expanded() {
        return wanted;
    }

    public boolean seeking() {
        return bar.dragging();
    }

    public void collapse() {
        wanted = false;
        pressing = false;
        morph.press(false);
        bar.cancel();
        keys.forget();
    }

    public void dismiss() {
        collapse();
        morph.snap(false);
    }
}
