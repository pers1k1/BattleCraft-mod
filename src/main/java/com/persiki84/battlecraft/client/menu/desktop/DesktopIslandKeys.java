package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: кнопки плеера отвечают как в iOS: круглая подложка проявляется под курсором, щелчок
// WHY: коротко утапливает значок, а пауза и воспроизведение перетекают друг в друга, а не мигают.
// WHY: Доступность тоже переезжает: «назад» на первом треке плейлиста гаснет плавно, а не кадром
final class DesktopIslandKeys {
    enum Key { PREVIOUS, TOGGLE, NEXT }

    private static final Key[] KEYS = Key.values();
    private static final float SPACING = 34.0f;
    private static final float SIDE_ICON = 11.0f;
    private static final float MAIN_ICON = 14.0f;
    private static final float HIT_RADIUS = 12.0f;
    private static final float HALO_RADIUS = 11.0f;
    private static final float HALO_ALPHA = 0.12f;
    private static final float PRESS_DIP = 0.14f;
    private static final float FACE_FROM = 0.6f;
    private static final float DISABLED_ALPHA = 0.3f;
    private static final float HOVER_SPEED = 14.0f;
    private static final float PRESS_SPEED = 9.0f;
    private static final float FACE_SPEED = 14.0f;
    private static final float READY_SPEED = 10.0f;
    private static final float READY_REVEAL = 0.9f;

    private final Smooth[] hover = new Smooth[KEYS.length];
    private final Smooth[] press = new Smooth[KEYS.length];
    private final Smooth[] ready = new Smooth[KEYS.length];
    private final boolean[] enabled = new boolean[KEYS.length];
    private final Smooth face = new Smooth(0.0f, FACE_SPEED);
    private Key hovered;

    DesktopIslandKeys() {
        for (int index = 0; index < KEYS.length; index++) {
            hover[index] = new Smooth(0.0f, HOVER_SPEED);
            press[index] = new Smooth(0.0f, PRESS_SPEED);
            ready[index] = new Smooth(READY_SPEED);
        }
    }

    void enable(boolean previous, boolean toggle, boolean next) {
        enabled[Key.PREVIOUS.ordinal()] = previous;
        enabled[Key.TOGGLE.ordinal()] = toggle;
        enabled[Key.NEXT.ordinal()] = next;
    }

    void advance(DesktopIslandFrame frame, double mouseX, double mouseY, boolean playing, float delta) {
        Key over = at(frame, mouseX, mouseY);
        if (over != null && over != hovered) UiSound.hover();
        hovered = over;
        for (Key key : KEYS) {
            hover[key.ordinal()].to(key == over ? 1.0f : 0.0f, delta);
            press[key.ordinal()].to(0.0f, delta);
            ready[key.ordinal()].to(enabled[key.ordinal()] ? 1.0f : 0.0f, delta);
        }
        face.to(playing ? 1.0f : 0.0f, delta);
    }

    Key at(DesktopIslandFrame frame, double mouseX, double mouseY) {
        if (frame.reveal < READY_REVEAL) return null;

        for (Key key : KEYS) {
            double dx = mouseX - centerOf(frame, key);
            double dy = mouseY - frame.rowY;
            if (enabled[key.ordinal()] && dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS) return key;
        }
        return null;
    }

    void pressed(Key key) {
        press[key.ordinal()].snap(1.0f);
    }

    void paint(GuiGraphics graphics, DesktopIslandFrame frame, int ink, float alpha) {
        if (alpha <= 0.02f) return;

        for (Key key : KEYS) {
            float centerX = centerOf(frame, key);
            float lit = Math.max(hover[key.ordinal()].get(), press[key.ordinal()].get());
            if (lit > 0.01f) {
                UiRender.dot(graphics, centerX, frame.rowY, HALO_RADIUS,
                        UiTheme.withAlpha(UiTheme.WHITE, HALO_ALPHA * lit * alpha));
            }
            float dip = 1.0f - PRESS_DIP * press[key.ordinal()].get();
            float lively = DISABLED_ALPHA + (1.0f - DISABLED_ALPHA) * ready[key.ordinal()].get();
            int tone = UiTheme.alpha(ink, alpha * lively);
            glyph(graphics, key, centerX, frame.rowY, dip, tone);
        }
    }

    private void glyph(GuiGraphics graphics, Key key, float centerX, float centerY, float dip, int tone) {
        switch (key) {
            case PREVIOUS -> UiIcon.draw(graphics, UiIcon.Kind.BACKWARD, centerX, centerY, SIDE_ICON * dip, tone);
            case NEXT -> UiIcon.draw(graphics, UiIcon.Kind.FORWARD, centerX, centerY, SIDE_ICON * dip, tone);
            case TOGGLE -> toggle(graphics, centerX, centerY, dip, tone);
        }
    }

    private void toggle(GuiGraphics graphics, float centerX, float centerY, float dip, int tone) {
        float pause = face.get();
        float play = 1.0f - pause;
        if (pause > 0.01f) {
            UiIcon.draw(graphics, UiIcon.Kind.PAUSE, centerX, centerY,
                    MAIN_ICON * dip * (FACE_FROM + (1.0f - FACE_FROM) * pause), UiTheme.alpha(tone, pause));
        }
        if (play > 0.01f) {
            UiIcon.draw(graphics, UiIcon.Kind.PLAY, centerX, centerY,
                    MAIN_ICON * dip * (FACE_FROM + (1.0f - FACE_FROM) * play), UiTheme.alpha(tone, play));
        }
    }

    private static float centerOf(DesktopIslandFrame frame, Key key) {
        return frame.centerX + (key.ordinal() - 1) * SPACING;
    }

    void forget() {
        hovered = null;
    }
}
