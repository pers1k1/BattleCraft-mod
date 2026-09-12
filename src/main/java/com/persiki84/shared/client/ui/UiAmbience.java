package com.persiki84.shared.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class UiAmbience {
    public enum Mood {
        CLEAR(1.0f, 0.0f, 0.0f, 0.5f, 0.5f),
        FOCUS(1.075f, 0.62f, 0.85f, 1.9f, 1.9f),
        WINDOW(1.035f, 0.44f, 0.42f, 0.26f, 0.6f);

        private final float zoom;
        private final float veil;
        private final float haze;
        private final float arrive;
        private final float leave;

        Mood(float zoom, float veil, float haze, float arrive, float leave) {
            this.zoom = zoom;
            this.veil = veil;
            this.haze = haze;
            this.arrive = arrive;
            this.leave = leave;
        }
    }

    private static Mood mood = Mood.CLEAR;
    private static Mood leaving = Mood.WINDOW;
    private static float phase = 1.0f;
    private static float fromZoom = Mood.CLEAR.zoom;
    private static float fromVeil;
    private static float fromHaze;
    private static long stamp = -1L;

    private UiAmbience() {}

    public static void advance() {
        long frame = UiFrame.frame();
        if (frame == stamp) return;

        stamp = frame;
        aim();
        phase = Math.min(1.0f, phase + UiFrame.delta() / seconds());
    }

    public static float zoom() {
        return fromZoom + (mood.zoom - fromZoom) * eased();
    }

    public static float veil() {
        return fromVeil + (mood.veil - fromVeil) * eased();
    }

    public static float haze() {
        return fromHaze + (mood.haze - fromHaze) * eased();
    }

    // WHY: экспонента меняет картинку рывком в первые полсекунды, и снятие эффектов читается вспышкой:
    // WHY: фон светлеет раньше, чем окно успевает уйти. Ход берётся временем и сглажен с обоих концов
    private static float eased() {
        return UiAnim.smoothstep(0.0f, 1.0f, phase);
    }

    // WHY: уход идёт временем того настроения, которое снимается: мастер растворяется так же
    // WHY: неспешно, как собирался, а окно поверх меню уходит чуть медленнее, чем приходило
    private static float seconds() {
        return mood == Mood.CLEAR ? leaving.leave : mood.arrive;
    }

    // WHY: под мозанговским оверлеем среда встаёт сразу: за секунду до появления экрана игрок
    // WHY: видит, как поле перестраивается, и это читается как разгон анимации на старте
    private static void aim() {
        Mood wanted = wanted();
        if (wanted == mood) return;

        fromZoom = zoom();
        fromVeil = veil();
        fromHaze = haze();
        if (mood != Mood.CLEAR) leaving = mood;
        mood = wanted;
        phase = UiBoot.loading() ? 1.0f : 0.0f;
    }

    private static Mood wanted() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return Mood.CLEAR;
        if (screen instanceof Ambient ambient) return ambient.ambience();
        return Mood.WINDOW;
    }
}
