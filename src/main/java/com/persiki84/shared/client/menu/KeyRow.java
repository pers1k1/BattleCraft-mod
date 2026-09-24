package com.persiki84.shared.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiSwap;
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
    private static final float LISTEN_SPEED = 16.0f;
    private static final float MIN_ALPHA = 0.02f;

    private final Supplier<Component> value;
    private final Consumer<InputConstants.Key> apply;
    private boolean listening;
    private final Smooth listen = new Smooth(0.0f, LISTEN_SPEED);
    private final UiSwap swap = new UiSwap();

    public KeyRow(int x, int y, int width, int height, Component label,
                  Supplier<Component> value, Consumer<InputConstants.Key> apply) {
        super(x, y, width, height, label);
        this.value = value;
        this.apply = apply;
    }

    public boolean listening() {
        return listening;
    }

    @Override
    public boolean capturing() {
        return listening();
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
        float delta = UiFrame.delta();
        float heard = listen.to(listening ? 1.0f : 0.0f, delta);
        UiGlass.panel(graphics, left, top, BOX_WIDTH, BOX_HEIGHT, UiMetrics.radius(BOX_HEIGHT),
                SURFACE_ALPHA, focus * BOX_LIFT + (BOX_LIFT - focus * BOX_LIFT) * heard);

        Component shown = listening
                ? Component.translatable("battlecraft.key.awaiting")
                : value.get();
        float phase = swap.advance(shown, delta);
        Component leaving = swap.outgoing();
        int color = tint(focus, heard);
        if (leaving != null) paintKey(graphics, leaving, left, top - phase * UiSwap.LIFT, color, 1.0f - phase);
        paintKey(graphics, shown, left, top + (1.0f - phase) * UiSwap.LIFT, color, phase);
    }

    private void paintKey(GuiGraphics graphics, Component text, float left, float top, int color, float alpha) {
        if (alpha <= MIN_ALPHA) return;
        UiRender.textTrackedBox(graphics, font(), text, left, top, BOX_HEIGHT, BOX_WIDTH,
                LABEL_SCALE, 0.0f, UiTheme.alpha(color, alpha), false, ALIGN_CENTER);
    }

    private int tint(float focus, float heard) {
        if (faded()) return UiAccent.textFaint();
        return UiTheme.mix(UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus), UiAccent.color(), heard);
    }

    @Override
    public void adopt(MenuRow previous) {
        super.adopt(previous);
        if (!(previous instanceof KeyRow older)) return;

        listen.take(older.listen);
        swap.take(older.swap);
        listening = older.listening;
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
