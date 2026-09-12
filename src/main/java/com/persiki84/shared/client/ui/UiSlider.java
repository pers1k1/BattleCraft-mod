package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class UiSlider extends AbstractSliderButton implements SelfPainted {
    private final Smooth hover = new Smooth(0.0f, 16.0f);
    private final Smooth position = new Smooth(24.0f);
    private boolean announced;
    private double lastNotch = -1.0;

    public UiSlider(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = UiInput.pointed(this);
        if (focused != announced) {
            announced = focused;
            if (focused) UiSound.hover();
        }

        double notch = Math.round(this.value * 24.0);
        if (notch != lastNotch) {
            if (lastNotch >= 0.0) UiSound.slide((float) this.value);
            lastNotch = notch;
        }

        float delta = UiFrame.delta();
        UiSkin.slider(graphics, this.getX(), this.getY(), this.width, this.height, this.getMessage(),
                this.active, hover.to(focused ? 1.0f : 0.0f, delta), position.to((float) this.value, delta));
    }
}
