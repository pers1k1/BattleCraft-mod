package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class ActionRow extends MenuRow {
    private static final float VALUE_WIDTH = 132.0f;

    private final Supplier<Component> value;
    private final Runnable action;
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
        if (shown.getString().isEmpty()) return;

        UiRender.textTrackedBox(graphics, font(), shown, getX() + width - PAD - VALUE_WIDTH, getY(), height,
                VALUE_WIDTH, LABEL_SCALE, 0.0f, valueColor(focus), false, 1.0f);
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
