package com.persiki84.shared.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class KeyRow extends MenuRow {
    private static final float BOX_WIDTH = 96.0f;
    private static final float BOX_HEIGHT = 16.0f;
    private static final float BOX_LIFT = 0.35f;

    private final Supplier<Component> value;
    private final Consumer<InputConstants.Key> apply;
    private boolean listening;

    public KeyRow(int x, int y, int width, int height, Component label,
                  Supplier<Component> value, Consumer<InputConstants.Key> apply) {
        super(x, y, width, height, label);
        this.value = value;
        this.apply = apply;
    }

    public boolean listening() {
        return listening;
    }

    // WHY: строка ловит клавишу раньше экрана, поэтому чужой обработчик обязан спросить её первой,
    // WHY: иначе Escape уводит мастер на шаг назад вместо отмены назначения
    public boolean take(int key, int scan) {
        if (!listening) return false;

        listening = false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            UiSound.deny();
            return true;
        }
        bind(InputConstants.getKey(key, scan));
        return true;
    }

    public boolean takeButton(int button) {
        if (!listening) return false;

        listening = false;
        bind(InputConstants.Type.MOUSE.getOrCreate(button));
        return true;
    }

    private void bind(InputConstants.Key key) {
        UiSound.confirm();
        apply.accept(key);
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        float left = getX() + width - PAD - BOX_WIDTH;
        float top = getY() + (height - BOX_HEIGHT) / 2.0f;
        UiGlass.panel(graphics, left, top, BOX_WIDTH, BOX_HEIGHT, UiMetrics.radius(BOX_HEIGHT),
                SURFACE_ALPHA, listening ? BOX_LIFT : focus * BOX_LIFT);

        Component shown = listening
                ? Component.translatable("battlecraft.key.awaiting")
                : value.get();
        UiRender.textTrackedBox(graphics, font(), shown, left, top, BOX_HEIGHT, BOX_WIDTH,
                LABEL_SCALE, 0.0f, tint(focus), false, ALIGN_CENTER);
    }

    private int tint(float focus) {
        if (faded()) return UiAccent.textFaint();
        if (listening) return UiAccent.color();
        return UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        if (!this.active) return;

        listening = !listening;
        UiSound.press();
        flash();
    }
}
