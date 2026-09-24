package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiSwap;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ToggleRow extends MenuRow {
    private static final float TRACK_WIDTH = 28.0f;
    private static final float TRACK_HEIGHT = 13.0f;
    private static final float KNOB_INSET = 1.5f;
    private static final float SLIDE_SPEED = 17.0f;
    private static final float STATE_GAP = 8.0f;
    private static final float LENS_SHEEN = 0.42f;
    private static final float MIN_ALPHA = 0.02f;

    private final BooleanSupplier state;
    private final Consumer<Boolean> apply;
    private final Smooth slide = new Smooth(SLIDE_SPEED);
    private final Pending pending = new Pending();
    private final UiSwap swap = new UiSwap();

    public ToggleRow(int x, int y, int width, int height, Component label,
                     BooleanSupplier state, Consumer<Boolean> apply) {
        super(x, y, width, height, label);
        this.state = state;
        this.apply = apply;
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        boolean on = chosen();
        float shift = slide.to(on ? 1.0f : 0.0f, UiFrame.delta());

        float trackX = getX() + width - PAD - TRACK_WIDTH;
        float trackY = getY() + (height - TRACK_HEIGHT) / 2.0f;
        renderTrack(graphics, trackX, trackY, shift, focus);
        renderStateLabel(graphics, trackX - STATE_GAP, on, focus);
    }

    private void renderTrack(GuiGraphics graphics, float trackX, float trackY, float shift, float focus) {
        boolean switching = UiGlassStyle.switching(true);
        try {
            paintTrack(graphics, trackX, trackY, shift, focus);
        } finally {
            UiGlassStyle.switching(switching);
        }
    }

    private void paintTrack(GuiGraphics graphics, float trackX, float trackY, float shift, float focus) {
        float radius = TRACK_HEIGHT / 2.0f;
        UiGlass.sunken(graphics, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, radius, 1.0f);
        UiGlass.progress(graphics, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, shift,
                valueTint(UiTheme.mix(UiAccent.dim(), UiAccent.color(), focus)), valueAlpha());

        float knob = TRACK_HEIGHT - KNOB_INSET * 2.0f;
        float travel = TRACK_WIDTH - knob - KNOB_INSET * 2.0f;
        paintKnob(graphics, trackX + KNOB_INSET + travel * shift, trackY + KNOB_INSET, knob, focus);
    }

    // WHY: при своей силе преломления шайба становится настоящей линзой и гнёт фон по кромке, поэтому
    // WHY: светлое тело поверх приглушается: на полной непрозрачности сквозь него ничего не видно
    private void paintKnob(GuiGraphics graphics, float x, float y, float size, float focus) {
        float radius = size / 2.0f;
        float lit = 0.55f + 0.45f * focus;
        if (UiGlassStyle.switchLens() <= 0.0f || !UiGlassStyle.liquid()) {
            UiGlass.inner(graphics, x, y, size, size, radius, valueAlpha(), lit);
            return;
        }

        UiGlass.panel(graphics, x, y, size, size, radius, valueAlpha(), lit);
        UiGlass.inner(graphics, x, y, size, size, radius, valueAlpha() * LENS_SHEEN, lit);
    }

    private void renderStateLabel(GuiGraphics graphics, float rightX, boolean on, float focus) {
        Component label = Component.translatable(on ? "battlecraft.menu.on" : "battlecraft.menu.off");
        float phase = swap.advance(label, UiFrame.delta());
        Component leaving = swap.outgoing();
        float y = UiRender.centerY(getY(), height, LABEL_SCALE);
        if (leaving != null) {
            paintState(graphics, leaving, rightX, y - phase * UiSwap.LIFT, stateColor(!on, focus), 1.0f - phase);
        }
        paintState(graphics, label, rightX, y + (1.0f - phase) * UiSwap.LIFT, stateColor(on, focus), phase);
    }

    private void paintState(GuiGraphics graphics, Component text, float rightX, float y, int color, float alpha) {
        if (alpha <= MIN_ALPHA) return;
        UiRender.textRight(graphics, font(), text, rightX, y, LABEL_SCALE, UiTheme.alpha(color, alpha), false);
    }

    private int stateColor(boolean on, float focus) {
        return on && !faded() ? UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus) : UiAccent.textFaint();
    }

    @Override
    public void adopt(MenuRow previous) {
        super.adopt(previous);
        if (!(previous instanceof ToggleRow older)) return;

        slide.take(older.slide);
        pending.take(older.pending);
        swap.take(older.swap);
    }

    private boolean chosen() {
        return pending.resolve(state.getAsBoolean() ? 1 : 0) == 1;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        flash();

        boolean wanted = !chosen();
        UiSound.toggle(wanted);
        pending.want(wanted ? 1 : 0);
        apply.accept(wanted);
    }
}
