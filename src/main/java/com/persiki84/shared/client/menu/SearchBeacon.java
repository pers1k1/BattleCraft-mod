package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: строка, к которой привёл поиск, один раз разгорается кольцом акцента и гаснет, как ряд
// WHY: в поиске настроек macOS: часы стоят, пока экран не проявился, иначе вспышку съедает вход
public final class SearchBeacon {
    private static final float RISE_SECONDS = 0.18f;
    private static final float HOLD_SECONDS = 1.1f;
    private static final float FALL_SECONDS = 0.6f;
    private static final float HALO = 1.5f;
    private static final float RIM = 1.0f;
    private static final float FILL_ALPHA = 0.14f;
    private static final float RIM_ALPHA = 0.9f;

    private boolean armed;
    private float clock;

    public void arm() {
        armed = true;
        clock = 0.0f;
    }

    public void stop() {
        armed = false;
    }

    public boolean armed() {
        return armed;
    }

    public float advance(boolean ready, float delta) {
        if (!armed) return 0.0f;
        if (ready) clock += delta;
        if (clock >= RISE_SECONDS + HOLD_SECONDS + FALL_SECONDS) {
            armed = false;
            return 0.0f;
        }
        return strength();
    }

    private float strength() {
        if (clock < RISE_SECONDS) return UiAnim.easeOut(clock / RISE_SECONDS);
        float falling = clock - RISE_SECONDS - HOLD_SECONDS;
        if (falling <= 0.0f) return 1.0f;
        return 1.0f - UiAnim.smoothstep(0.0f, FALL_SECONDS, falling);
    }

    public static void paint(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                             float strength) {
        if (strength <= 0.0f || width <= 0.0f || height <= 0.0f) return;

        float left = x - HALO;
        float top = y - HALO;
        float wide = width + HALO * 2.0f;
        float tall = height + HALO * 2.0f;
        float rounded = radius + HALO;
        int accent = UiAccent.color();
        UiRender.panel(graphics, left, top, wide, tall, rounded, UiTheme.alpha(accent, FILL_ALPHA * strength));
        UiRender.rim(graphics, left, top, wide, tall, rounded, RIM, UiTheme.alpha(accent, RIM_ALPHA * strength));
    }
}
