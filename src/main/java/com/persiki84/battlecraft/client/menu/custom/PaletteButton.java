package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.shared.client.ui.SelfPainted;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiInput;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSkin;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class PaletteButton extends AbstractButton implements SelfPainted {
    public static final int SIZE = 26;

    private static final float HOVER_RESPONSE = 0.30f;
    private static final float HOVER_DAMPING = 0.92f;
    private static final float PRESS_RESPONSE = 0.26f;
    private static final float PRESS_DAMPING = 0.58f;
    private static final float ICON_SHARE = 0.74f;
    private static final float SPIN_MS = 5200.0f;
    private static final float SPIN_TILT = 0.16f;

    private final Consumer<PaletteButton> action;
    private final Spring hover = new Spring(HOVER_RESPONSE, HOVER_DAMPING, 0.0f);
    private final Spring press = new Spring(PRESS_RESPONSE, PRESS_DAMPING, 0.0f);
    private boolean announced;
    private boolean held;

    public PaletteButton(int x, int y, Consumer<PaletteButton> action) {
        super(x, y, SIZE, SIZE, Component.translatable("battlecraft.custom.open"));
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
        UiSound.press();
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
            if (focused) UiSound.hover();
        }
        if (held && !UiInput.mouseDown()) held = false;

        float delta = UiFrame.delta();
        float focus = hover.to(focused ? 1.0f : 0.0f, delta);
        float pushed = press.to(held ? 1.0f : 0.0f, delta);

        UiSkin.icon(graphics, getX(), getY(), width, height, this.active, focus);
        drawIcon(graphics, focus, pushed);
    }

    private void drawIcon(GuiGraphics graphics, float focus, float pushed) {
        float centerX = getX() + width / 2.0f;
        float centerY = getY() + height / 2.0f;
        float span = Math.min(width, height) * ICON_SHARE * (1.0f + focus * 0.08f - pushed * 0.06f);
        float tilt = UiAnim.pulse(SPIN_MS, -SPIN_TILT, SPIN_TILT) * (0.4f + 0.6f * focus);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0f);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(tilt));
        graphics.pose().translate(-centerX, -centerY, 0.0f);
        try {
            UiRender.iconPalette(graphics, centerX, centerY, span, 0.85f + 0.15f * focus);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
