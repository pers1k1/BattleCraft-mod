package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.MenuHint;
import com.persiki84.shared.client.menu.RowAnchor;
import com.persiki84.shared.client.menu.ScrollLanes;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class UiButton extends AbstractButton implements GlidingRow, SelfPainted {
    private static final float HOVER_RESPONSE = 0.30f;
    private static final float HOVER_DAMPING = 0.92f;
    private static final float PRESS_RESPONSE = 0.26f;
    private static final float PRESS_DAMPING = 0.58f;

    private final Consumer<UiButton> action;
    private final Spring hover = new Spring(HOVER_RESPONSE, HOVER_DAMPING, 0.0f);
    private final Spring press = new Spring(PRESS_RESPONSE, PRESS_DAMPING, 0.0f);
    private final RowAnchor anchor = new RowAnchor();
    private Component explanation;
    private int swatch;
    private boolean lit;
    private boolean muted;
    private boolean announced;
    private boolean held;

    public UiButton swatch(int argb) {
        swatch = argb;
        return this;
    }

    // WHY: выбранная строка гасит себя как выключенная, и в магазине именно её подпись читается хуже всех
    public UiButton lit() {
        lit = true;
        return this;
    }

    public UiButton hint(Component text) {
        explanation = text;
        return this;
    }

    public UiButton hint(String key) {
        return hint(MenuHint.of(key));
    }

    // WHY: пробник звуковой схемы играет образец чужой схемы, и свой щелчок кнопки лёг бы поверх него
    public UiButton muted() {
        muted = true;
        return this;
    }

    @Override
    public void anchor(int y, Lane lane) {
        anchor.set(y, lane);
        setY(y);
    }

    @Override
    public void glide(ScrollLanes lanes) {
        if (!anchor.placed()) return;

        anchor.follow(lanes);
        setY(anchor.shifted());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (anchor.gone(getY(), this.height)) return;

        boolean clipped = anchor.clip(graphics, getX(), this.width, getY(), this.height);
        try {
            super.render(graphics, mouseX, anchor.pointer(mouseY), partialTick);
        } finally {
            if (clipped) graphics.disableScissor();
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return anchor.covers(mouseY) && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return anchor.covers(mouseY) && super.clicked(mouseX, mouseY);
    }

    public UiButton(int x, int y, int width, int height, Component message, Consumer<UiButton> action) {
        super(x, y, width, height, message);
        this.action = action;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        held = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        held = false;
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public void onPress() {
        if (!muted) UiSound.press();
        action.accept(this);
    }

    @Override
    public void playDownSound(SoundManager sounds) {
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = this.active && UiInput.pointed(this);
        if (focused != announced) {
            announced = focused;
            if (focused && !muted) UiSound.hover();
        }

        if (held && !UiInput.mouseDown()) held = false;
        if (this.visible && this.isHovered()) {
            MenuHint.offer(this, explanation, getY(), this.height, mouseX);
        }

        float delta = UiFrame.delta();
        UiSkin.button(graphics, this.getX(), this.getY(), this.width, this.height, this.getMessage(),
                this.active, hover.to(focused ? 1.0f : 0.0f, delta), press.to(held ? 1.0f : 0.0f, delta),
                UiSkin.fit(graphics), swatch, lit);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
