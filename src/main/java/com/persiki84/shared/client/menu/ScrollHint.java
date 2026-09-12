package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

public final class ScrollHint {
    public static final int BAND_TOP = 9;
    public static final int BAND_BOTTOM = 13;

    private static final float SPAN_DOWN = 5.2f;
    private static final float SPAN_UP = 3.8f;
    private static final float DEPTH = 0.42f;
    private static final float STROKE = 1.15f;
    private static final float APPEAR_SPEED = 11.0f;
    private static final float BREATH_PERIOD_MS = 2600.0f;
    private static final float BREATH_LOW = 0.58f;
    private static final float BREATH_HIGH = 1.0f;
    private static final float BOB_TRAVEL = 1.2f;
    private static final float BODY_ALPHA = 0.82f;
    private static final float HALO_SPREAD = 0.62f;
    private static final float HALO_GROWTH = 0.08f;
    private static final float HALO_ALPHA = 0.09f;
    private static final float HALO_LIFT = 0.45f;
    private static final float DIAGONAL = 0.7071f;
    private static final float GONE = 0.02f;
    private static final int CORNERS = 6;

    private static final float[] HALO_STEPS = {
            1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, -1.0f,
            DIAGONAL, DIAGONAL, -DIAGONAL, DIAGONAL, DIAGONAL, -DIAGONAL, -DIAGONAL, -DIAGONAL
    };

    private final Smooth appear = new Smooth(0.0f, APPEAR_SPEED);
    private final float[] xs = new float[CORNERS];
    private final float[] ys = new float[CORNERS];
    private float tipX;
    private float tipY;
    private float span;
    private float rise;

    public void render(GuiGraphics graphics, float centerX, float centerY, boolean down, boolean wanted) {
        float shown = UiAnim.easeOut(appear.to(wanted ? 1.0f : 0.0f, UiFrame.delta()));
        if (shown <= GONE) return;

        float breath = UiAnim.pulse(BREATH_PERIOD_MS, BREATH_LOW, BREATH_HIGH);
        float wave = (breath - BREATH_LOW) / (BREATH_HIGH - BREATH_LOW) - 0.5f;
        place(centerX, centerY + (down ? wave : -wave) * BOB_TRAVEL, down);

        int tone = UiAccent.text();
        float visible = shown * breath;

        halo(graphics, UiTheme.alpha(UiTheme.lighten(tone, HALO_LIFT), HALO_ALPHA * visible));
        chevron(graphics, 0.0f, 0.0f, STROKE, UiTheme.alpha(tone, BODY_ALPHA * visible));
    }

    private void place(float centerX, float centerY, boolean down) {
        span = down ? SPAN_DOWN : SPAN_UP;
        float depth = span * DEPTH * (down ? 1.0f : -1.0f);
        tipX = centerX;
        tipY = centerY + depth;
        rise = -depth * 2.0f;
    }

    private void halo(GuiGraphics graphics, int color) {
        for (int step = 0; step < HALO_STEPS.length; step += 2) {
            chevron(graphics, HALO_STEPS[step] * HALO_SPREAD, HALO_STEPS[step + 1] * HALO_SPREAD,
                    STROKE + HALO_GROWTH, color);
        }
    }

    private void chevron(GuiGraphics graphics, float shiftX, float shiftY, float width, int color) {
        shape(shiftX, shiftY, width * 0.5f);
        UiRender.polygon(graphics, xs, ys, CORNERS, tipX + shiftX, tipY + shiftY, color);
    }

    private void shape(float shiftX, float shiftY, float half) {
        float arm = (float) Math.sqrt(span * span + rise * rise);
        float normalX = -rise / arm * half;
        float normalY = span / arm * half;
        float notch = half * arm / span;

        corner(0, tipX - span - normalX + shiftX, tipY + rise + normalY + shiftY);
        corner(1, tipX + shiftX, tipY + notch + shiftY);
        corner(2, tipX + span + normalX + shiftX, tipY + rise + normalY + shiftY);
        corner(3, tipX + span - normalX + shiftX, tipY + rise - normalY + shiftY);
        corner(4, tipX + shiftX, tipY - notch + shiftY);
        corner(5, tipX - span + normalX + shiftX, tipY + rise - normalY + shiftY);
    }

    private void corner(int index, float x, float y) {
        xs[index] = x;
        ys[index] = y;
    }
}
