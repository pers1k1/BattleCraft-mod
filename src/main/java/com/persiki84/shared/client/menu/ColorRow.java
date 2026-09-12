package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWash;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.IntSupplier;

public class ColorRow extends MenuRow {
    private static final float SWATCH_WIDTH = 34.0f;
    private static final float SWATCH_INSET = 4.0f;
    private static final float HEX_SCALE = 0.85f;
    private static final float HEX_GAP = 7.0f;
    private static final float MARK_SIZE = 4.0f;
    private static final float SWATCH_SECONDS = 0.4f;

    private final IntSupplier value;
    private final Runnable open;
    private final BooleanState custom;
    private final UiWash wash = new UiWash(SWATCH_SECONDS);

    public interface BooleanState {
        boolean get();
    }

    public ColorRow(int x, int y, int width, int height, Component label,
                    IntSupplier value, BooleanState custom, Runnable open) {
        super(x, y, width, height, label);
        this.value = value;
        this.custom = custom;
        this.open = open;
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        int color = value.getAsInt();
        float swatchX = swatchLeft();
        float swatchY = getY() + SWATCH_INSET;
        float swatchHeight = height - SWATCH_INSET * 2.0f;
        float radius = Math.min(UiGlassStyle.radiusCell(), swatchHeight / 2.0f);

        UiGlass.sunken(graphics, swatchX, swatchY, SWATCH_WIDTH, swatchHeight, radius, 0.9f);
        UiRender.panel(graphics, swatchX + 1.0f, swatchY + 1.0f, SWATCH_WIDTH - 2.0f, swatchHeight - 2.0f,
                Math.max(0.0f, radius - 1.0f), UiTheme.withAlpha(shownColor(color), 1.0f));

        if (custom.get()) {
            UiRender.dot(graphics, swatchX + SWATCH_WIDTH - MARK_SIZE, swatchY + MARK_SIZE,
                    MARK_SIZE / 2.0f, UiAccent.text());
        }

        UiRender.textRight(graphics, font(), hex(color), swatchX - HEX_GAP,
                UiRender.centerY(getY(), height, HEX_SCALE), HEX_SCALE,
                this.active ? UiAccent.textDim() : UiAccent.textFaint(), false);
    }

    private int shownColor(int color) {
        wash.aim(color);
        wash.advance(UiFrame.delta());
        return wash.get();
    }

    private int hexValue;
    private String hexText;

    private String hex(int color) {
        if (color == hexValue && hexText != null) return hexText;

        hexValue = color;
        hexText = String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        return hexText;
    }

    private float swatchLeft() {
        return getX() + width - PAD - SWATCH_WIDTH;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        UiSound.press();
        flash();
        open.run();
    }
}
