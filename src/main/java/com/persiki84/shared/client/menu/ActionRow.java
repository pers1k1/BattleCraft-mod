package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiSwap;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class ActionRow extends MenuRow {
    private static final float VALUE_WIDTH = 132.0f;
    private static final int MIN_ALPHA = 4;

    private final Supplier<Component> value;
    private final Runnable action;
    private final UiSwap swap = new UiSwap();
    private boolean alerting;

    public ActionRow(int x, int y, int width, int height, Component label,
                     Supplier<Component> value, Runnable action) {
        super(x, y, width, height, label);
        this.value = value;
        this.action = action;
    }

    public ActionRow alerting() {
        this.alerting = true;
        return this;
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        Component shown = value.get();
        float phase = swap.advance(shown, UiFrame.delta());
        Component leaving = swap.outgoing();
        int color = valueColor(focus);
        if (leaving != null) paintValue(graphics, leaving, -phase * UiSwap.LIFT, UiTheme.alpha(color, 1.0f - phase));
        paintValue(graphics, shown, (1.0f - phase) * UiSwap.LIFT, UiTheme.alpha(color, phase));
    }

    private void paintValue(GuiGraphics graphics, Component text, float shift, int color) {
        if (text.getString().isEmpty() || (color >>> 24) < MIN_ALPHA) return;

        UiRender.textTrackedBox(graphics, font(), text, getX() + width - PAD - VALUE_WIDTH, getY() + shift, height,
                VALUE_WIDTH, LABEL_SCALE, 0.0f, color, false, 1.0f);
    }

    @Override
    public void adopt(MenuRow previous) {
        super.adopt(previous);
        if (previous instanceof ActionRow older) swap.take(older.swap);
    }

    private int valueColor(float focus) {
        if (blocked()) return UiAccent.textFaint();
        if (alerting) return UiTheme.mix(UiPalette.alert(), UiTheme.WHITE, focus * 0.15f);
        if (!this.active) return UiAccent.textFaint();
        return UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        UiSound.press();
        flash();
        action.run();
    }
}
