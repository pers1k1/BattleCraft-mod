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
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVeil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
    private static final float PILL_DRIFT = 1.5f;
    private static final float CARD_DRIFT = -3.0f;
    private static final float STILL = 0.001f;
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

    public void render(GuiGraphics graphics, Font font, float screenWidth, float room, int mouseX, int mouseY,
                       float alpha) {
        advance(graphics, font, screenWidth, room, mouseX, mouseY);
        float visible = alpha * UiAnim.clamp01(shown.get());
        if (visible <= 0.01f) return;

        float scale = HIDDEN_SCALE + (1.0f - HIDDEN_SCALE) * UiAnim.clamp01(shown.get());
        boolean outer = UiRender.floating(true);
        UiRender.floating(outer || moving);
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
        if (text.describe(held, delta, shown.get() > VISIBLE_SHARE)) morph.swap();
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
        frame.lay(screenWidth / 2.0f, top, morph.shape(), morph.squeeze(), pillWidth.get(), cardWidth.get(), bars);
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

    private void paint(GuiGraphics graphics, Font font, float alpha) {
        paintBody(graphics, alpha);
        boolean veiled = !morph.resting() && UiVeil.begin(graphics);
        if (!veiled) UiRender.clip(graphics, frame.x, frame.y, frame.width, frame.height);
        try {
            paintPill(graphics, font, alpha * morph.pill());
            paintCard(graphics, font, alpha * morph.card());
        } finally {
            if (veiled) {
                UiVeil.end(graphics, frame.x, frame.y, frame.width, frame.height, frame.radius,
                        morph.blur() * IslandMorph.BLUR, 1.0f);
            } else {
                graphics.disableScissor();
            }
        }
        if (morph.resting()) UiVeil.tidy();
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

        IslandMorph.pose(graphics, frame.centerX, frame.pillCoverY, morph.pill(), PILL_DRIFT);
        try {
            paintCover(graphics, frame.pillCoverX, frame.pillCoverY, frame.faceSize(), alpha);
            pillTitle(graphics, font, text.leaving(), alpha * text.fading());
            pillTitle(graphics, font, text.shown(), alpha * text.entering());
            if (bars) IslandGlyph.pillBars(graphics, frame.pillWaveX, frame.pillCoverY, alpha);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void paintCard(GuiGraphics graphics, Font font, float alpha) {
        if (alpha <= 0.01f) return;

        IslandMorph.pose(graphics, frame.centerX, frame.y + DesktopIslandFrame.CARD_HEIGHT / 2.0f, morph.card(),
                CARD_DRIFT);
        try {
            paintCover(graphics, frame.coverX, frame.coverY, frame.coverSize(), alpha);
            cardFace(graphics, font, text.leaving(), alpha * text.fading());
            cardFace(graphics, font, text.shown(), alpha * text.entering());
            if (bars) IslandGlyph.cardBars(graphics, frame.waveX, frame.coverY, alpha);
            paintControls(graphics, font, alpha);
        } finally {
            UiRestFrame.pop(graphics);
        }
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

        line(graphics, font, face.title(), face.titleRaw(), face.pillTitleWidth(), frame.pillTextX,
                frame.pillTitleY, frame.pillTitleSlot, DesktopIslandText.PILL_TITLE_SCALE,
                UiTheme.alpha(TITLE_INK, alpha));
    }

    private void cardFace(GuiGraphics graphics, Font font, DesktopIslandText.Face face, float alpha) {
        if (alpha <= 0.01f) return;

        line(graphics, font, face.title(), face.titleRaw(), face.cardTitleWidth(), frame.textX, frame.titleY,
                frame.titleSlot, DesktopIslandText.CARD_TITLE_SCALE, UiTheme.alpha(TITLE_INK, alpha));
        line(graphics, font, face.artist(), face.artistRaw(), face.artistWidth(), frame.textX, frame.artistY,
                frame.titleSlot, DesktopIslandText.ARTIST_SCALE, UiTheme.alpha(DIM_INK, alpha));
    }

    private void paintControls(GuiGraphics graphics, Font font, float alpha) {
        if (!held.blind() && held.durationMs() > 0L) {
            bar.paint(graphics, font, frame, text, bar.shown(progress.value()), DIM_INK, alpha);
        }
        keys.paint(graphics, frame, TITLE_INK, alpha);
    }

    private void line(GuiGraphics graphics, Font font, Component value, String raw, float span, float x, float y,
                      float slot, float scale, int color) {
        if (slot <= 4.0f || (color >>> 24) < 3) return;

        if (span <= slot) {
            UiRender.labelScaled(graphics, font, value, x, y, scale, color);
            return;
        }
        float margin = UiMarquee.margin(slot, scale);
        float start = x - UiRender.marqueeShift(graphics, raw, span - slot);
        UiRender.clip(graphics, x - margin, frame.y, slot + margin * 2.0f, frame.height);
        UiRender.marqueeFrom(graphics, x, slot, start, span, scale);
        try {
            UiRender.labelScaled(graphics, font, value, start, y, scale, color);
        } finally {
            UiRender.marqueeDone();
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            collapse();
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (!wanted) {
            wanted = true;
            UiSound.press();
            return true;
        }
        return clickCard(mouseX, mouseY);
    }

    private boolean clickCard(double mouseX, double mouseY) {
        DesktopIslandKeys.Key key = keys.at(frame, mouseX, mouseY);
        if (key != null) {
            trigger(key);
            return true;
        }
        if (MediaControl.canSeek() && bar.press(frame, mouseX, mouseY)) return true;
        if (!frame.onHeader(mouseX, mouseY)) return true;

        UiSound.press();
        collapse();
        return true;
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
        bar.cancel();
        keys.forget();
    }

    public void dismiss() {
        collapse();
        morph.snap(false);
    }
}
