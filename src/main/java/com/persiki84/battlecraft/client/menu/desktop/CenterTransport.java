package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.media.MediaControl;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: кнопки мини-плеера отвечают как в iOS: круглая подложка проявляется под курсором, щелчок
// WHY: коротко утапливает значок, пауза и воспроизведение перетекают друг в друга. Недоступная
// WHY: кнопка гаснет плавно и не нажимается: плеер сам решает, можно ли листать и ставить паузу
final class CenterTransport {
    enum Key { PREVIOUS, TOGGLE, NEXT }

    private static final Key[] KEYS = Key.values();
    private static final float SPACING = 24.0f;
    private static final float SIDE_ICON = 9.0f;
    private static final float MAIN_ICON = 12.0f;
    private static final float HIT_RADIUS = 10.0f;
    private static final float HALO_RADIUS = 9.0f;
    private static final float HALO_ALPHA = 0.12f;
    private static final float PRESS_DIP = 0.14f;
    private static final float FACE_FROM = 0.6f;
    private static final float DISABLED_ALPHA = 0.3f;

    private final Smooth[] hover = new Smooth[KEYS.length];
    private final Smooth[] press = new Smooth[KEYS.length];
    private final Smooth[] ready = new Smooth[KEYS.length];
    private final boolean[] enabled = new boolean[KEYS.length];
    private final Smooth face = new Smooth(0.0f, 14.0f);
    private Key hovered;
    private float centerX;
    private float rowY;

    CenterTransport() {
        for (int index = 0; index < KEYS.length; index++) {
            hover[index] = new Smooth(0.0f, 14.0f);
            press[index] = new Smooth(0.0f, 9.0f);
            ready[index] = new Smooth(0.0f, 10.0f);
        }
    }

    void place(float x, float y) {
        centerX = x;
        rowY = y;
    }

    void advance(double mouseX, double mouseY, float delta) {
        enabled[Key.PREVIOUS.ordinal()] = MediaControl.canPrevious();
        enabled[Key.TOGGLE.ordinal()] = MediaControl.canToggle();
        enabled[Key.NEXT.ordinal()] = MediaControl.canNext();
        Key over = at(mouseX, mouseY);
        if (over != null && over != hovered) UiSound.hover();
        hovered = over;
        for (Key key : KEYS) {
            hover[key.ordinal()].to(key == over ? 1.0f : 0.0f, delta);
            press[key.ordinal()].to(0.0f, delta);
            ready[key.ordinal()].to(enabled[key.ordinal()] ? 1.0f : 0.0f, delta);
        }
        face.to(MediaControl.playing() ? 1.0f : 0.0f, delta);
    }

    private Key at(double mouseX, double mouseY) {
        for (Key key : KEYS) {
            double dx = mouseX - centerOf(key);
            double dy = mouseY - rowY;
            if (enabled[key.ordinal()] && dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS) return key;
        }
        return null;
    }

    boolean click(double mouseX, double mouseY) {
        Key key = at(mouseX, mouseY);
        if (key == null) return false;
        press[key.ordinal()].snap(1.0f);
        UiSound.press();
        switch (key) {
            case PREVIOUS -> MediaControl.previous();
            case TOGGLE -> MediaControl.togglePlay();
            case NEXT -> MediaControl.next();
        }
        return true;
    }

    void paint(GuiGraphics graphics, int ink, float alpha) {
        if (alpha <= 0.02f) return;
        for (Key key : KEYS) {
            float x = centerOf(key);
            float lit = Math.max(hover[key.ordinal()].get(), press[key.ordinal()].get());
            if (lit > 0.01f) {
                UiRender.dot(graphics, x, rowY, HALO_RADIUS, UiTheme.withAlpha(UiTheme.WHITE, HALO_ALPHA * lit * alpha));
            }
            float dip = 1.0f - PRESS_DIP * press[key.ordinal()].get();
            float lively = DISABLED_ALPHA + (1.0f - DISABLED_ALPHA) * ready[key.ordinal()].get();
            glyph(graphics, key, x, dip, UiTheme.alpha(ink, alpha * lively));
        }
    }

    private void glyph(GuiGraphics graphics, Key key, float x, float dip, int tone) {
        switch (key) {
            case PREVIOUS -> UiIcon.draw(graphics, UiIcon.Kind.BACKWARD, x, rowY, SIDE_ICON * dip, tone);
            case NEXT -> UiIcon.draw(graphics, UiIcon.Kind.FORWARD, x, rowY, SIDE_ICON * dip, tone);
            case TOGGLE -> toggle(graphics, x, dip, tone);
        }
    }

    private void toggle(GuiGraphics graphics, float x, float dip, int tone) {
        float pause = face.get();
        float play = 1.0f - pause;
        if (pause > 0.01f) {
            UiIcon.draw(graphics, UiIcon.Kind.PAUSE, x, rowY, MAIN_ICON * dip * (FACE_FROM + (1.0f - FACE_FROM) * pause),
                    UiTheme.alpha(tone, pause));
        }
        if (play > 0.01f) {
            UiIcon.draw(graphics, UiIcon.Kind.PLAY, x, rowY, MAIN_ICON * dip * (FACE_FROM + (1.0f - FACE_FROM) * play),
                    UiTheme.alpha(tone, play));
        }
    }

    private float centerOf(Key key) {
        return centerX + (key.ordinal() - 1) * SPACING;
    }

    void forget() {
        hovered = null;
    }
}
